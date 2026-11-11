import '../../design_system/component_specs.dart';
import 'dart:math' as math;
import 'dart:ui';
import 'package:inotes_recovered/inotes_recovered.dart';

class NativeContactTuning {
  const NativeContactTuning({
    this.contactPeriod = AkiraContactSpec.heldPeriod,
    this.contactDamping = AkiraContactSpec.heldDamping,
    this.releasePeriod = AkiraContactSpec.releasePeriod,
    this.releaseDamping = AkiraContactSpec.releaseDamping,
    this.margin = AkiraContactSpec.buttonMargin,
    this.resistance = AkiraContactSpec.buttonResistance,
    this.maximumExtent = false,
  });
  const NativeContactTuning.pane()
    : contactPeriod = AkiraContactSpec.paneHeldPeriod,
      contactDamping = AkiraContactSpec.heldDamping,
      releasePeriod = AkiraContactSpec.paneReleasePeriod,
      releaseDamping = AkiraContactSpec.paneReleaseDamping,
      margin = AkiraContactSpec.paneMargin,
      resistance = AkiraContactSpec.paneResistance,
      maximumExtent = true;
  final double contactPeriod, contactDamping, releasePeriod, releaseDamping;
  final double margin, resistance;
  final bool maximumExtent;
}

/// Four-axis contact transform observed at f3c6e8 in optics-controls5.
class NativeContactDeformation {
  NativeContactDeformation({this.tuning = const NativeContactTuning()})
    : x = _axis(1),
      y = _axis(1),
      dx = _axis(0),
      dy = _axis(0);

  final RecoveredSpring x, y, dx, dy;
  final NativeContactTuning tuning;
  static RecoveredSpring _axis(double value) => RecoveredSpring(
    period: .262,
    damping: .625,
    position: value,
    target: value,
    epsilon: .001,
  );
  Iterable<RecoveredSpring> get axes => [x, y, dx, dy];
  bool get isSettled =>
      axes.every((s) => s.position == s.target && s.velocity == 0);

  void updateTargets({
    required Size size,
    required bool held,
    required Offset unbounded,
    required Offset bounded,
    double? margin,
    double? resistance,
  }) {
    margin ??= tuning.margin;
    resistance ??= tuning.resistance;
    final extent = tuning.maximumExtent
        ? math.max(size.width.abs(), size.height.abs())
        : math.min(size.width.abs(), size.height.abs());
    final base = held ? (extent + margin) / extent : 1.0;
    double pull(double a, double b) => a == 0
        ? 0
        : b.abs() / (4 * resistance!) + (a.abs() - b.abs()) / resistance;
    final gx = held ? pull(unbounded.dx, bounded.dx) : 0.0;
    final gy = held ? pull(unbounded.dy, bounded.dy) : 0.0;
    x.target = base + gx - gy;
    y.target = base - gx + gy;
    dx.target = size.width * gx * unbounded.dx.sign;
    dy.target = size.height * gy * unbounded.dy.sign;
    for (final s in axes) {
      s.period = held ? tuning.contactPeriod : tuning.releasePeriod;
      s.damping = held ? tuning.contactDamping : tuning.releaseDamping;
    }
  }

  void step(double seconds, {bool immediate = false}) {
    for (final s in axes) {
      if (immediate) {
        s.position = s.target;
        s.velocity = 0;
      } else {
        s.step(seconds);
      }
    }
  }
}
