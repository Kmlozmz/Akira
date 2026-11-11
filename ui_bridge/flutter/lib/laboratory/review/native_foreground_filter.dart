import 'dart:ui' as ui;
import 'package:flutter/widgets.dart';

/// Alpha matrix followed by decal blur (AOT ae78cc..ae7b74).
class NativeForegroundFilter extends StatelessWidget {
  const NativeForegroundFilter({
    super.key,
    required this.alpha,
    required this.blur,
    required this.child,
  });
  final double alpha, blur;
  final Widget child;

  @override
  Widget build(BuildContext context) => ImageFiltered(
    enabled: blur > .01 || alpha < 1,
    imageFilter: ui.ImageFilter.compose(
      outer: ui.ImageFilter.blur(
        sigmaX: blur,
        sigmaY: blur,
        tileMode: ui.TileMode.decal,
      ),
      inner: ui.ColorFilter.matrix([
        1,
        0,
        0,
        0,
        0,
        0,
        1,
        0,
        0,
        0,
        0,
        0,
        1,
        0,
        0,
        0,
        0,
        0,
        alpha.clamp(0, 1),
        0,
      ]),
    ),
    child: child,
  );
}
