import '../../design_system/component_specs.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/scheduler.dart';
import 'package:inotes_recovered/inotes_recovered.dart';

enum GlassResponse {
  contact,
  release,
  tracking,
  sliderTracking,
  switchTracking,
  switchRelease,
  selection,
}

/// Retargets the recovered integrator without restarting its frame clock.
/// Contact/release parameters are observed slider states in resortes-runtime.
/// Tracking/selection attribution is verified separately against direct replays.
class GlassMotionController extends ChangeNotifier {
  GlassMotionController.unbounded({
    required TickerProvider vsync,
    double value = 0,
    double epsilon = .0001,
  }) : _spring = RecoveredSpring(
         period: .1,
         damping: 1,
         position: value,
         target: value,
         epsilon: epsilon,
       ) {
    _ticker = vsync.createTicker(_tick);
  }
  final RecoveredSpring _spring;
  late final Ticker _ticker;
  Duration _previous = Duration.zero;
  double get value => _spring.position;
  set value(double next) {
    _ticker.stop();
    _spring.position = _spring.target = next;
    _spring.velocity = 0;
    notifyListeners();
  }

  double get velocity => _spring.velocity;
  bool get isAnimating => _ticker.isActive;
  void drive(double target, GlassResponse response, bool immediate) {
    final (period, damping) = switch (response) {
      GlassResponse.contact => (AkiraControlMotionSpec.contactPeriod, AkiraControlMotionSpec.contactDamping),
      GlassResponse.release => (AkiraControlMotionSpec.releasePeriod, AkiraControlMotionSpec.releaseDamping),
      GlassResponse.tracking => (AkiraControlMotionSpec.trackingPeriod, AkiraControlMotionSpec.trackingDamping),
      GlassResponse.sliderTracking => (AkiraControlMotionSpec.sliderTrackingPeriod, AkiraControlMotionSpec.sliderTrackingDamping),
      GlassResponse.switchTracking => (AkiraControlMotionSpec.switchTrackingPeriod, AkiraControlMotionSpec.switchTrackingDamping),
      GlassResponse.switchRelease => (AkiraControlMotionSpec.switchReleasePeriod, AkiraControlMotionSpec.switchReleaseDamping),
      GlassResponse.selection => (AkiraControlMotionSpec.selectionPeriod, AkiraControlMotionSpec.selectionDamping),
    };
    driveSpring(target, period: period, damping: damping, immediate: immediate);
  }

  void driveSpring(
    double target, {
    required double period,
    required double damping,
    bool immediate = false,
  }) {
    if (immediate) {
      value = target;
      return;
    }
    _spring.period = period;
    _spring.damping = damping;
    _spring.target = target;
    if (!_ticker.isActive && (value != target || velocity != 0)) {
      _previous = Duration.zero;
      _ticker.start();
    }
  }

  void _tick(Duration elapsed) {
    final seconds = (elapsed - _previous).inMicroseconds / 1e6;
    _previous = elapsed;
    if (seconds <= 0) return;
    final active = _spring.step(seconds);
    if (!active) _ticker.stop();
    notifyListeners();
  }

  @override
  void dispose() {
    _ticker.dispose();
    super.dispose();
  }
}

void retargetGlass(
  GlassMotionController controller,
  double target, {
  GlassResponse response = GlassResponse.tracking,
  bool reduceMotion = false,
}) => controller.drive(target, response, reduceMotion);

double rubberGlass(double distance, double limit) =>
    limit * distance / (limit + distance.abs());
