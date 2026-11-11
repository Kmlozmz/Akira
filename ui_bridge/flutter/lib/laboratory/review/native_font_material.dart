import 'dart:math' as math;

/// Numeric light/shadow response verified against 1150 native draws, Sources17-23.
class NativeFontMaterial {
  static double shadowAlpha(
    double rawProgress,
    double foreground,
    double extra,
    bool closing,
  ) {
    final p = rawProgress.clamp(0.0, 1.0);
    final f = foreground.clamp(0.0, 1.0);
    final e = extra.clamp(0.0, 1.0);
    final opacity = ((1 - f) + f * e) * p;
    if (opacity <= 0) return 0;
    final contribution = f * e * p;
    final base = closing
        ? math.max(.012, .1555 - .1995 * (1 - rawProgress))
        : .1555;
    return base * (contribution / opacity);
  }

  static List<double> light(double progress, bool closing) {
    final t = closing ? math.min(1.0, 2.28 * (1 - progress)) : 0.0;
    return [
      math.sin((115 / 24 + 125 / 24 * t) * math.pi / 180),
      0,
      math.cos((5317 / 96 - 325 / 96 * t) * math.pi / 180),
      34 / 3,
    ];
  }

  static double shadowBlur(double progress, bool closing) => closing
      ? math.max(2.0, 10.541666666666666 - 11.875 * (1 - progress))
      : 10.541666666666666;

  static double style(double progress, bool closing) =>
      closing ? (-16 - 57 * progress).clamp(-60.0, -22.0) : -60;
}
