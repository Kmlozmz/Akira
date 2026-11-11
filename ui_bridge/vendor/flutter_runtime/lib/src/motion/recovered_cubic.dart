/// Native midpoint/Y terms are verified; the return follows two recovered adds.
class RecoveredCubic {
  const RecoveredCubic(this.a, this.b, this.c, this.d);

  final double a;
  final double b;
  final double c;
  final double d;

  double transform(double t) {
    if (t.isNaN) throw ArgumentError.value(t, 't');
    if (t <= 0) return 0;
    if (t >= 1) return 1;
    var start = 0.0;
    var end = 1.0;
    while (true) {
      final middle = (start + end) / 2.0;
      final inverse = 1.0 - middle;
      var x = (((a * 3.0) * inverse) * inverse) * middle;
      x += (((c * 3.0) * inverse) * middle) * middle;
      final cube = (middle * middle) * middle;
      x += cube;
      if ((t - x).abs() < 0.001) {
        var y = (((b * 3.0) * inverse) * inverse) * middle;
        y += (((d * 3.0) * inverse) * middle) * middle;
        return y + cube;
      }
      if (x < t) {
        start = middle;
      } else {
        end = middle;
      }
    }
  }
}
