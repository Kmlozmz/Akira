import 'render_bindings.dart';
import 'regional_capture_stage.dart';

import 'dart:convert';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:inotes_recovered/inotes_recovered.dart';

import 'path_keys.dart';
import 'path_paint.dart';

const recoveredFont = 'packages/inotes_recovered/InotesPretendard';
const recoveredNoteSizes = [14.0, 15.0, 16.0, 17.0, 19.0, 21.0, 23.0];

class RecoveredLabModel extends ChangeNotifier {
  bool dark = true;
  bool reduceMotion = false;
  bool guides = false;
  int accentIndex = 0;
  int noteSizeIndex = 3;
  Color get accent => inotesPresetColors[accentIndex];
  Color get background =>
      dark ? const Color(0xff000000) : const Color(0xfff2f2f7);
  Color get surface => dark ? const Color(0xff1c1c1e) : const Color(0xffffffff);
  Color get text => dark ? const Color(0xffffffff) : const Color(0xff000000);
  Color get secondary =>
      dark ? const Color(0x99ebebf5) : const Color(0x993c3c43);
  Color get divider => dark ? const Color(0x80545458) : const Color(0x493c3c43);
  double get noteSize => recoveredNoteSizes[noteSizeIndex];
  String get defaultInterfaceFont => recoveredFont;
  String get interfaceFont => defaultInterfaceFont;
  double get interfaceScale => 1;
  void configureMaterial(
    Map<String, Object?> fields, {
    bool dock = false,
    bool readable = false,
  }) {}
  void change(VoidCallback update) {
    update();
    notifyListeners();
  }
}

TextStyle nativeText(
  RecoveredLabModel model, {
  double size = 17,
  FontWeight weight = FontWeight.w400,
  double? height,
  Color? color,
  bool note = false,
}) => TextStyle(
  inherit: false,
  fontFamily: model.interfaceFont,
  fontSize: size * model.interfaceScale,
  fontWeight: weight,
  height: height,
  letterSpacing: 0,
  wordSpacing: 0,
  color: color ?? model.text,
  fontFeatures: note ? const [ui.FontFeature('cv11')] : const [],
);

class NativeGroup extends StatelessWidget {
  const NativeGroup({
    super.key,
    required this.model,
    required this.child,
    this.radius = 26,
  });
  final RecoveredLabModel model;
  final Widget child;
  final double radius;
  @override
  Widget build(BuildContext context) => ClipRSuperellipse(
    borderRadius: BorderRadius.circular(radius),
    child: ColoredBox(color: model.surface, child: child),
  );
}

class NativeRow extends StatelessWidget {
  const NativeRow(
    this.label, {
    super.key,
    required this.model,
    this.trailing,
    this.onTap,
    this.last = true,
    this.height = 52,
  });
  final String label;
  final RecoveredLabModel model;
  final Widget? trailing;
  final VoidCallback? onTap;
  final bool last;
  final double height;
  VoidCallback? get _activate {
    if (onTap != null) return onTap;
    final toggle = trailing;
    if (toggle is NativeToggle && toggle.enabled)
      return () => toggle.onChanged(!toggle.value);
    return null;
  }

  @override
  Widget build(BuildContext context) => Semantics(
    button: onTap != null,
    child: GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: _activate,
      child: ConstrainedBox(
        constraints: BoxConstraints(minHeight: height),
        child: Stack(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              child: Row(
                children: [
                  Expanded(
                    child: ExcludeSemantics(
                      excluding: trailing is NativeToggle,
                      child: Text(label, style: nativeText(model)),
                    ),
                  ),
                  if (trailing != null) ...[
                    const SizedBox(width: 12),
                    trailing!,
                  ],
                ],
              ),
            ),
            if (!last)
              Positioned(
                left: 16,
                right: 16,
                bottom: 0,
                child: SizedBox(
                  height: 1,
                  child: ColoredBox(color: model.divider),
                ),
              ),
          ],
        ),
      ),
    ),
  );
}

class NativePathIcon extends StatelessWidget {
  const NativePathIcon(
    this.name, {
    super.key,
    required this.color,
    this.size = 24,
  });
  final String name;
  final Color color;
  final double size;
  @override
  Widget build(BuildContext context) => SizedBox.square(
    dimension: size,
    child: CustomPaint(painter: _NativePathPainter(name, color)),
  );
}

class _NativePathPainter extends CustomPainter {
  _NativePathPainter(this.name, this.color);
  final String name;
  final Color color;
  @override
  void paint(Canvas canvas, Size size) {
    final factory = inotesRecoveredPaths[name];
    if (factory == null) return;
    final path = factory();
    final bounds = path.getBounds();
    final scale = (size.width / bounds.width)
        .clamp(0.0, size.height / bounds.height)
        .clamp(0.0, 1.0);
    canvas.translate(size.width / 2, size.height / 2);
    canvas.scale(scale);
    canvas.translate(-bounds.center.dx, -bounds.center.dy);
    final paint = inotesObservedPathPaints[nativePathPaintKeys[name]]!.first
        .toPaint(color: color);
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(_NativePathPainter old) =>
      old.name != name || old.color != color;
}

class NativeToggle extends StatelessWidget {
  const NativeToggle({
    super.key,
    required this.value,
    required this.onChanged,
    required this.model,
    required this.label,
    this.enabled = true,
  });
  final bool value, enabled;
  final ValueChanged<bool> onChanged;
  final RecoveredLabModel model;
  final String label;
  @override
  Widget build(BuildContext context) => Semantics(
    label: label,
    toggled: value,
    enabled: enabled,
    child: GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: enabled ? () => onChanged(!value) : null,
      child: SizedBox(
        width: 63,
        height: 44,
        child: Center(
          child: SizedBox(
            width: 63,
            height: 28,
            child: CustomPaint(
              painter: _TogglePainter(
                value,
                enabled ? model.accent : model.divider,
                model.dark,
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

class _TogglePainter extends CustomPainter {
  _TogglePainter(this.value, this.accent, this.dark);
  final bool value, dark;
  final Color accent;
  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRRect(
      RRect.fromRectAndRadius(Offset.zero & size, const Radius.circular(14)),
      Paint()
        ..color = value
            ? accent
            : (dark ? const Color(0x4debebf5) : const Color(0x4d3c3c43)),
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(value ? 24 : 2, 2, 37, 24),
        const Radius.circular(12),
      ),
      Paint()..color = Colors.white,
    );
  }

  @override
  bool shouldRepaint(_TogglePainter old) =>
      value != old.value || accent != old.accent || dark != old.dark;
}

class NativeSizeSlider extends StatelessWidget {
  const NativeSizeSlider({super.key, required this.model});
  final RecoveredLabModel model;
  @override
  Widget build(BuildContext context) => Semantics(
    label: 'Tamaño de nota',
    value: '${model.noteSize.toInt()}',
    increasedValue:
        '${recoveredNoteSizes[(model.noteSizeIndex + 1).clamp(0, 6)].toInt()}',
    decreasedValue:
        '${recoveredNoteSizes[(model.noteSizeIndex - 1).clamp(0, 6)].toInt()}',
    slider: true,
    onIncrease: model.noteSizeIndex < 6
        ? () => model.change(() => model.noteSizeIndex++)
        : null,
    onDecrease: model.noteSizeIndex > 0
        ? () => model.change(() => model.noteSizeIndex--)
        : null,
    child: LayoutBuilder(
      builder: (context, constraints) {
        void choose(double x) {
          final width = constraints.maxWidth - 37;
          final index = (((x - 18.5) / width).clamp(0.0, 1.0) * 6).round();
          if (index != model.noteSizeIndex)
            model.change(() => model.noteSizeIndex = index);
        }

        return GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTapDown: (d) => choose(d.localPosition.dx),
          onHorizontalDragUpdate: (d) => choose(d.localPosition.dx),
          child: SizedBox(
            height: 44,
            child: CustomPaint(
              painter: _SizePainter(model.noteSizeIndex, model.accent),
            ),
          ),
        );
      },
    ),
  );
}

class _SizePainter extends CustomPainter {
  _SizePainter(this.index, this.accent);
  final int index;
  final Color accent;
  @override
  void paint(Canvas canvas, Size size) {
    final width = size.width - 37, left = 18.5;
    final center = left + width * index / 6;
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(0, 19, size.width, 6),
        const Radius.circular(3),
      ),
      Paint()..color = const Color(0x35787880),
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(0, 19, center, 6),
        const Radius.circular(3),
      ),
      Paint()..color = accent,
    );
    for (var i = 0; i < 7; i++)
      canvas.drawCircle(
        Offset(left + width * i / 6, 31),
        2,
        Paint()..color = const Color(0xff48484a),
      );
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(center - 18.5, 11, 37, 24),
        const Radius.circular(12),
      ),
      Paint()..color = const Color(0x24000000),
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(center - 18.5, 10, 37, 24),
        const Radius.circular(12),
      ),
      Paint()..color = Colors.white,
    );
  }

  @override
  bool shouldRepaint(_SizePainter old) =>
      index != old.index || accent != old.accent;
}

class _RepaintSignal extends ChangeNotifier {
  void request() => notifyListeners();
}

class NativeGlassStage extends StatefulWidget {
  const NativeGlassStage({
    super.key,
    required this.model,
    required this.background,
    required this.foreground,
    this.enableShaders = true,
  });
  final RecoveredLabModel model;
  final Widget background, foreground;
  final bool enableShaders;
  @override
  State<NativeGlassStage> createState() => NativeGlassStageState();
}

class NativeGlassStageState extends State<NativeGlassStage> {
  final capture = LiveCaptureController();
  final lensCapture = RegionalLensCapture();
  final repaint = _RepaintSignal();
  final stageKey = GlobalKey();
  ui.FragmentProgram? lean;
  RecoveredMipChain? chain;
  Map<String, dynamic>? darkRecipes, lightRecipes;
  ui.Image? mip;
  int generation = -1;
  String? error;
  bool _loading = false;
  @override
  void initState() {
    super.initState();
    if (widget.enableShaders) _load();
  }

  @override
  void didUpdateWidget(NativeGlassStage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.enableShaders && !oldWidget.enableShaders && lean == null) {
      _load();
    }
  }

  Future<void> _load() async {
    if (_loading) return;
    _loading = true;
    try {
      final programs = await Future.wait([
        loadRecoveredProgram('glass_lean'),
        loadRecoveredProgram('glass_mip'),
      ]);
      final dark = jsonDecode(
        await rootBundle.loadString('assets/recovered_components/dark.json'),
      ) as Map<String, dynamic>;
      final light = jsonDecode(
        await rootBundle.loadString('assets/recovered_components/light.json'),
      ) as Map<String, dynamic>;
      if (!mounted) return;
      setState(() {
        lean = programs[0];
        chain = RecoveredMipChain(programs[1]);
        darkRecipes = dark;
        lightRecipes = light;
        error = null;
      });
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    } finally {
      _loading = false;
    }
  }

  Map<String, dynamic>? recipe(String name) =>
      (widget.model.dark ? darkRecipes : lightRecipes)?[name]
          as Map<String, dynamic>?;
  void prepare() {
    final source = capture.image;
    if (source != null && chain != null && generation != capture.generation) {
      mip = chain!.render(source);
      generation = capture.generation;
    }
  }

  @override
  void dispose() {
    chain?.dispose();
    repaint.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      if (constraints.maxWidth <= 0 || constraints.maxHeight <= 0)
        return const SizedBox.shrink();
      return _GlassScope(
        host: this,
        child: !widget.enableShaders
            ? Stack(
                fit: StackFit.expand,
                children: [widget.background, widget.foreground],
              )
            : NotificationListener<ScrollNotification>(
                onNotification: (_) {
                  repaint.request();
                  return false;
                },
                child: RepaintBoundary(
                  child: RegionalCaptureStage(
                    key: stageKey,
                    controller: capture,
                    lens: lensCapture,
                    pixelRatio: MediaQuery.devicePixelRatioOf(context) / 2,
                    repaint: repaint,
                    background: widget.background,
                    foreground: RepaintBoundary(
                      child: CustomPaint(
                        painter: _PrepareGlass(this),
                        child: Stack(
                          fit: StackFit.expand,
                          children: [
                            widget.foreground,
                            if (error != null)
                              Align(
                                alignment: Alignment.bottomCenter,
                                child: Text(
                                  'No se pudo cargar el shader',
                                  style: nativeText(widget.model, size: 13),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
      );
    },
  );
}

class _PrepareGlass extends CustomPainter {
  _PrepareGlass(this.host) : super(repaint: host.repaint);
  final NativeGlassStageState host;
  @override
  void paint(Canvas canvas, Size size) => host.prepare();
  @override
  bool hitTest(Offset position) => false;
  @override
  bool shouldRepaint(_PrepareGlass old) => true;
}

class _GlassScope extends InheritedWidget {
  const _GlassScope({required this.host, required super.child});
  final NativeGlassStageState host;
  @override
  bool updateShouldNotify(_GlassScope old) => true;
}

class NativeGlassButton extends StatefulWidget {
  const NativeGlassButton({
    super.key,
    required this.label,
    required this.child,
    required this.onPressed,
    this.recipe = 'back',
    this.size = const Size(44, 44),
  });
  final String label, recipe;
  final Widget child;
  final VoidCallback? onPressed;
  final Size size;
  @override
  State<NativeGlassButton> createState() => _NativeGlassButtonState();
}

class _NativeGlassButtonState extends State<NativeGlassButton> {
  final paintKey = GlobalKey();
  ui.FragmentShader? shader;
  ui.FragmentProgram? program;
  @override
  void dispose() {
    shader?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final host = context
        .dependOnInheritedWidgetOfExactType<_GlassScope>()!
        .host;
    if (program != host.lean) {
      shader?.dispose();
      program = host.lean;
      shader = program?.fragmentShader();
    }
    return Semantics(
      label: widget.label,
      button: true,
      enabled: widget.onPressed != null,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: widget.onPressed,
        child: SizedBox.fromSize(
          size: widget.size,
          child: CustomPaint(
            key: paintKey,
            painter: _GlassButtonPainter(host, this),
            child: Center(child: ExcludeSemantics(child: widget.child)),
          ),
        ),
      ),
    );
  }
}

class _GlassButtonPainter extends CustomPainter {
  _GlassButtonPainter(this.host, this.button) : super(repaint: host.repaint);
  final NativeGlassStageState host;
  final _NativeGlassButtonState button;
  @override
  void paint(Canvas canvas, Size size) {
    final shader = button.shader, recipe = host.recipe(button.widget.recipe);
    final base = host.capture.image, mip = host.mip;
    if (shader == null || recipe == null || base == null || mip == null) return;
    final stage =
        host.stageKey.currentContext!.findRenderObject()! as RenderBox;
    final box =
        button.paintKey.currentContext!.findRenderObject()! as RenderBox;
    final origin = stage.globalToLocal(box.localToGlobal(Offset.zero));
    final fields = Map<String, Object?>.from(recipe['uniforms'] as Map);
    final unitX =
        stage.globalToLocal(box.localToGlobal(const Offset(1, 0))) - origin;
    final unitY =
        stage.globalToLocal(box.localToGlobal(const Offset(0, 1))) - origin;
    final sx = unitX.dx, sy = unitY.dy;
    fields['uBd'] = [
      origin.dx / sx,
      origin.dy / sy,
      sx / stage.size.width,
      sy / stage.size.height,
    ];
    final tap = List<num>.from(fields['uTap']! as List);
    fields['uTap'] = [.5 / mip.width, .5 / mip.height, tap[2], tap[3]];
    host.widget.model.configureMaterial(fields);
    bindReviewFloats(shader, fields);
    for (final name in ['uLevel', 'uBleedL', 'uSharp', 'uNext'])
      shader.setImageSampler(
        reviewSamplerIndex(shader, name),
        name == 'uLevel' || name == 'uBleedL' ? mip : base,
        filterQuality: ui.FilterQuality.low,
      );
    final r = (recipe['geometry'] as List).cast<num>();
    canvas.drawRect(
      Rect.fromLTRB(
        r[0].toDouble(),
        r[1].toDouble(),
        r[2].toDouble(),
        r[3].toDouble(),
      ),
      Paint()..shader = shader,
    );
  }

  @override
  bool shouldRepaint(_GlassButtonPainter old) => true;
}
