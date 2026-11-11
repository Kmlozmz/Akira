import 'package:flutter/widgets.dart';

import '../laboratory/recovered/components.dart';
import '../laboratory/review/interactive_controls.dart'
    show AkiraContactResponse;
import 'component_specs.dart';

class AkiraBackButton extends StatelessWidget {
  const AkiraBackButton({
    super.key,
    required this.onPressed,
    required this.color,
    this.label = 'Back',
    this.glass = true,
    this.surface = const Color(0xff1c1c1e),
  });
  final VoidCallback onPressed;
  final Color color;
  final String label;
  final bool glass;
  final Color surface;
  @override
  Widget build(BuildContext context) => AkiraContactResponse(
    child: glass
        ? NativeGlassButton(
            label: label,
            onPressed: onPressed,
            size: const Size(
              AkiraNavigationSpec.face,
              AkiraNavigationSpec.face,
            ),
            child: NativePathIcon('chevron_left', color: color),
          )
        : Semantics(
            label: label,
            button: true,
            child: GestureDetector(
              onTap: onPressed,
              behavior: HitTestBehavior.opaque,
              child: SizedBox.square(
                dimension: AkiraNavigationSpec.face,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: surface,
                    shape: BoxShape.circle,
                  ),
                  child: Center(
                    child: NativePathIcon('chevron_left', color: color),
                  ),
                ),
              ),
            ),
          ),
  );
}
