import 'package:flutter/widgets.dart';

/// Original shared contact filter: libapp0xd44c4c,0xcc36f4,0xab3340.
class RecoveredPointerExclusions {
  static final shared = RecoveredPointerExclusions();
  final Set<int> _active = <int>{};

  bool contains(int pointer) => _active.contains(pointer);

  void pointerDown(int pointer, {bool? shouldExclude}) {
    if (shouldExclude != false) _active.add(pointer);
  }

  void pointerFinished(int pointer) => _active.remove(pointer);
}

class RecoveredPointerExclusionRegion extends StatelessWidget {
  const RecoveredPointerExclusionRegion({
    super.key,
    required this.child,
    required this.behavior,
    this.exclusions,
    this.shouldExclude,
  });

  final Widget child;
  final HitTestBehavior behavior;
  final RecoveredPointerExclusions? exclusions;
  final bool? Function(PointerDownEvent event)? shouldExclude;

  @override
  Widget build(BuildContext context) {
    final registry = exclusions ?? RecoveredPointerExclusions.shared;
    return Listener(
      behavior: behavior,
      onPointerDown: (event) => registry.pointerDown(
        event.pointer,
        shouldExclude: shouldExclude?.call(event),
      ),
      onPointerUp: (event) => registry.pointerFinished(event.pointer),
      onPointerCancel: (event) => registry.pointerFinished(event.pointer),
      child: child,
    );
  }
}
