import 'dart:math' as math;

import 'recovered_spring.dart';

/// Numerical wrapper recovered from AOT 0xf52298; widget attribution is separate.
class RecoveredThresholdSpring {
  RecoveredThresholdSpring({
    required double period,
    required double damping,
    required double position,
    required double target,
    double velocity = 0,
    this.crossedMask = 0,
    this.lastTarget = double.nan,
  }) : kernel = RecoveredSpring(
         period: period,
         damping: damping,
         position: position,
         target: target,
         velocity: velocity,
         epsilon: 0,
       );

  final RecoveredSpring kernel;
  int crossedMask;
  double lastTarget;

  bool step(double seconds, List<double> thresholds) {
    if (thresholds.length > 64) throw RangeError('At most 64 thresholds');
    if (lastTarget != kernel.target) {
      lastTarget = kernel.target;
      crossedMask = 0;
    }
    if (kernel.position == kernel.target && kernel.velocity == 0) return false;
    kernel.step(seconds);
    final displacement = kernel.target - kernel.position;
    final scaledVelocity =
        kernel.damping * kernel.period / math.pi * kernel.velocity;
    final error = math.sqrt(
      displacement * displacement + scaledVelocity * scaledVelocity,
    );
    for (var i = 0; i < thresholds.length; i++) {
      if (thresholds[i] >= error) crossedMask |= 1 << i;
    }
    final requiredMask = thresholds.length == 64
        ? -1
        : (1 << thresholds.length) - 1;
    if ((crossedMask & requiredMask) != requiredMask) return true;
    kernel.position = kernel.target;
    kernel.velocity = 0;
    return false;
  }
}

/// Time partition only; a host must preserve property order and target updates.
Iterable<double> recoveredFrameParts(double seconds) sync* {
  var remaining = math.min(seconds, .25);
  while (remaining > 1e-9) {
    final part = math.min(remaining, 1 / 240);
    remaining -= part;
    yield part;
  }
}
