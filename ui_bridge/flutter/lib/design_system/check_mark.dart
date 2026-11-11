import 'package:flutter/widgets.dart';

/// A vector state mark independent of icon-font coverage.
class AkiraCheckMark extends StatelessWidget {
  const AkiraCheckMark({
    super.key,
    this.size = 20,
    this.color = const Color(0xffffffff),
  });
  final double size;
  final Color color;
  @override
  Widget build(BuildContext context) => SizedBox.square(
    dimension: size,
    child: CustomPaint(painter: _CheckPainter(color)),
  );
}

class _CheckPainter extends CustomPainter {
  const _CheckPainter(this.color);
  final Color color;
  @override
  void paint(Canvas canvas, Size size) {
    final path = Path()
      ..moveTo(size.width * .18, size.height * .51)
      ..lineTo(size.width * .41, size.height * .74)
      ..lineTo(size.width * .83, size.height * .25);
    canvas.drawPath(
      path,
      Paint()
        ..color = color
        ..style = PaintingStyle.stroke
        ..strokeWidth = size.width * .105
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
  }

  @override
  bool shouldRepaint(_CheckPainter old) => old.color != color;
}
