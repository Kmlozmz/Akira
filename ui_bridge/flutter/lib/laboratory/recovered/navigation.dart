import 'dart:math' as math;
import 'package:flutter/gestures.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/widgets.dart';
import 'package:inotes_recovered/inotes_recovered.dart';
import 'motion/recovered_back_policy.dart';
import 'motion/recovered_back_recognizer.dart';
import 'motion/recovered_back_velocity.dart';
import 'motion/recovered_underdamped_simulation.dart';

/// Reference integration of the recovered route and recognizer contracts.
/// Native input evidence and host tests are separate from scene pixel parity.
class RecoveredLabRoute<T> extends InotesMotionPageRoute<T> {
  RecoveredLabRoute({required super.builder, this.reduceMotion = false, this.interactivePop = false});
  final bool reduceMotion;
  final bool interactivePop;
  final overscroll = ValueNotifier<double>(0);
  double? _releaseVelocity;
  bool _userGesture = false;
  bool get canStart =>
      !reduceMotion &&
      isCurrent &&
      !isFirst &&
      animation?.status == AnimationStatus.completed &&
      !_userGesture;

  void beginInteractive() {
    _userGesture = true;
    controller!.stop();
    navigator!.didStartUserGesture();
  }

  void applyDrag(double position) {
    final pose = recoveredBackDragPose(position, 1);
    controller!.value = pose.routeProgress;
    overscroll.value = pose.overscroll;
  }

  void finishInteractive(bool exit, double velocity) {
    if (!_userGesture) return;
    if (exit) {
      _releaseVelocity = velocity;
      navigator!.pop();
      controller!.addStatusListener(_finishOnStatus);
    } else {
      controller!
          .animateWith(_UnderSimulation(controller!.value, 1, velocity))
          .whenComplete(_stopUserGesture);
    }
  }

  void _finishOnStatus(AnimationStatus status) {
    if (status == AnimationStatus.dismissed ||
        status == AnimationStatus.completed) {
      controller!.removeStatusListener(_finishOnStatus);
      _stopUserGesture();
    }
  }

  void _stopUserGesture() {
    if (_userGesture) {
      _userGesture = false;
      navigator?.didStopUserGesture();
    }
  }

  @override
  Simulation createSimulation({required bool forward}) {
    if (reduceMotion) return _ImmediateSimulation(forward ? 1 : 0);
    final velocity = _releaseVelocity;
    _releaseVelocity = null;
    return velocity == null
        ? super.createSimulation(forward: forward)
        : _UnderSimulation(controller!.value, forward ? 1 : 0, velocity);
  }

  @override
  Widget buildTransitions(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) => super.buildTransitions(
    context,
    animation,
    secondaryAnimation,
    interactivePop ? _BackGesture(
      route: this,
      child: ValueListenableBuilder<double>(
        valueListenable: overscroll,
        builder: (context, value, child) =>
            FractionalTranslation(translation: Offset(-value, 0), child: child),
        child: child,
      ),
    ) : child,
  );
  @override
  void dispose() {
    _stopUserGesture();
    overscroll.dispose();
    super.dispose();
  }
}

class _ImmediateSimulation extends Simulation {
  _ImmediateSimulation(this.value);
  final double value;
  @override
  double x(double t) => value;
  @override
  double dx(double t) => 0;
  @override
  bool isDone(double t) => true;
}

class _UnderSimulation extends Simulation {
  _UnderSimulation(double start, double end, double velocity)
    : model = RecoveredUnderdampedSimulation(
        w: 11.032910810242633,
        r: -17.802358370342162,
        c1: start - end,
        c2:
            (velocity + 17.802358370342162 * (start - end)) /
            11.032910810242633,
        end: end,
        distanceTolerance: .0005,
        velocityTolerance: .01,
        snapToEnd: true,
      );
  final RecoveredUnderdampedSimulation model;
  @override
  double x(double t) => model.position(t);
  @override
  double dx(double t) => model.velocity(t);
  @override
  bool isDone(double t) => model.isDone(t);
}

class _BackGesture extends StatefulWidget {
  const _BackGesture({required this.route, required this.child});
  final RecoveredLabRoute<dynamic> route;
  final Widget child;
  @override
  State<_BackGesture> createState() => _BackGestureState();
}

class _BackGestureState extends State<_BackGesture>
    with SingleTickerProviderStateMixin {
  late final Ticker ticker = createTicker(_tick);
  final spring = RecoveredSpring(
    period: .08,
    damping: .85,
    position: 0,
    target: 0,
    epsilon: .0005,
  );
  final tracker = RecoveredBackVelocity(
    lastProcessedTime: 0,
    lastSeenTime: 0,
    position: 0,
    primaryVelocity: 0,
    clampedAcceleration: 0,
    averageVelocity: 0,
    averageAcceleration: 0,
    count: 1,
    boost: true,
    shortSampleFlag: false,
    velocityRing: [0, 0, 0],
    accelerationRing: [0, 0, 0],
  );
  VelocityTracker? velocity;
  Duration lastTick = Duration.zero;
  double width = 1, accumulated = 0, raw = 0, activation = 0;
  bool active = false, rejected = false;
  _UnderSimulation? overscrollRelease;
  double _seconds(Duration? stamp) =>
      (stamp ?? Duration.zero).inMicroseconds / 1e6;

  bool _allowed(PointerDownEvent event) {
    final media = MediaQuery.of(context);
    return Directionality.of(context) == TextDirection.ltr &&
        widget.route.canStart &&
        recoveredBackStartAllowed(
          x: event.localPosition.dx,
          width: media.size.width,
          leftInset: media.padding.left,
          rightInset: media.padding.right,
          rtl: false,
        );
  }

  void _start(DragStartDetails details) {
    ticker.stop();
    overscrollRelease = null;
    width = MediaQuery.sizeOf(context).width;
    accumulated = raw = activation = 0;
    active = rejected = false;
    spring.position = spring.target = spring.velocity = 0;
    velocity = VelocityTracker.withKind(details.kind ?? PointerDeviceKind.touch)
      ..addPosition(
        details.sourceTimeStamp ?? Duration.zero,
        details.globalPosition,
      );
    tracker.reset(_seconds(details.sourceTimeStamp), 0, 0);
  }

  void _update(DragUpdateDetails details) {
    if (rejected) return;
    accumulated += details.primaryDelta ?? 0;
    velocity!.addPosition(
      details.sourceTimeStamp ?? Duration.zero,
      details.globalPosition,
    );
    final speed = velocity!.getVelocity().pixelsPerSecond.dx / width;
    if (!active) {
      if (accumulated.abs() < 30) return;
      if (accumulated < 0) {
        rejected = true;
        return;
      }
      active = true;
      activation = accumulated / width;
      widget.route.beginInteractive();
    } else {
      raw += (details.primaryDelta ?? 0) / width;
    }
    tracker.update(_seconds(details.sourceTimeStamp), raw, speed, false);
    spring.target = recoveredBackDragTarget(raw);
    if (!ticker.isActive) {
      lastTick = Duration.zero;
      ticker.start();
    }
  }

  void _tick(Duration elapsed) {
    if (overscrollRelease case final release?) {
      widget.route.overscroll.value = math.max(
        0,
        release.x(elapsed.inMicroseconds / 1e6),
      );
      if (release.isDone(elapsed.inMicroseconds / 1e6)) {
        ticker.stop();
        overscrollRelease = null;
      }
      return;
    }
    final delta = (elapsed - lastTick).inMicroseconds / 1e6;
    lastTick = elapsed;
    final running = spring.step(delta);
    widget.route.applyDrag(spring.position);
    if (!running) ticker.stop();
  }

  void _end(DragEndDetails details) {
    if (!active) return;
    ticker.stop();
    final exit = recoveredBackReleaseDecision(
      sampleCount: tracker.count,
      shortSampleFlag: tracker.shortSampleFlag,
      rawDrag: raw,
      activationOffset: activation,
      velocity: tracker.averageVelocity,
      acceleration: tracker.averageAcceleration,
      width: width,
    );
    final v = recoveredBackReleaseVelocity(
      tracker.primaryVelocity,
      tracker.boost,
    );
    final over = widget.route.overscroll.value;
    if (over > 0) {
      overscrollRelease = _UnderSimulation(over, 0, 0);
      lastTick = Duration.zero;
      ticker.start();
    }
    widget.route.finishInteractive(exit, v);
    active = false;
  }

  void _cancel() {
    ticker.stop();
    if (active) widget.route.finishInteractive(false, 0);
    active = false;
    widget.route.overscroll.value = 0;
  }

  @override
  void dispose() {
    ticker.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => RawGestureDetector(
    behavior: HitTestBehavior.translucent,
    gestures: {
      RecoveredBackGestureRecognizer: GestureRecognizerFactoryWithHandlers<RecoveredBackGestureRecognizer>(
        RecoveredBackGestureRecognizer.new,
        (instance) {
          instance
            ..allowed = _allowed
            ..dragStartBehavior = DragStartBehavior.down
            ..onStart = _start
            ..onUpdate = _update
            ..onEnd = _end
            ..onCancel = _cancel;
        },
      ),
    },
    child: widget.child,
  );
}
