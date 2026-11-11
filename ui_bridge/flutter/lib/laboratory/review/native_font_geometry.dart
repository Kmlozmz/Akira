import 'dart:math' as math;
import 'dart:ui';
import 'native_optical_geometry.dart';

class NativeFontGeometry {
  NativeFontGeometry({
    required this.menu,
    required this.anchor,
    required this.progress,
    required this.bodyArc,
    required this.seedArc,
    required this.seedWidth,
    required this.bodyRadius,
    required this.sigma,
    this.anchorOnly = false,
  });
  final Rect menu, anchor;
  final double progress, bodyArc, seedArc, seedWidth, bodyRadius, sigma;
  final bool anchorOnly;

  static double _lerp(double a, double b, double t) => a * (1 - t) + b * t;
  static Offset _center(Rect r) =>
      Offset((r.left + r.right) / 2, (r.top + r.bottom) / 2);
  static Offset _point(Offset a, Offset b, double t) =>
      Offset(_lerp(a.dx, b.dx, t), _lerp(a.dy, b.dy, t));
  static Rect _rect(Offset c, double w, double h) =>
      Rect.fromLTRB(c.dx - w / 2, c.dy - h / 2, c.dx + w / 2, c.dy + h / 2);

  late final bodyScale = math.max(
    0.0,
    _lerp(anchor.height / (2 * math.max(menu.width, menu.height)), 1, progress),
  );
  late final seedScale = math.max(0.0, _lerp(1, .25, progress));

  late final Rect rawBody = _body();
  Rect _body() {
    if (anchorOnly) return anchor;
    final c = _point(_center(anchor), _center(menu), progress);
    return _rect(
      Offset(c.dx, c.dy + bodyArc * bodyScale),
      menu.width * bodyScale,
      math.max(0.0, _lerp(menu.width, menu.height, progress)) * bodyScale,
    );
  }

  late final Rect rawSeed = _seed();
  Rect _seed() {
    if (anchorOnly) return anchor;
    final start = _center(anchor);
    final c = _point(start, _point(start, _center(menu), .25), progress);
    return _rect(
      Offset(c.dx, c.dy + seedArc * seedScale),
      math.max(0.0, seedWidth) * seedScale,
      anchor.height * seedScale,
    );
  }

  late final double inputBodyRadius =
      math.min(
        math.max(bodyRadius, 0.0),
        math.min(menu.width, _lerp(menu.width, menu.height, progress)) / 2,
      ) *
      bodyScale;

  late final NativeOpticalShape? body = NativeOpticalGeometry.convert(
    rawBody,
    inputBodyRadius,
    math.max(sigma, 0.0),
  );
  late final NativeOpticalShape? seed = NativeOpticalGeometry.convert(
    rawSeed,
    0,
    math.max(sigma, 0.0),
  );
}
