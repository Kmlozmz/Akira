import '../recovered/render_bindings.dart';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'live_glass.dart';
import 'frame_audit.dart';

Map<String, dynamic> paletteButtonFields(Map source, Color accent, bool done) {
  final fields = copyRenderFields(source);
  if (!done) return fields;
  final rgb = [accent.r, accent.g, accent.b];
  final ambient = (fields['uTintA'] as List).cast<num>();
  final transmission = [
    for (var i = 0; i < 3; i++) (rgb[i] - .025 * ambient[i]).clamp(0.0, 1.0),
  ];
  fields['uTr'] = [...transmission, (fields['uTr'] as List)[3]];
  fields['uTintO'] = [
    for (var i = 0; i < 3; i++) rgb[i] - .15 * ambient[i],
    1.0,
  ];
  fields['uTg'] = [
    for (final row in ['uVm0', 'uVm1', 'uVm2'])
      ((fields[row][0] as num) * transmission[0] +
              (fields[row][1] as num) * transmission[1] +
              (fields[row][2] as num) * transmission[2] +
              (fields[row][3] as num))
          .clamp(0.0, 1.0),
    (fields['uTg'] as List)[3],
  ];
  return fields;
}

class PaletteGlassFace extends StatefulWidget {
  const PaletteGlassFace({
    super.key,
    required this.done,
    required this.accent,
    required this.child,
  });
  final bool done;
  final Color accent;
  final Widget child;
  @override
  State<PaletteGlassFace> createState() => _PaletteGlassFaceState();
}

class _PaletteGlassFaceState extends State<PaletteGlassFace> {
  final paintKey = GlobalKey();
  ui.FragmentProgram? program;
  ui.FragmentShader? shader;
  @override
  void dispose() {
    shader?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final owner = LivePaneScope.of(context);
    final settings = owner?.widget.source?.settings;
    final enabled =
        owner?.widget.source?.enabled == true &&
        settings?.style == 'Glass' &&
        settings?.reduceTransparency == false;
    if (owner != null && program != owner.leanProgram) {
      shader?.dispose();
      program = owner.leanProgram;
      shader = program?.fragmentShader();
    }
    if (!enabled)
      return DecoratedBox(
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: widget.done ? widget.accent : const Color(0xff454547),
        ),
        child: widget.child,
      );
    return CustomPaint(
      key: paintKey,
      painter: _PaletteGlassFacePainter(owner!, this),
      child: widget.child,
    );
  }
}

class _PaletteGlassFacePainter extends CustomPainter {
  _PaletteGlassFacePainter(this.owner, this.face)
    : super(repaint: owner.signal);
  final LiveGlassPaneState owner;
  final _PaletteGlassFaceState face;
  @override
  void paint(Canvas canvas, Size size) {
    final shader = face.shader;
    final recipe = owner.paletteRecipes?[face.widget.done ? 'done' : 'close'];
    if (shader == null || recipe == null) return;
    owner.prepareButtonBackdrop();
    final base = owner.buttonBase, mip = owner.buttonMip;
    if (base == null || mip == null) return;
    final button =
        face.paintKey.currentContext?.findRenderObject() as RenderBox?;
    final pane = owner.boxKey.currentContext?.findRenderObject() as RenderBox?;
    if (button == null || pane == null) return;
    final origin = pane.globalToLocal(button.localToGlobal(Offset.zero));
    final unit =
        pane.globalToLocal(button.localToGlobal(const Offset(1, 0))) - origin;
    final scale = unit.dx;
    final ratio = MediaQuery.devicePixelRatioOf(owner.context) / 2 * scale;
    final source = Map<String, dynamic>.from(recipe);
    if (face.widget.done) {
      final captured =
          (owner.paletteRecipes?['accent_materials'] as Map?)?[face
              .widget
              .accent
              .toARGB32()
              .toString()];
      if (captured is Map) source.addAll(Map<String, dynamic>.from(captured));
    }
    final fields = paletteButtonFields(
      source,
      face.widget.accent,
      face.widget.done,
    );
    fields['uBd'] = [
      origin.dx / scale,
      origin.dy / scale,
      ratio / base.width,
      ratio / base.height,
    ];
    fields['uTap'] = [
      .5 / mip.width,
      .5 / mip.height,
      fields['uTap'][2],
      fields['uTap'][3],
    ];
    fields['uShape'][3] = 1 / MediaQuery.devicePixelRatioOf(owner.context);
    ReviewFrameAudit.surface(
      face.widget.done ? 'palette-done' : 'palette-close',
      {
        'base_size': [base.width, base.height],
        'mip_size': [mip.width, mip.height],
        'fields': {
          for (final name in [
            'uBd',
            'uTap',
            'uShape',
            'uTr',
            'uTg',
            'uTintA',
            'uTintO',
          ])
            name: fields[name],
        },
      },
    );
    bindReviewFloats(shader, fields);
    for (final name in ['uLevel', 'uBleedL', 'uSharp', 'uNext']) {
      shader.setImageSampler(
        reviewSamplerIndex(shader, name),
        name == 'uLevel' || name == 'uBleedL' ? mip : base,
        filterQuality: ui.FilterQuality.low,
      );
    }
    canvas.drawRect(
      Rect.fromLTRB(-16, -16, size.width + 16, size.height + 16),
      Paint()..shader = shader,
    );
  }

  @override
  bool shouldRepaint(_PaletteGlassFacePainter old) => true;
}
