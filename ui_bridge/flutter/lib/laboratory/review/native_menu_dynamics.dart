import 'dart:math' as math;
import 'package:inotes_recovered/inotes_recovered.dart';

/// Native Font actor stepping order (AOT 0xf50700). Action scheduling is separate.
class NativeMenuDynamics {
  NativeMenuDynamics(Map<int, List<double>> initial) {
    for (final field in order) {
      final values = initial[field]!;
      springs[field] = RecoveredThresholdSpring(
        period: .35,
        damping: .75,
        position: values[0],
        target: values[1],
        velocity: values[2],
      );
    }
    final light = initial[0x97]!;
    closeLight = RecoveredSpring(
      period: .35,
      damping: .75,
      position: light[0],
      target: light[1],
      velocity: light[2],
    );
  }

  static const order = [
    0x4b,
    0x4f,
    0x53,
    0x5f,
    0x63,
    0x77,
    0x7f,
    0x87,
    0x8f,
    0x6f,
    0x73,
  ];
  static const thresholds = <int, List<double>>{
    0x4b: [.001, 4.273504273504274e-06],
    0x4f: [1 / 3],
    0x53: [.001],
    0x5f: [.001],
    0x63: [.001],
    0x77: [1 / 3, .006153846153846155],
    0x7f: [1 / 3],
    0x87: [.05],
    0x8f: [.05],
    0x6f: [.01],
    0x73: [.01],
  };

  final springs = <int, RecoveredThresholdSpring>{};
  late final RecoveredSpring closeLight;
  int _sharedMask = 0;
  double _sharedTarget = double.nan;

  RecoveredSpring state(int field) =>
      field == 0x97 ? closeLight : springs[field]!.kernel;

  void retarget(int field, double target, double period, double damping) {
    final spring = state(field);
    spring.target = target;
    spring.period = period;
    spring.damping = damping;
  }

  bool stepField(int field, double seconds) {
    if (field == 0x97) return closeLight.step(seconds);
    final spring = springs[field]!;
    final shared = field != 0x4b && field != 0x77;
    if (shared) {
      spring.crossedMask = _sharedMask;
      spring.lastTarget = _sharedTarget;
    }
    final active = spring.step(seconds, thresholds[field]!);
    if (shared) {
      _sharedMask = spring.crossedMask;
      _sharedTarget = spring.lastTarget;
    }
    return active;
  }

  bool stepFrame(double seconds, {void Function(double)? beforeSubstep}) {
    var active = false;
    for (final part in recoveredFrameParts(seconds)) {
      beforeSubstep?.call(part);
      for (final field in order) {
        active = stepField(field, part) || active;
      }
      active = stepField(0x97, part) || active;
    }
    return active;
  }

  int get mainMask => springs[0x4b]!.crossedMask;
  double get foregroundProgress =>
      mainMask & 1 != 0 ? state(0x4b).target : state(0x4b).position;
  double get geometryProgress =>
      mainMask & 2 != 0 ? state(0x4b).target : state(0x4b).position;
  double get blur => springs[0x77]!.crossedMask & 2 != 0
      ? state(0x77).target
      : state(0x77).position;
  double get foregroundBlur => springs[0x77]!.crossedMask & 1 != 0
      ? state(0x77).target
      : state(0x77).position;

  // AOT f51e70 uses looser geometry thresholds before retiring a closed pane.
  bool geometrySettled({double tolerance = 1 / 3.05}) {
    double error(int field, [double? coefficient]) {
      final s = state(field);
      final y = s.target - s.position;
      final v = s.velocity * (coefficient ?? s.period * s.damping / math.pi);
      return math.sqrt(y * y + v * v);
    }

    return error(0x4b) <= thresholds[0x4b]![1] * tolerance / .001 &&
        error(0x4f) <= tolerance &&
        error(0x53) <= tolerance &&
        error(0x5f) <= tolerance &&
        error(0x63, .12477747538404593) <= tolerance &&
        error(0x77) <= 1 / 3 &&
        error(0x7f) <= tolerance &&
        error(0x87) <= .05 &&
        error(0x8f) <= .05 &&
        error(0x6f, .06366197723675814) <= .01 &&
        error(0x73, .09469719113967773) <= .01 &&
        error(0x97, .12477747538404593) <= .002;
  }
}
