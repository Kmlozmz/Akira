import '../recovered/render_bindings.dart';
import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:inotes_design_reference/inotes_design_reference.dart'
    as reference;
import '../recovered/components.dart';
import '../recovered/motion/recovered_pointer_exclusions.dart';
import 'settings.dart';
import 'laboratory.dart';
import 'interactive_controls.dart';

const materialNames = <String, String>{
  'glass_lean_lens': 'Lente',
  'glass_lean': 'Menú More',
  'press_glow': 'Glow de pulsación',
  'glass_lean_morph': 'Morph',
  'text_loupe': 'Lupa de texto',
  'bar_blur_mip': 'Barra',
};

class MaterialCatalogue extends StatelessWidget {
  const MaterialCatalogue({super.key, required this.settings});
  final AppearanceSettings settings;
  @override
  Widget build(BuildContext context) => ReviewScaffold(
    settings: settings,
    title: 'Glass original',
    body: (context) => ListView(
      padding: reviewInsets(context),
      children: [
        NativeGroup(
          model: settings,
          child: Column(
            children: [
              for (final family in materialNames.entries)
                reviewEntry(
                  context,
                  settings,
                  family.value,
                  MaterialReviewPage(settings: settings, family: family.key),
                  last: family.key == materialNames.keys.last,
                ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        Text(
          'Cada muestra se renderiza con sus entradas y geometría originales. Alterna entre captura, shader y comparación. Puedes ampliar el resultado y explorar los parámetros; Restablecer vuelve a los valores capturados.',
          style: nativeText(
            settings,
            size: 15,
            height: 1.43,
            color: settings.secondary,
          ),
        ),
      ],
    ),
  );
}

List<double> _numbers(Object? value) {
  if (value is num) return [value.toDouble()];
  return (value as List).expand(_numbers).toList();
}

Object _replace(Object value, int target, double next) {
  var position = 0;
  Object walk(Object item) {
    if (item is num) return position++ == target ? next : item;
    return [for (final child in item as List) walk(child as Object)];
  }

  return walk(value);
}

Future<ui.Image> _png(String asset) async {
  final data = await rootBundle.load(asset);
  final codec = await ui.instantiateImageCodec(
    data.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes),
  );
  try {
    return (await codec.getNextFrame()).image;
  } finally {
    codec.dispose();
  }
}

Future<ui.Image> _nativeImage(Map asset, Map metadata) async {
  final floating = metadata['pixel_format_index'] == 8;
  final name = asset[floating ? 'raw' : 'premul8'] as String;
  final data = await rootBundle.load('assets/review/filters/$name');
  final done = Completer<ui.Image>();
  ui.decodeImageFromPixels(
    data.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes),
    metadata['width'] as int,
    metadata['height'] as int,
    floating ? ui.PixelFormat.rgbaFloat32 : ui.PixelFormat.rgba8888,
    done.complete,
    targetFormat: floating
        ? ui.TargetPixelFormat.rgbaFloat32
        : ui.TargetPixelFormat.dontCare,
  );
  return done.future;
}

class MaterialSession {
  MaterialSession(this.family);
  final String family;
  Map<String, dynamic> fields = {}, baseline = {};
  Map? captured;
  ui.FragmentProgram? originalProgram, structuredProgram;
  ui.FragmentShader? shader;
  ui.Image? original, rendered, background;
  final inputs = <ui.Image>[];
  reference.RecoveredCapturedSurfaceRenderer? more;
  reference.RecoveredCapturedSurfaceFrame? moreFrame;
  bool structured = false;
  Rect geometry = Rect.zero;
  Size outputSize = Size.zero;
  List<double> matrix = [];
  bool get lens => family == 'glass_lean_lens';
  bool get isMore => family == 'glass_lean';
  Future<void> load() async {
    if (isMore) {
      final recipe = await reference.RecoveredCapturedSurfaceRecipe.loadMore();
      captured = recipe.data;
      fields = Map<String, dynamic>.from(recipe.material['fields'] as Map);
      outputSize = Size(
        recipe.outputSize[0].toDouble(),
        recipe.outputSize[1].toDouble(),
      );
      original = await _png('assets/review/more/original.png');
      inputs.add(await _png('assets/review/more/input.png'));
    } else if (lens) {
      final fixture =
          jsonDecode(
                await rootBundle.loadString('assets/review/lens/fixture.json'),
              )
              as Map;
      captured = fixture;
      fields = Map<String, dynamic>.from(fixture['uniforms'] as Map);
      inputs.add(await _png('assets/review/lens/level.png'));
      inputs.add(await _png('assets/review/lens/base.png'));
      original = await _png('assets/review/lens/screen-original.png');
      background = await _png('assets/review/lens/screen-background.png');
      final g = _numbers(fixture['geometry']);
      geometry = Rect.fromLTRB(g[0], g[1], g[2], g[3]);
      outputSize = const Size(520, 320);
    } else {
      final cases =
          jsonDecode(
                await rootBundle.loadString('assets/review/filters/cases.json'),
              )
              as Map;
      captured = (cases['cases'] as List).cast<Map>().singleWhere(
        (c) => c['family'] == family,
      );
      fields = Map<String, dynamic>.from(captured!['fields'] as Map);
      final g = _numbers(captured!['geometry']);
      geometry = Rect.fromLTRB(g[0], g[1], g[2], g[3]);
      matrix = _numbers(captured!['draw_matrix_column_major']);
      outputSize = Size(
        (captured!['output']['width'] as num).toDouble(),
        (captured!['output']['height'] as num).toDouble(),
      );
      inputs.add(
        await _nativeImage(
          captured!['assets']['filterin'] as Map,
          captured!['input'] as Map,
        ),
      );
      for (final extra in captured!['extras'] as List) {
        inputs.add(
          await _nativeImage(
            captured!['assets'][extra['mode']] as Map,
            extra as Map,
          ),
        );
      }
      original = await _png(
        'assets/review/filters/${captured!['assets']['filterout']['png']}',
      );
    }
    baseline = copyRenderFields(fields);
    if (!isMore) {
      originalProgram = await reference.loadRecoveredProgram(family);
      structuredProgram = await reference.loadRecoveredProgram(
        family,
        encoding: reference.RecoveredProgramEncoding.structured,
      );
    }
    await render();
  }

  Future<void> render() async {
    if (isMore) {
      final data = copyRenderFields(captured!);
      (data['material'] as Map)['fields'] = fields;
      final next = await reference.RecoveredCapturedSurfaceRenderer.create(
        reference.RecoveredCapturedSurfaceRecipe.fromJson(data),
      );
      final frame = next.render(inputs.first);
      final previousFrame = moreFrame, previous = more;
      more = next;
      moreFrame = frame;
      rendered = frame.image;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        previousFrame?.dispose();
        previous?.dispose();
      });
      return;
    }
    final next = (structured ? structuredProgram : originalProgram)!
        .fragmentShader();
    bindReviewFloats(next, fields);
    if (lens) {
      next.setImageSampler(
        reviewSamplerIndex(next, 'uLevel'),
        inputs[0],
        filterQuality: ui.FilterQuality.low,
      );
      next.setImageSampler(
        reviewSamplerIndex(next, 'uSharp'),
        inputs[1],
        filterQuality: ui.FilterQuality.low,
      );
    } else {
      final sampling = captured!['sampling'] as List;
      for (var i = 0; i < inputs.length; i++) {
        next.setImageSampler(
          i,
          inputs[i],
          filterQuality: ui.FilterQuality.values[sampling[i] as int],
        );
      }
    }
    final previousShader = shader, previousImage = rendered;
    shader = next;
    if (!lens) {
      final recorder = ui.PictureRecorder();
      final canvas = ui.Canvas(recorder)
        ..transform(Float64List.fromList(matrix));
      canvas.drawRect(geometry, ui.Paint()..shader = next);
      final picture = recorder.endRecording();
      rendered = picture.toImageSync(
        outputSize.width.toInt(),
        outputSize.height.toInt(),
      );
      picture.dispose();
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      previousImage?.dispose();
      previousShader?.dispose();
    });
  }

  void paint(Canvas canvas, Size size, bool source) {
    canvas.save();
    canvas.clipRect(Offset.zero & size);
    canvas.scale(
      size.width / outputSize.width,
      size.height / outputSize.height,
    );
    if (lens) {
      canvas.translate(-340, -2200);
      canvas.drawImage(source ? original! : background!, Offset.zero, Paint());
      if (!source) {
        for (final raw in captured!['picture']['ancestors'] as List) {
          final node = raw as Map;
          if (node['tag'] == 10) {
            canvas.transform(
              Float64List.fromList(_numbers(node['column_major'])),
            );
          }
          if (node['tag'] == 7) {
            final xy = _numbers(node['native_double_xy']);
            canvas.translate(xy[0], xy[1]);
          }
        }
        canvas.drawRect(geometry, Paint()..shader = shader);
      }
    } else {
      canvas.drawImage(
        source ? original! : rendered!,
        Offset.zero,
        Paint()..filterQuality = FilterQuality.none,
      );
    }
    canvas.restore();
  }

  void dispose() {
    original?.dispose();
    background?.dispose();
    if (isMore) {
      moreFrame?.dispose();
      more?.dispose();
    } else {
      rendered?.dispose();
    }
    shader?.dispose();
    for (final image in inputs) {
      image.dispose();
    }
  }
}

class MaterialReviewPage extends StatefulWidget {
  const MaterialReviewPage({
    super.key,
    required this.settings,
    required this.family,
  });
  final AppearanceSettings settings;
  final String family;
  @override
  State<MaterialReviewPage> createState() => _MaterialReviewPageState();
}

class _MaterialReviewPageState extends State<MaterialReviewPage> {
  late final session = MaterialSession(widget.family);
  bool ready = false, busy = false, parameters = false, white = false;
  String? error;
  String mode = 'Shader', uniform = '';
  int component = 0;
  double divider = .5;
  Future<void> pending = Future.value();
  @override
  void initState() {
    super.initState();
    pending = _load();
  }

  Future<void> _load() async {
    try {
      await session.load();
      if (mounted)
        setState(() {
          ready = true;
          uniform = session.fields.keys.first;
        });
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    }
  }

  Future<void> _render() {
    if (busy) return pending;
    pending = _renderFrame();
    return pending;
  }

  Future<void> _renderFrame() async {
    if (busy) return;
    setState(() => busy = true);
    try {
      await session.render();
    } catch (e) {
      error = '$e';
    }
    if (mounted) setState(() => busy = false);
  }

  @override
  void dispose() {
    pending.whenComplete(() {
      WidgetsBinding.instance.addPostFrameCallback((_) => session.dispose());
      WidgetsBinding.instance.scheduleFrame();
    });
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.settings;
    return ReviewScaffold(
      settings: s,
      title: materialNames[widget.family]!,
      body: (context) => ListView(
        padding: reviewInsets(context),
        children: [
          if (error != null)
            Text('No se pudo renderizar: $error', style: nativeText(s)),
          if (!ready && error == null)
            Text(
              'Cargando material…',
              style: nativeText(s, color: s.secondary),
            ),
          if (ready) ...[
            NativeGroup(
              model: s,
              child: Row(
                children: [
                  for (final choice in ['Original', 'Shader', 'Comparar'])
                    Expanded(
                      child: GestureDetector(
                        behavior: HitTestBehavior.opaque,
                        onTap: () => setState(() => mode = choice),
                        child: SizedBox(
                          height: 48,
                          child: Center(
                            child: Text(
                              choice,
                              style: nativeText(
                                s,
                                size: 15,
                                color: mode == choice ? s.accent : s.secondary,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              height: 300,
              child: ColoredBox(
                color: white ? Colors.white : Colors.black,
                child: InteractiveViewer(
                  minScale: 1,
                  maxScale: 8,
                  child: Center(
                    child: AspectRatio(
                      aspectRatio: session.outputSize.aspectRatio,
                      child: RepaintBoundary(
                        child: CustomPaint(
                          painter: _MaterialPainter(session, mode, divider),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
            if (mode == 'Comparar')
              RecoveredPointerExclusionRegion(
                behavior: HitTestBehavior.deferToChild,
                child: Slider(
                  value: divider,
                  onChanged: (v) => setState(() => divider = v),
                  semanticFormatterCallback: (v) =>
                      '${(v * 100).round()}% original',
                ),
              ),
            const SizedBox(height: 16),
            Text(
              session.lens || session.isMore
                  ? 'Referencia aceptada: máximo 1/255. Esta vista permite ampliar la muestra.'
                  : 'Referencia aceptada: RGBA exacto con los programas original y estructurado. Esta vista permite ampliar la muestra.',
              style: nativeText(
                s,
                size: 13,
                height: 16 / 13,
                color: s.secondary,
              ),
            ),
            const SizedBox(height: 16),
            NativeGroup(
              model: s,
              child: Column(
                children: [
                  NativeRow(
                    'Fondo blanco',
                    model: s,
                    last: false,
                    trailing: InteractiveToggle(
                      label: 'Fondo blanco',
                      settings: s,
                      value: white,
                      onChanged: (v) => setState(() => white = v),
                    ),
                  ),
                  if (!session.isMore)
                    NativeRow(
                      'Port estructurado',
                      model: s,
                      last: false,
                      trailing: InteractiveToggle(
                        label: 'Port estructurado',
                        settings: s,
                        value: session.structured,
                        onChanged: (v) {
                          session.structured = v;
                          _render();
                        },
                      ),
                    ),
                  NativeRow(
                    'Parámetros',
                    model: s,
                    last: false,
                    onTap: () => setState(() => parameters = !parameters),
                    trailing: Text(
                      parameters ? 'Ocultar' : 'Explorar',
                      style: nativeText(s, color: s.secondary),
                    ),
                  ),
                  NativeRow(
                    'Restablecer',
                    model: s,
                    onTap: () {
                      session.fields =
                          jsonDecode(jsonEncode(session.baseline))
                              as Map<String, dynamic>;
                      _render();
                    },
                  ),
                ],
              ),
            ),
            if (parameters) ...[
              const SizedBox(height: 16),
              Text(
                'Variación experimental. Restablecer recupera los valores capturados.',
                style: nativeText(s, size: 13, color: s.secondary),
              ),
              const SizedBox(height: 12),
              NativeGroup(
                model: s,
                child: Column(
                  children: [
                    for (final key in session.fields.keys)
                      NativeRow(
                        key,
                        model: s,
                        onTap: () => setState(() {
                          uniform = key;
                          component = 0;
                        }),
                        trailing: Text(
                          key == uniform
                              ? 'Seleccionado'
                              : '${_numbers(session.fields[key]).length}',
                          style: nativeText(
                            s,
                            size: 13,
                            color: key == uniform ? s.accent : s.secondary,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              if (uniform.isNotEmpty) ...[
                Text(
                  '$uniform [$component] = ${_numbers(session.fields[uniform])[component].toStringAsPrecision(6)}',
                  style: nativeText(s, size: 15),
                ),
                Wrap(
                  children: [
                    for (
                      var i = 0;
                      i < _numbers(session.fields[uniform]).length;
                      i++
                    )
                      TextButton(
                        onPressed: () => setState(() => component = i),
                        child: Text('$i'),
                      ),
                  ],
                ),
                _parameterSlider(),
              ],
            ],
          ],
        ],
      ),
    );
  }

  Widget _parameterSlider() {
    final original = _numbers(session.baseline[uniform])[component];
    final span = math.max(1.0, original.abs() * .5);
    final value = _numbers(session.fields[uniform])[component];
    return RecoveredPointerExclusionRegion(
      behavior: HitTestBehavior.deferToChild,
      child: Slider(
        value: value.clamp(original - span, original + span),
        min: original - span,
        max: original + span,
        onChanged: busy
            ? null
            : (v) {
                session.fields[uniform] = _replace(
                  session.fields[uniform] as Object,
                  component,
                  v,
                );
                _render();
              },
      ),
    );
  }
}

class _MaterialPainter extends CustomPainter {
  _MaterialPainter(this.session, this.mode, this.divider);
  final MaterialSession session;
  final String mode;
  final double divider;
  @override
  void paint(Canvas canvas, Size size) {
    session.paint(canvas, size, mode == 'Original');
    if (mode == 'Comparar') {
      canvas.save();
      canvas.clipRect(Rect.fromLTWH(0, 0, size.width * divider, size.height));
      session.paint(canvas, size, true);
      canvas.restore();
      canvas.drawLine(
        Offset(size.width * divider, 0),
        Offset(size.width * divider, size.height),
        Paint()
          ..color = Colors.red
          ..strokeWidth = 1,
      );
    }
  }

  @override
  bool shouldRepaint(_MaterialPainter old) => true;
}
