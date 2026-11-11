import '../../design_system/component_specs.dart';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';

/// Native painter731b9c: independently animated horizontal gradient translation.
void paintNativeSwitchTrack(
  Canvas canvas,
  Rect rect, {
  required double scalar,
  required Color inactive,
  required Color active,
  required bool enabled,
}) {
  final rounded = RRect.fromRectAndRadius(
    rect,
    Radius.circular(rect.height / 2),
  );
  canvas.drawRRect(rounded, Paint()..color = inactive);
  if (!enabled) return;
  final shader = ui.Gradient.linear(
    Offset(rect.left + AkiraSwitchSpec.trackGradientEnd - scalar, rect.center.dy),
    Offset(rect.left + AkiraSwitchSpec.trackGradientExtent - scalar, rect.center.dy),
    [active, active.withValues(alpha: 0)],
    const [0, 1],
    TileMode.clamp,
  );
  canvas.save();
  canvas.clipRRect(rounded);
  canvas.drawRect(rect, Paint()..shader = shader);
  canvas.restore();
}
