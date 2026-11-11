import '../../design_system/component_specs.dart';
import 'dart:math' as math;
import 'package:inotes_recovered/inotes_recovered.dart';

class NativeDeformationParameters {
  const NativeDeformationParameters({
    required this.period,
    required this.damping,
    required this.accelerationScale,
    required this.extent,
    required this.minimum,
    required this.maximum,
  });

  // Original slider parameters at f39764/f39798/f397e0, exp-lens3.jsonl.
  static const held = NativeDeformationParameters(
    period: AkiraThumbMotionSpec.heldPeriod,
    damping: AkiraThumbMotionSpec.heldDamping,
    accelerationScale: AkiraThumbMotionSpec.heldAccelerationScale,
    extent: AkiraThumbMotionSpec.heldExtent,
    minimum: AkiraThumbMotionSpec.heldMinimum,
    maximum: AkiraThumbMotionSpec.heldMaximum,
  );
  static const switchHeld = NativeDeformationParameters(
    period: AkiraThumbMotionSpec.switchPeriod,
    damping: AkiraThumbMotionSpec.switchDamping,
    accelerationScale: AkiraThumbMotionSpec.switchAccelerationScale,
    extent: AkiraThumbMotionSpec.switchExtent,
    minimum: AkiraThumbMotionSpec.switchMinimum,
    maximum: AkiraThumbMotionSpec.switchMaximum,
  );
  static const released = NativeDeformationParameters(
    period: AkiraThumbMotionSpec.releasePeriod,
    damping: AkiraThumbMotionSpec.releaseDamping,
    accelerationScale: AkiraThumbMotionSpec.releaseAccelerationScale,
    extent: AkiraThumbMotionSpec.releaseExtent,
    minimum: AkiraThumbMotionSpec.releaseMinimum,
    maximum: AkiraThumbMotionSpec.releaseMaximum,
  );

  final double period, damping, accelerationScale, extent, minimum, maximum;
}

/// Horizontal specialization of the observed f3a1f8/f39848/f3a004 pipeline.
class NativeGlassDeformation {
  final x = RecoveredSpring(
    period: .444,
    damping: .56,
    position: 1,
    target: 1,
    epsilon: .001,
  );
  final y = RecoveredSpring(
    period: .444,
    damping: .56,
    position: 1,
    target: 1,
    epsilon: .001,
  );
  final offset = RecoveredSpring(
    period: .444,
    damping: .56,
    position: 0,
    target: 0,
    epsilon: .001,
  );

  double? historyTime;
  double historyPosition = 0, historyVelocity = 0, historyAcceleration = 0;

  bool get isSettled =>
      [x, y, offset].every((s) => s.position == s.target && s.velocity == 0);

  void reset(double position) {
    historyTime = null;
    historyPosition = position;
    historyVelocity = historyAcceleration = 0;
    for (final s in [x, y, offset]) {
      s.position = s.target = identical(s, offset) ? 0 : 1;
      s.velocity = 0;
    }
  }

  void updateTargets({
    required double time,
    required double position,
    required double width,
    required double height,
    required NativeDeformationParameters parameters,
  }) {
    final previous = historyTime;
    if (previous == null || time - previous > .05) {
      historyPosition = position;
      historyVelocity = historyAcceleration = 0;
      historyTime = time;
    } else if (time - previous >= .0001) {
      final dt = time - previous;
      final next = historyPosition + (position - historyPosition) * .3;
      final rawVelocity = (next - historyPosition) / dt;
      final velocity = historyVelocity + (rawVelocity - historyVelocity) * .3;
      final rawAcceleration = (velocity.abs() - historyVelocity.abs()) / dt;
      historyAcceleration += (rawAcceleration - historyAcceleration) * .3;
      historyVelocity = velocity;
      historyPosition = next;
      historyTime = time;
    }
    final lowerX = math.max(parameters.minimum, 1 - parameters.extent / width);
    final upperX = math.min(parameters.maximum, 1 + parameters.extent / width);
    final lowerY = math.max(parameters.minimum, 1 - parameters.extent / height);
    final upperY = math.min(parameters.maximum, 1 + parameters.extent / height);
    final acceleration = historyAcceleration / parameters.accelerationScale;
    x.target = _softLimit(1 + acceleration * (upperX - 1), lowerX, upperX);
    y.target = _softLimit(1 + acceleration * (lowerY - 1), lowerY, upperY);
    offset.target =
        (1 - x.target) * .5 * width * (historyVelocity.isNegative ? -1 : 1);
    for (final s in [x, y, offset]) {
      s.period = parameters.period;
      s.damping = parameters.damping;
    }
  }

  void step(double seconds) {
    for (final s in [x, y, offset]) {
      s.step(seconds);
    }
  }

  static double _tanh(double value) {
    if (value > 20) return 1;
    if (value < -20) return -1;
    final e = math.exp(2 * value);
    return (e - 1) / (e + 1);
  }

  static double _softLimit(double value, double lower, double upper) {
    final band = (upper - lower) / 3;
    if (band <= 0) return value;
    if (value < lower) {
      return lower -
          band +
          band * (_tanh((value - (lower - band)) * .55 / band - .55) + 1);
    }
    if (value > upper) {
      return upper + band * _tanh((value - upper) * .55 / band);
    }
    return value;
  }
}
