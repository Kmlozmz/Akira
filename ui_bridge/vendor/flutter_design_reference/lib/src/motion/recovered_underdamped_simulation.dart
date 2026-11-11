import 'dart:math' as math;

class RecoveredUnderdampedSimulation {
  const RecoveredUnderdampedSimulation({
    required this.w,
    required this.r,
    required this.c1,
    required this.c2,
    required this.end,
    required this.distanceTolerance,
    required this.velocityTolerance,
    required this.snapToEnd,
  });

  final double w, r, c1, c2, end;
  final double distanceTolerance, velocityTolerance;
  final bool snapToEnd;

  double relativePosition(double time) =>
      math.pow(math.e, r * time).toDouble() *
      (c1 * math.cos(w * time) + c2 * math.sin(w * time));

  double rawVelocity(double time) {
    final decay = math.pow(math.e, r * time).toDouble();
    final cosine = math.cos(w * time);
    final sine = math.sin(w * time);
    return decay * (c2 * w * cosine - c1 * w * sine) +
        r * (decay * (c1 * cosine + c2 * sine));
  }

  bool isDone(double time) =>
      relativePosition(time).abs() < distanceTolerance &&
      rawVelocity(time).abs() < velocityTolerance;

  double position(double time) =>
      snapToEnd && isDone(time) ? end : end + relativePosition(time);

  double velocity(double time) =>
      snapToEnd && isDone(time) ? 0 : rawVelocity(time);
}
