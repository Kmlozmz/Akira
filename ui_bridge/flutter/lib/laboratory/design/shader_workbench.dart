import 'dart:convert';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../candidate_layouts.dart';
import '../equivalence.dart' show ComparisonPainter;
import 'catalog.dart';
import 'controls.dart';
import 'foundations.dart';

class ShaderWorkbench extends StatefulWidget {
  const ShaderWorkbench({super.key});
  @override
  State<ShaderWorkbench> createState() => _ShaderWorkbenchState();
}

class _ShaderWorkbenchState extends State<ShaderWorkbench> {
  String name = 'glass_lean_lens';
  String uniform = 'uLight';
  int element = 0;
  int background = 0;
  int revision = 0;
  int generation = 0;
  int checks = 0;
  bool busy = true;
  String? error;
  ui.FragmentShader? a;
  ui.FragmentShader? b;
  late ui.Image image;
  final overrides = <String, double>{};
  final entry = TextEditingController();

  @override
  void initState() {
    super.initState();
    image = makeImage();
    load();
  }

  ui.Image makeImage() {
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    const rect = Rect.fromLTWH(0, 0, 384, 384);
    if (background == 0) {
      canvas.drawRect(
        rect,
        Paint()
          ..shader = const LinearGradient(
            colors: [Color(0xFFC47849), Color(0xFF3F699C), Color(0xFF4B997D)],
          ).createShader(rect),
      );
    } else if (background == 1) {
      for (var y = 0; y < 12; y++) {
        for (var x = 0; x < 12; x++) {
          canvas.drawRect(
            Rect.fromLTWH(x * 32, y * 32, 32, 32),
            Paint()..color = (x + y).isEven ? Colors.white : Colors.black,
          );
        }
      }
    } else {
      canvas.drawRect(rect, Paint()..color = const Color(0xFF1C1C1E));
      for (var y = 20; y < 384; y += 36) {
        canvas.drawRect(
          Rect.fromLTWH(16, y.toDouble(), 290, 4),
          Paint()..color = Colors.white,
        );
      }
    }
    final picture = recorder.endRecording();
    final result = picture.toImageSync(384, 384);
    picture.dispose();
    return result;
  }

  Future<void> load() async {
    final request = ++generation;
    setState(() {
      busy = true;
      error = null;
    });
    ui.FragmentShader? nextA;
    ui.FragmentShader? nextB;
    try {
      nextA = (await ui.FragmentProgram.fromAsset(
        'shaders/$name.frag',
      )).fragmentShader();
      nextB = (await ui.FragmentProgram.fromAsset(
        'shaders/${name}_structured.frag',
      )).fragmentShader();
      var count = 0;
      for (final shader in [nextA, nextB]) {
        for (final (key, _, length) in candidateLayouts[name]!) {
          for (final index in <int>{0, length - 1}) {
            shader.getUniformFloat(key, index);
            count++;
          }
        }
        for (final (key, _) in candidateSamplers[name]!) {
          shader.getImageSampler(key);
          count++;
        }
      }
      if (!mounted || request != generation) {
        nextA.dispose();
        nextB.dispose();
        return;
      }
      a?.dispose();
      b?.dispose();
      a = nextA;
      b = nextB;
      setState(() {
        checks = count;
        busy = false;
        revision++;
      });
    } catch (e) {
      nextA?.dispose();
      nextB?.dispose();
      if (mounted && request == generation)
        setState(() {
          error = '$e';
          busy = false;
        });
    }
  }

  void select(String value) {
    setState(() {
      name = value;
      uniform = candidateLayouts[name]!.first.$1;
      element = 0;
      overrides.clear();
      entry.clear();
    });
    load();
  }

  @override
  void dispose() {
    generation++;
    a?.dispose();
    b?.dispose();
    image.dispose();
    entry.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    final key = '$uniform[$element]';
    return LabPage(
      title: 'Shaders',
      note:
          'Programas recuperados; valores y texturas sintéticos. Que ambas adaptaciones dibujen lo mismo no demuestra que reproduzcan iNotes.',
      children: [
        LabSection(
          'Programa',
          child: LabGroup(
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: DropdownButton<String>(
                  value: name,
                  isExpanded: true,
                  underline: const SizedBox.shrink(),
                  items: [
                    for (final v in candidateLayouts.keys)
                      DropdownMenuItem(value: v, child: Text(v)),
                  ],
                  onChanged: (v) {
                    if (v != null) select(v);
                  },
                ),
              ),
            ],
          ),
        ),
        if (busy)
          const Padding(
            padding: EdgeInsets.all(20),
            child: Text('Cargando programas y comprobando nombres…'),
          ),
        if (error != null) EvidenceNote('No se pudo cargar: $error'),
        if (!busy && error == null && a != null && b != null) ...[
          Text(
            '$checks extremos de uniformes y samplers resueltos por nombre',
            style: TextStyle(fontSize: 12, color: m.secondary),
          ),
          const SizedBox(height: 10),
          LabSection(
            'GLSL / SkSL estructurado',
            note:
                'Mismos parámetros, mismo fondo. Una salida vacía o uniforme es inconclusa. Todos los samplers reciben la misma imagen en esta prueba.',
            child: LayoutBuilder(
              builder: (context, constraints) {
                return SizedBox(
                  height: 170,
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Expanded(
                        child: CustomPaint(
                          painter: WorkbenchPainter(
                            name,
                            a!,
                            image,
                            Map.of(overrides),
                            revision,
                          ),
                        ),
                      ),
                      const SizedBox(width: 4),
                      Expanded(
                        child: CustomPaint(
                          painter: WorkbenchPainter(
                            name,
                            b!,
                            image,
                            Map.of(overrides),
                            revision,
                          ),
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
        ],
        LabSection(
          'Fondo sintético',
          child: LabSegments(
            labels: const ['Color', 'Rejilla', 'Líneas'],
            index: background,
            onChanged: (i) => setState(() {
              background = i;
              final old = image;
              image = makeImage();
              old.dispose();
              revision++;
            }),
          ),
        ),
        LabSection(
          'Inspector de uniformes',
          note:
              'La caja permite cualquier valor finito; el slider es un atajo entre −2 y 2. Los valores no editados mantienen la receta sintética.',
          child: LabGroup(
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: DropdownButton<String>(
                  value: uniform,
                  isExpanded: true,
                  underline: const SizedBox.shrink(),
                  items: [
                    for (final item in candidateLayouts[name]!)
                      DropdownMenuItem(
                        value: item.$1,
                        child: Text('${item.$1} · ${item.$3} componentes'),
                      ),
                  ],
                  onChanged: (v) {
                    if (v != null)
                      setState(() {
                        uniform = v;
                        element = 0;
                        entry.clear();
                      });
                  },
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: DropdownButton<int>(
                  value: element,
                  isExpanded: true,
                  underline: const SizedBox.shrink(),
                  items: [
                    for (
                      var i = 0;
                      i <
                          candidateLayouts[name]!
                              .firstWhere((e) => e.$1 == uniform)
                              .$3;
                      i++
                    )
                      DropdownMenuItem(
                        value: i,
                        child: Text('Componente [$i]'),
                      ),
                  ],
                  onChanged: (v) {
                    if (v != null)
                      setState(() {
                        element = v;
                        entry.clear();
                      });
                  },
                ),
              ),
              LabRow(
                key,
                subtitle: overrides.containsKey(key)
                    ? 'Override: ${overrides[key]}'
                    : 'Receta sintética según tamaño',
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: TextField(
                  controller: entry,
                  keyboardType: const TextInputType.numberWithOptions(
                    decimal: true,
                    signed: true,
                  ),
                  decoration: const InputDecoration(
                    labelText: 'Valor exacto de prueba',
                    hintText: 'Introduce un número finito',
                  ),
                  onSubmitted: (text) => applyValue(text),
                ),
              ),
              LabRow('Aplicar valor', onPressed: () => applyValue(entry.text)),
              LabSlider(
                label: 'Override de $key',
                value: (overrides[key] ?? 0).clamp(-2, 2),
                min: -2,
                max: 2,
                onChanged: (v) => setState(() {
                  overrides[key] = v;
                  revision++;
                }),
              ),
              LabRow(
                'Restablecer receta',
                onPressed: () => setState(() {
                  overrides.clear();
                  entry.clear();
                  revision++;
                }),
              ),
              LabRow(
                'Copiar receta de prueba',
                onPressed: () {
                  Clipboard.setData(
                    ClipboardData(
                      text: jsonEncode({
                        'kind': 'synthetic-not-inotes-runtime',
                        'shader': name,
                        'background': background,
                        'overrides': overrides,
                      }),
                    ),
                  );
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Receta copiada')),
                  );
                },
              ),
            ],
          ),
        ),
        LabSection(
          'Texturas declaradas',
          child: LabGroup(
            children: [
              for (final (sampler, index) in candidateSamplers[name]!)
                LabRow(
                  sampler,
                  subtitle:
                      'Índice $index en adaptación GLSL; imagen sintética compartida',
                ),
            ],
          ),
        ),
      ],
    );
  }

  void applyValue(String text) {
    final value = double.tryParse(text.replaceAll(',', '.'));
    if (value == null || !value.isFinite) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Introduce un número finito')),
      );
      return;
    }
    setState(() {
      overrides['$uniform[$element]'] = value;
      revision++;
    });
    FocusScope.of(context).unfocus();
  }
}

class WorkbenchPainter extends ComparisonPainter {
  WorkbenchPainter(
    super.name,
    super.shader,
    super.image,
    this.overrides,
    this.revision,
  );
  final Map<String, double> overrides;
  final int revision;
  @override
  double value(String key, int i, Size s) =>
      overrides['$key[$i]'] ?? super.value(key, i, s);
  @override
  bool shouldRepaint(covariant WorkbenchPainter oldDelegate) =>
      revision != oldDelegate.revision ||
      shader != oldDelegate.shader ||
      image != oldDelegate.image ||
      name != oldDelegate.name;
}
