import 'dart:async';
import 'package:flutter/gestures.dart';
import 'package:flutter/widgets.dart';

/// Observes the held contact without taking the page's scrolling gesture.
class FontMenuTrigger extends StatefulWidget {
  const FontMenuTrigger({
    super.key,
    required this.onOpen,
    required this.child,
    this.acceptsPointer,
  });
  final ValueChanged<int?> onOpen;
  final Widget child;
  final bool Function(Offset)? acceptsPointer;

  @override
  State<FontMenuTrigger> createState() => _FontMenuTriggerState();
}

class _FontMenuTriggerState extends State<FontMenuTrigger> {
  Timer? _timer;
  int? _pointer;
  Offset _down = Offset.zero;
  bool _opened = false;
  bool _accepted = false;

  void _start(PointerDownEvent event) {
    if (_pointer != null) return;
    _accepted = widget.acceptsPointer?.call(event.position) ?? true;
    if (!_accepted) return;
    _pointer = event.pointer;
    _down = event.position;
    _opened = false;
    _timer?.cancel();
    // Native warm holds first paint at 318–322 ms, including dispatch/frame time.
    _timer = Timer(const Duration(milliseconds: 300), () {
      _opened = true;
      widget.onOpen(_pointer);
    });
  }

  void _move(PointerMoveEvent event) {
    if (event.pointer == _pointer &&
        !_opened &&
        (event.position - _down).distance > kTouchSlop) {
      _timer?.cancel();
    }
  }

  void _end(PointerEvent event) {
    if (event.pointer != _pointer) return;
    _timer?.cancel();
    _pointer = null;
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Listener(
    onPointerDown: _start,
    onPointerMove: _move,
    onPointerUp: _end,
    onPointerCancel: _end,
    child: GestureDetector(
      behavior: HitTestBehavior.opaque,
      excludeFromSemantics: true,
      onTap: () {
        if (_accepted && !_opened) widget.onOpen(null);
      },
      child: Semantics(
        button: true,
        onTap: () => widget.onOpen(null),
        child: widget.child,
      ),
    ),
  );
}
