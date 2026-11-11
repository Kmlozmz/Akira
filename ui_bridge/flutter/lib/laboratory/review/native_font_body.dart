import 'package:flutter/widgets.dart';
import 'package:flutter/rendering.dart' show OverflowBoxFit;
import 'native_foreground_filter.dart';

/// BODY foreground structure recovered at AOT ae4444..ae46c4.
class NativeFontBody extends StatelessWidget {
  const NativeFontBody({
    super.key,
    required this.menuSize,
    required this.bodyRect,
    required this.contentHeight,
    required this.radius,
    required this.alpha,
    required this.blur,
    required this.child,
  });
  final Size menuSize;
  final Rect bodyRect;
  final double contentHeight, radius, alpha, blur;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final content = SizedBox.fromSize(
      size: menuSize,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Positioned.fromRect(
            rect: bodyRect,
            child: FittedBox(
              fit: BoxFit.fill,
              alignment: Alignment.center,
              child: SizedBox(
                width: menuSize.width,
                height: contentHeight,
                child: ClipRSuperellipse(
                  borderRadius: BorderRadius.circular(radius),
                  clipBehavior: Clip.antiAlias,
                  child: OverflowBox(
                    alignment: Alignment.topCenter,
                    minWidth: menuSize.width,
                    maxWidth: menuSize.width,
                    minHeight: menuSize.height,
                    maxHeight: menuSize.height,
                    fit: OverflowBoxFit.max,
                    child: child,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
    return NativeForegroundFilter(alpha: alpha, blur: blur, child: content);
  }
}
