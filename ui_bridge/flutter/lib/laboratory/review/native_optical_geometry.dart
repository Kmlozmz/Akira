import 'dart:math' as math;
import 'dart:ui';

class NativeOpticalShape {
  const NativeOpticalShape(this.rect, this.radius);
  final Rect rect;
  final double radius;

  List<double> get uniform => [
    rect.left + (rect.right - rect.left) / 2,
    rect.top + (rect.bottom - rect.top) / 2,
    (rect.right - rect.left) / 2,
    (rect.bottom - rect.top) / 2,
  ];
}

/// Native 0x72ba3c conversion, with Source22 quadrature payloads.
class NativeOpticalGeometry {
  static const nodes = [
    -2.27950708050106,
    -1.597682635152605,
    -.947788391240164,
    -.314240376254359,
    .314240376254359,
    .947788391240164,
    1.597682635152605,
    2.27950708050106,
  ];
  static const weights = [
    .003905390584629,
    .05160798561588,
    .2604923102642,
    .5701352362625,
    .5701352362625,
    .2604923102642,
    .05160798561588,
    .003905390584629,
  ];

  static NativeOpticalShape? convert(Rect rect, double radius, double sigma) {
    final hx = (rect.right - rect.left) / 2;
    final hy = (rect.bottom - rect.top) / 2;
    if (hx <= 0 || hy <= 0) return null;
    radius = math.min(radius, math.min(hx, hy));
    if (sigma <= 0) return NativeOpticalShape(rect, radius);
    if ((2 * hx - 2 * hy).abs() <= .5 && radius >= math.min(hx, hy) - .5) {
      final half = math.min(hx, hy);
      final squared = half * half - (sigma * (math.pi / 2)) * sigma;
      if (squared <= 0) return null;
      final r = math.sqrt(squared);
      final cx = rect.left + (rect.right - rect.left) / 2;
      final cy = rect.top + (rect.bottom - rect.top) / 2;
      return NativeOpticalShape(
        Rect.fromLTRB(cx - r, cy - r, cx + r, cy + r),
        r,
      );
    }
    final diagonal = math.sqrt(hx * hx + hy * hy);
    final rx = _root(hx, hy, radius, sigma, 1, 0, diagonal);
    final ry = _root(hx, hy, radius, sigma, 0, 1, diagonal);
    final rd = math.max(
      0.0,
      _root(hx, hy, radius, sigma, hx / diagonal, hy / diagonal, diagonal),
    );
    final ix = rx < 0 ? hx : math.max(0.0, hx - rx);
    final iy = ry < 0 ? hy : math.max(0.0, hy - ry);
    if (ix >= hx || iy >= hy) return null;
    final ax = math.max(0.0, (hx - ix) - rd * hx / diagonal);
    final ay = math.max(0.0, (hy - iy) - rd * hy / diagonal);
    final corner = ax + ay + math.sqrt((2 * ax) * ay);
    final output = Rect.fromLTRB(
      rect.left + ix,
      rect.top + iy,
      rect.right - ix,
      rect.bottom - iy,
    );
    return NativeOpticalShape(
      output,
      math.min(corner, math.min(output.width / 2, output.height / 2)),
    );
  }

  static double _root(
    double hx,
    double hy,
    double radius,
    double sigma,
    double dx,
    double dy,
    double diagonal,
  ) {
    if (_distance(0, hx, hy, radius, sigma, dx, dy) >= 0) return -1;
    var lo = 0.0, hi = diagonal + sigma * 4;
    for (var step = 0; step < 64; step++) {
      if (hi - lo <= .01) return (lo + hi) / 2;
      final middle = (lo + hi) / 2;
      if (_distance(middle, hx, hy, radius, sigma, dx, dy) < 0) {
        lo = middle;
      } else {
        hi = middle;
      }
    }
    throw StateError('Native optical bisection did not converge');
  }

  static double _distance(
    double t,
    double hx,
    double hy,
    double radius,
    double sigma,
    double dx,
    double dy,
  ) {
    final support = 1 + 2.8 * sigma;
    var total = 0.0, normalizer = 0.0;
    for (var i = 0; i < 8; i++) {
      final x = t * dx + (nodes[i] * math.sqrt2) * sigma;
      if (x.abs() > hx + support) continue;
      for (var j = 0; j < 8; j++) {
        final y = t * dy + (nodes[j] * math.sqrt2) * sigma;
        if (y.abs() > hy + support) continue;
        final weight = weights[i] * weights[j];
        final qx = x.abs() - hx + radius;
        final qy = y.abs() - hy + radius;
        final px = math.max(qx, 0.0), py = math.max(qy, 0.0);
        final distance =
            math.min(math.max(qx, qy), 0.0) +
            math.sqrt(px * px + py * py) -
            radius;
        total += weight * distance;
        normalizer += weight;
      }
    }
    return normalizer <= 0 ? 1000 : total / normalizer;
  }
}
