import '../../design_system/check_mark.dart';

import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart' show showCupertinoModalPopup;
import 'package:inotes_design_reference/inotes_design_reference.dart'
    as recovered;

import '../recovered/components.dart';
import 'settings.dart';
import 'live_glass.dart';
import 'interactive_controls.dart';
import 'frame_audit.dart';
import 'palette_glass_face.dart';

Future<void> showAppearanceColors(
  BuildContext context,
  AppearanceSettings settings,
) async {
  ReviewFrameAudit.begin('Color Theme');
  final source = ReviewCaptureScope.of(context);
  await showCupertinoModalPopup<void>(
    context: context,
    barrierDismissible: true,
    barrierColor: settings.dark
        ? const Color(0x7a000000)
        : const Color(0x33000000),
    builder: (_) => _ColorSheet(settings: settings, source: source),
  );
}

class _ColorSheet extends StatefulWidget {
  const _ColorSheet({required this.settings, required this.source});
  final AppearanceSettings settings;
  final ReviewCaptureScope? source;
  @override
  State<_ColorSheet> createState() => _ColorSheetState();
}

class _ColorSheetState extends State<_ColorSheet> {
  late int pending = widget.settings.accentArgb;
  @override
  Widget build(BuildContext context) {
    final s = widget.settings;
    return RepaintBoundary(
      child: Stack(
        children: [
          Positioned(
            left: ((MediaQuery.sizeOf(context).width - 384) / 2).clamp(
              8,
              double.infinity,
            ),
            right: ((MediaQuery.sizeOf(context).width - 384) / 2).clamp(
              8,
              double.infinity,
            ),
            bottom: MediaQuery.paddingOf(context).bottom + 4,
            child: Material(
              color: Colors.transparent,
              child: LiveGlassPane(
                composes: false,
                surface: GlassPaneSurface.color,
                source: widget.source,
                radius: 36.48,
                child: _ScaledPopupContent(
                  height: 215.52,
                  child: Column(
                    children: [
                      _SheetHeader(
                        settings: s,
                        title: 'Color Theme',
                        accent: Color(pending),
                        onClose: () => Navigator.pop(context),
                        onDone: () {
                          s.change(() => s.accentArgb = pending);
                          Navigator.pop(context);
                        },
                      ),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(36, 12, 36, 0),
                        child: GridView.count(
                          padding: EdgeInsets.zero,
                          shrinkWrap: true,
                          physics: const NeverScrollableScrollPhysics(),
                          crossAxisCount: 5,
                          clipBehavior: Clip.none,
                          mainAxisSpacing: 26,
                          crossAxisSpacing: 32,
                          childAspectRatio: 1,
                          children: [
                            for (
                              var i = 0;
                              i < recovered.inotesPresetColors.length;
                              i++
                            )
                              ColorDot(
                                diameter: 36,
                                label: [
                                  'Yellow',
                                  'Orange',
                                  'Green',
                                  'Cyan',
                                  'Blue',
                                  'Indigo',
                                  'Purple',
                                  'Pink',
                                  'Red',
                                ][i],
                                color: recovered.inotesPresetColors[i],
                                selected:
                                    pending ==
                                    recovered.inotesPresetColors[i].toARGB32(),
                                onTap: () => setState(
                                  () => pending = recovered
                                      .inotesPresetColors[i]
                                      .toARGB32(),
                                ),
                              ),
                            ColorDot(
                              diameter: 36,
                              label: 'Custom',
                              rainbow: true,
                              color: Colors.transparent,
                              onTap: () async {
                                await showCupertinoModalPopup<void>(
                                  context: context,
                                  barrierDismissible: true,
                                  barrierColor: s.dark
                                      ? const Color(0x7a000000)
                                      : const Color(0x33000000),
                                  builder: (_) => _CustomSheet(
                                    settings: s,
                                    source: widget.source,
                                    onCommitted: (color) {
                                      if (mounted)
                                        setState(() => pending = color);
                                    },
                                  ),
                                );
                              },
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _CustomSheet extends StatefulWidget {
  const _CustomSheet({
    required this.settings,
    required this.source,
    required this.onCommitted,
  });
  final AppearanceSettings settings;
  final ReviewCaptureScope? source;
  final ValueChanged<int> onCommitted;
  @override
  State<_CustomSheet> createState() => _CustomSheetState();
}

class _CustomSheetState extends State<_CustomSheet> {
  String base = recovered.inotesCustomPalettes.keys.first;
  late int pending = widget.settings.accentArgb;
  @override
  Widget build(BuildContext context) {
    final s = widget.settings;
    return LayoutBuilder(
      builder: (context, constraints) {
        final shades = recovered.inotesCustomPalettes[base]!;
        final rows = (shades.length / 7).ceil();
        final height = 388.0 + 48 * rows;
        return Stack(
          children: [
            Positioned(
              left: ((MediaQuery.sizeOf(context).width - 384) / 2).clamp(
                8,
                double.infinity,
              ),
              right: ((MediaQuery.sizeOf(context).width - 384) / 2).clamp(
                8,
                double.infinity,
              ),
              bottom: MediaQuery.paddingOf(context).bottom + 4,
              child: Material(
                color: Colors.transparent,
                child: LiveGlassPane(
                  composes: false,
                  surface: GlassPaneSurface.color,
                  source: widget.source,
                  radius: 36.48,
                  child: _ScaledPopupContent(
                    height: height,
                    maxHeight: constraints.maxHeight - 60,
                    child: Column(
                      children: [
                        _SheetHeader(
                          settings: s,
                          title: 'Select color',
                          accent: Color(pending),
                          onClose: () => Navigator.pop(context),
                          onDone: () {
                            s.change(() => s.accentArgb = pending);
                            widget.onCommitted(pending);
                            Navigator.pop(context);
                          },
                        ),
                        Expanded(
                          child: SingleChildScrollView(
                            padding: const EdgeInsets.fromLTRB(36, 12, 36, 36),
                            child: Column(
                              children: [
                                GridView.count(
                                  padding: EdgeInsets.zero,
                                  shrinkWrap: true,
                                  physics: const NeverScrollableScrollPhysics(),
                                  crossAxisCount: 7,
                                  mainAxisSpacing: 8,
                                  crossAxisSpacing: 8,
                                  children: [
                                    for (final palette
                                        in recovered
                                            .inotesCustomPalettes
                                            .entries)
                                      ColorDot(
                                        label: palette.key,
                                        color:
                                            palette.value[500] ??
                                            palette.value[200]!,
                                        onTap: () =>
                                            setState(() => base = palette.key),
                                      ),
                                  ],
                                ),
                                const SizedBox(height: 12),
                                Text(
                                  'Select color shade',
                                  style: nativeText(
                                    s,
                                    size: 14,
                                    height: 1.43,
                                    weight: FontWeight.w500,
                                  ).copyWith(letterSpacing: .1),
                                ),
                                const SizedBox(height: 12),
                                GridView.count(
                                  padding: EdgeInsets.zero,
                                  shrinkWrap: true,
                                  physics: const NeverScrollableScrollPhysics(),
                                  crossAxisCount: 7,
                                  mainAxisSpacing: 8,
                                  crossAxisSpacing: 8,
                                  children: [
                                    for (final shade in shades.entries)
                                      ColorDot(
                                        label: '${shade.key}',
                                        color: shade.value,
                                        onTap: () => setState(
                                          () =>
                                              pending = shade.value.toARGB32(),
                                        ),
                                      ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

class _ScaledPopupContent extends StatelessWidget {
  const _ScaledPopupContent({
    required this.height,
    required this.child,
    this.maxHeight = double.infinity,
  });
  final double height, maxHeight;
  final Widget child;
  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final scale = constraints.maxWidth / 400;
      final extent = math.min(height, maxHeight / scale);
      return SizedBox(
        height: extent * scale,
        child: FittedBox(
          fit: BoxFit.fill,
          child: SizedBox(width: 400, height: extent, child: child),
        ),
      );
    },
  );
}

class _SheetHeader extends StatelessWidget {
  const _SheetHeader({
    required this.settings,
    required this.title,
    required this.onClose,
    required this.onDone,
    this.accent,
  });
  final AppearanceSettings settings;
  final Color? accent;
  final String title;
  final VoidCallback onClose, onDone;
  @override
  Widget build(BuildContext context) => SizedBox(
    height: 72,
    child: Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
      child: Row(
        children: [
          _SheetButton(
            label: 'Close',
            settings: settings,
            accent: accent ?? settings.accent,
            onTap: onClose,
            done: false,
          ),
          Expanded(
            child: Center(
              child: Text(
                settings.ui(title),
                style: nativeText(settings, weight: FontWeight.w600),
              ),
            ),
          ),
          _SheetButton(
            label: 'Done',
            settings: settings,
            accent: accent ?? settings.accent,
            onTap: onDone,
            done: true,
          ),
        ],
      ),
    ),
  );
}

class _SheetButton extends StatelessWidget {
  const _SheetButton({
    required this.label,
    required this.settings,
    required this.onTap,
    required this.done,
    required this.accent,
  });
  final String label;
  final AppearanceSettings settings;
  final VoidCallback onTap;
  final bool done;
  final Color accent;
  @override
  Widget build(BuildContext context) => Semantics(
    label: label,
    button: true,
    child: GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: PressGlow(
        paintPadding: 16,
        child: SizedBox.square(
          dimension: 44,
          child: PaletteGlassFace(
            done: done,
            accent: accent,
            child: ExcludeSemantics(
              child: Center(
                child: done
                    ? const AkiraCheckMark(size: 25)
                    : CustomPaint(
                        size: const Size(22, 22),
                        painter: _ClosePainter(),
                      ),
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

class _ClosePainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white
      ..strokeWidth = 2
      ..strokeCap = StrokeCap.round;
    canvas.drawLine(
      const Offset(3, 3),
      Offset(size.width - 3, size.height - 3),
      paint,
    );
    canvas.drawLine(
      Offset(size.width - 3, 3),
      Offset(3, size.height - 3),
      paint,
    );
  }

  @override
  bool shouldRepaint(_ClosePainter old) => false;
}

class ColorDot extends StatelessWidget {
  const ColorDot({
    super.key,
    required this.label,
    required this.color,
    required this.onTap,
    this.selected = false,
    this.rainbow = false,
    this.diameter = 40,
  });
  final String label;
  final Color color;
  final VoidCallback onTap;
  final bool selected, rainbow;
  final double diameter;
  @override
  Widget build(BuildContext context) => Semantics(
    label: label,
    button: true,
    selected: selected,
    child: GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: CustomPaint(
        painter: _ColorDotPainter(color, diameter, selected, rainbow),
        child: selected
            ? const ExcludeSemantics(
                child: Center(child: AkiraCheckMark(size: 22)),
              )
            : null,
      ),
    ),
  );
}

class _ColorDotPainter extends CustomPainter {
  _ColorDotPainter(this.color, this.diameter, this.selected, this.rainbow);
  final Color color;
  final double diameter;
  final bool selected, rainbow;
  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final rect = Rect.fromCircle(center: center, radius: diameter / 2);
    final paint = Paint()..color = rainbow ? Colors.white : color;
    if (rainbow)
      paint.shader = const SweepGradient(
        colors: [
          Colors.red,
          Colors.yellow,
          Colors.blue,
          Colors.green,
          Colors.red,
        ],
      ).createShader(rect);
    canvas.drawCircle(center, diameter / 2, paint);
    if (selected)
      canvas.drawCircle(
        center,
        diameter / 2 + 2,
        Paint()
          ..color = color
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2,
      );
  }

  @override
  bool shouldRepaint(_ColorDotPainter old) =>
      old.color != color ||
      old.diameter != diameter ||
      old.selected != selected ||
      old.rainbow != rainbow;
}
