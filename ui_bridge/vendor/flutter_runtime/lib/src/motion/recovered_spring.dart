import 'dart:math' as math;

/// Analytic state update recovered at iNotes AOT 0x8599f4.
/// Parameter names describe the equations; original Dart names are unknown.
class RecoveredSpring {
  RecoveredSpring({
    required this.period,
    required this.damping,
    required this.position,
    required this.target,
    this.velocity = 0,
    this.epsilon = .0001,
  });

  double period;
  double damping;
  final double epsilon;
  double position;
  double target;
  double velocity;

  bool step(double seconds) {
    if (position == target && velocity == 0) return false;
    final dt = math.min(seconds, .25);
    final omega = 2 * math.pi / period;
    final displacement = position - target;
    late double y;
    late double v;
    if (damping < 1) {
      final wd = omega * math.sqrt(1 - damping * damping);
      final decay = math.exp(-damping * omega * dt);
      final c = math.cos(wd * dt);
      final s = math.sin(wd * dt);
      final k = (velocity + damping * omega * displacement) / wd;
      y = decay * (displacement * c + k * s);
      v =
          decay *
          (velocity * c - (damping * omega * k + displacement * wd) * s);
    } else if (damping == 1) {
      final decay = math.exp(-omega * dt);
      final k = velocity + omega * displacement;
      y = decay * (displacement + k * dt);
      v = decay * (k - omega * (displacement + k * dt));
    } else {
      final h = omega * math.sqrt(damping * damping - 1);
      final r1 = -damping * omega + h;
      final r2 = -damping * omega - h;
      final c2 = (velocity - r1 * displacement) / (r2 - r1);
      final c1 = displacement - c2;
      y = c1 * math.exp(r1 * dt) + c2 * math.exp(r2 * dt);
      v = r1 * c1 * math.exp(r1 * dt) + r2 * c2 * math.exp(r2 * dt);
    }
    position = target + y;
    velocity = v;
    if (y.abs() < epsilon && v.abs() < 10 * epsilon) {
      position = target;
      velocity = 0;
      return false;
    }
    return true;
  }
}
