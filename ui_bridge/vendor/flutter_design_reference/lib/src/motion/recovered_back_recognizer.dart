import 'package:flutter/gestures.dart';
import 'recovered_recognizer_policy.dart';
import 'recovered_pointer_exclusions.dart';

/// Original recognizer policy, with host-provided pointer eligibility.
/// Host geometry and menu arbitration remain separate from the contact filter.
class RecoveredBackGestureRecognizer extends HorizontalDragGestureRecognizer {
  RecoveredBackGestureRecognizer() {
    dragStartBehavior = DragStartBehavior.down;
    multitouchDragStrategy = MultitouchDragStrategy.latestPointer;
  }

  bool Function(PointerDownEvent)? allowed;
  RecoveredPointerExclusions exclusions = RecoveredPointerExclusions.shared;
  bool rtl = false;
  final _movement = <int, Offset>{};
  bool _arenaAccepted = false;

  @override
  bool isPointerAllowed(PointerEvent event) =>
      event is PointerDownEvent &&
      !exclusions.contains(event.pointer) &&
      (allowed?.call(event) ?? false) &&
      super.isPointerAllowed(event);

  @override
  void handleEvent(PointerEvent event) {
    if (event is PointerMoveEvent) {
      final delta = (_movement[event.pointer] ?? Offset.zero) + event.delta;
      _movement[event.pointer] = delta;
      if (!_arenaAccepted && recoveredBackCrossAxisRejected(delta.dx, delta.dy)) {
        // Original 0x769414 resolves this pointer before 0x769458 stops routing.
        resolvePointer(event.pointer, GestureDisposition.rejected);
        stopTrackingPointer(event.pointer);
        return;
      }
    }
    super.handleEvent(event);
  }

  @override
  bool hasSufficientGlobalDistanceToAccept(PointerDeviceKind kind, double? slop) =>
      _movement.values.any((delta) =>
          recoveredBackDistanceAccepted(delta.dx, rtl: rtl));

  @override
  void acceptGesture(int pointer) {
    _arenaAccepted = true;
    super.acceptGesture(pointer);
  }

  @override
  void didStopTrackingLastPointer(int pointer) {
    _arenaAccepted = false;
    _movement.clear();
    super.didStopTrackingLastPointer(pointer);
  }
}
