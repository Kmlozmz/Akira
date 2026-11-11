import 'dart:math' as math;

class RecoveredCriticalSimulation {
  const RecoveredCriticalSimulation({
    required this.r,
    required this.c1,
    required this.c2,
    required this.end,
    required this.distanceTolerance,
    required this.velocityTolerance,
    required this.snapToEnd,
  });

  final double r, c1, c2, end;
  final double distanceTolerance, velocityTolerance;
  final bool snapToEnd;

  double relativePosition(double time) =>
      (c1 + c2 * time) * math.pow(math.e, r * time).toDouble();

  double rawVelocity(double time) {
    final exponent = math.pow(math.e, r * time).toDouble();
    return r * (c1 + c2 * time) * exponent + c2 * exponent;
  }

  bool isDone(double time) =>
      relativePosition(time).abs() < distanceTolerance &&
      rawVelocity(time).abs() < velocityTolerance;

  double position(double time) =>
      snapToEnd && isDone(time) ? end : end + relativePosition(time);

  double velocity(double time) =>
      snapToEnd && isDone(time) ? 0 : rawVelocity(time);
}
