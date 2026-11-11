import 'dart:math' as math;

/// Original libapp.so: 0xcc56f0, 0xdc9bd0 and 0x7693ac–0x7693e4.
/// Native event checks: reference/native/gesturepolicy2/.
double recoveredBackCrossAxisLimit(double cumulativeDx) =>
    math.min(25, 10 + cumulativeDx.abs() * .8);

bool recoveredBackDistanceAccepted(double cumulativeDx, {bool rtl = false}) =>
    cumulativeDx.abs() >= 30 && (rtl ? cumulativeDx < 0 : cumulativeDx > 0);

bool recoveredBackCrossAxisRejected(double cumulativeDx, double cumulativeDy) =>
    cumulativeDy.abs() >= recoveredBackCrossAxisLimit(cumulativeDx);
