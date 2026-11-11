import 'package:flutter/widgets.dart';

import 'recovered_critical_simulation.dart';

class InotesRouteSimulation extends Simulation {
  InotesRouteSimulation({required double start, required double end})
    : recovered = RecoveredCriticalSimulation(
        r: -20.943951023931955,
        c1: start - end,
        c2: 20.943951023931955 * (start - end),
        end: end,
        distanceTolerance: 0.0005,
        velocityTolerance: 0.01,
        snapToEnd: true,
      ),
      super(
        tolerance: const Tolerance(
          distance: 0.0005,
          time: 0.001,
          velocity: 0.01,
        ),
      );

  final RecoveredCriticalSimulation recovered;

  @override
  double x(double time) => recovered.position(time);
  @override
  double dx(double time) => recovered.velocity(time);
  @override
  bool isDone(double time) => recovered.isDone(time);
}

class InotesSlideTransitions extends StatelessWidget {
  const InotesSlideTransitions({
    super.key,
    required this.primaryAnimation,
    required this.secondaryAnimation,
    required this.child,
  });

  final Animation<double> primaryAnimation;
  final Animation<double> secondaryAnimation;
  final Widget child;

  @override
  Widget build(BuildContext context) => SlideTransition(
    position: secondaryAnimation.drive(
      Tween<Offset>(begin: Offset.zero, end: const Offset(-0.3, 0)),
    ),
    child: SlideTransition(
      position: primaryAnimation.drive(
        Tween<Offset>(begin: const Offset(1, 0), end: Offset.zero),
      ),
      child: child,
    ),
  );
}

/// Motion adapter for the observed Android LTR push/pop cases.
/// Page surfaces, edge decoration, dialogs and other gestures are separate.
class InotesMotionPageRoute<T> extends PageRoute<T> {
  InotesMotionPageRoute({required this.builder, super.settings});

  final WidgetBuilder builder;

  // Flutter ignores this metadata when createSimulation returns a Simulation.
  @override
  Duration get transitionDuration => Duration.zero;
  @override
  bool get opaque => true;
  @override
  bool get maintainState => true;
  @override
  Color? get barrierColor => null;
  @override
  String? get barrierLabel => null;

  @override
  Simulation createSimulation({required bool forward}) =>
      InotesRouteSimulation(start: controller!.value, end: forward ? 1 : 0);

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) => builder(context);

  @override
  Widget buildTransitions(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) => InotesSlideTransitions(
    primaryAnimation: animation,
    secondaryAnimation: secondaryAnimation,
    child: child,
  );
}
