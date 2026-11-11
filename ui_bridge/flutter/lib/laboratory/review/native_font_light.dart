import 'dart:convert';
import 'dart:math' as math;
import 'dart:ui' as ui;
import 'package:flutter/services.dart';

class NativeFontLightProfile {
  NativeFontLightProfile(Map<String, dynamic> data)
    : stops = (data['stops'] as List)
          .cast<num>()
          .map((v) => v.toDouble())
          .toList(),
      alpha = (data['profile_alpha'] as List)
          .cast<num>()
          .map((v) => v.toDouble())
          .toList(),
      rgb = (data['highlight_rgb'] as List)
          .cast<num>()
          .map((v) => v.toDouble())
          .toList();
  final List<double> stops, alpha, rgb;
  static final Future<NativeFontLightProfile> loaded = rootBundle
      .loadString('assets/review/font-light-native.json')
      .then((value) => NativeFontLightProfile(jsonDecode(value)));

  void paint(
    ui.Canvas canvas,
    ui.Size size, {
    required double rowTop,
    required double selectionAlpha,
    required ui.Offset center,
    required double intensity,
    required double reach,
  }) {
    if (selectionAlpha > 0) {
      canvas.drawRRect(
        ui.RRect.fromRectAndRadius(
          ui.Rect.fromLTWH(10, rowTop, size.width - 20, 42),
          const ui.Radius.circular(21),
        ),
        ui.Paint()
          ..color = ui.Color.from(
            alpha: selectionAlpha.clamp(0, 1),
            red: rgb[0],
            green: rgb[1],
            blue: rgb[2],
          ),
      );
    }
    if (intensity <= 0) return;
    final radius = 2 * math.min(160.0, 1.5 * size.shortestSide) * reach;
    canvas.save();
    canvas.clipRRect(
      ui.RRect.fromRectAndRadius(
        ui.Offset.zero & size,
        const ui.Radius.circular(3),
      ),
    );
    canvas.drawCircle(
      center,
      radius,
      ui.Paint()
        ..shader = ui.Gradient.radial(
          center,
          radius,
          [
            for (final value in alpha)
              const ui.Color(
                0xffffffff,
              ).withValues(alpha: (value * intensity).clamp(0, 1)),
          ],
          stops,
          ui.TileMode.clamp,
        ),
    );
    canvas.restore();
  }
}

/// The native pointer handler latches this change after 50 logical pixels.
class NativeFontContact {
  NativeFontContact(this.start);
  final ui.Offset start;
  bool travelled = false;
  void move(ui.Offset point) {
    if ((point - start).distance > 50) travelled = true;
  }

  double get intensity => travelled ? .25 : .5;
  double get reach => travelled ? 2 : 1;
}
