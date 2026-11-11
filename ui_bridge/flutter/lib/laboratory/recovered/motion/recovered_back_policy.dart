import 'dart:math' as math;

double recoveredBackDragTarget(double raw) {
  if (raw < 0) return -((1 - 1 / (1 + ((-raw * .55) / .5))) * .5);
  return math.min(raw, 1);
}

({double routeProgress, double overscroll, double frontX, double backgroundX})
recoveredBackDragPose(double dragPosition, double width) {
  final progress = (1 - dragPosition).clamp(0.0, 1.0).toDouble();
  final overscroll = math.max(-dragPosition, 0.0);
  return (
    routeProgress: progress,
    overscroll: overscroll,
    frontX: width * (1 - progress) - width * overscroll,
    backgroundX: -.3 * width * progress,
  );
}

bool recoveredBackStartAllowed({
  required double x,
  required double width,
  required double leftInset,
  required double rightInset,
  required bool rtl,
}) => rtl
    ? width * .5 <= x && x <= width - rightInset
    : leftInset <= x && x <= width * .5;

bool recoveredBackReleaseDecision({
  required int sampleCount,
  required bool shortSampleFlag,
  required double rawDrag,
  required double activationOffset,
  required double velocity,
  required double acceleration,
  required double width,
}) {
  if (sampleCount < 3) return shortSampleFlag && velocity > 0;
  final total = (rawDrag + activationOffset).clamp(0.0, 1.0).toDouble();
  final projected =
      (total + velocity * .35) + ((acceleration * .5) * .35) * .35;
  return projected > math.min(187.5 / width, .5);
}

double recoveredBackReleaseVelocity(double primary, bool boost) =>
    -(boost ? primary * 4.25 : primary);
