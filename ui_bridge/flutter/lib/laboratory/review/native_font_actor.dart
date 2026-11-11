import 'dart:math' as math;
import 'dart:ui';
import 'native_menu_dynamics.dart';
import 'native_font_geometry.dart';

/// Font action targets and delayed writes recovered at 8a11f4 / 8a759c.
class NativeFontActor {
  NativeFontActor({
    required this.menu,
    required this.anchor,
    this.pixelRatio = 3.05,
  }) : dynamics = NativeMenuDynamics({
         0x4b: [0, 0, 0],
         0x4f: [menu.width / 2, menu.width / 2, 0],
         0x53: [anchor.width, anchor.width, 0],
         0x5f: [0, 0, 0],
         0x63: [0, 0, 0],
         0x6f: [0, 0, 0],
         0x73: [1, 1, 0],
         0x77: [0, 0, 0],
         0x7f: [0, 0, 0],
         0x87: [0, 0, 0],
         0x8f: [20, 20, 0],
         0x97: [0, 0, 0],
       });

  final Rect menu, anchor;
  final double pixelRatio;
  final NativeMenuDynamics dynamics;
  final _queue = <(double, void Function())>[];
  double elapsed = 0;
  bool closing = false, collapsed = false;
  int generation = 0;

  double value(int field) => dynamics.state(field).position;
  double get _arc => 80 * ((menu.height - 60) / 140).clamp(.5, 1);
  static const _open = .35;
  static const _close = .4899999999999999;
  void _drive(int field, double target, double period, double damping) =>
      dynamics.retarget(field, target, period, damping);
  void _later(double delay, void Function() action) =>
      _queue.add((elapsed + delay, action));

  void open() {
    generation++;
    closing = collapsed = false;
    _drive(0x4b, 1, _open, .75);
    _drive(0x4f, menu.width, _open, .75);
    _later(.06999999999999999, () => _drive(0x4f, 32, _open, .75));
    _drive(0x53, anchor.width, .21, 1);
    _drive(0x5f, -_arc, .13999999999999999, 1);
    _later(.06999999999999999, () => _drive(0x5f, 0, _open, .75));
    _drive(0x6f, 1, .2, 1);
    _drive(0x73, 1, .35, .85);
    _drive(0x77, 4, .21, 1);
    _later(.08399999999999999, () => _drive(0x77, 0, .315, .8));
    _drive(0x7f, 20, .06999999999999999, 1);
    _later(.21, () => _drive(0x7f, 0, .42, 1));
    final warp = math.min(menu.width, menu.height) * .3;
    for (final field in [0x87, 0x8f]) {
      _drive(field, warp, _open, .75);
    }
    _later(.08399999999999999, () {
      for (final field in [0x87, 0x8f]) {
        _drive(field, 0, .42, 1);
      }
    });
  }

  void close() {
    if (closing) return;
    generation++;
    closing = true;
    elapsed = 0;
    _queue.clear();
    _drive(0x4b, 0, _close, .8);
    _drive(0x4f, menu.width / 2, _close, .8);
    _drive(0x53, anchor.width, _close, .8);
    _drive(0x5f, -_arc, .13999999999999999, 1);
    _later(.06999999999999999, () => _drive(0x5f, 0, _close, .8));
    _drive(0x63, _arc / 2, _close, .8);
    _later(.105, () => _drive(0x63, 0, _close, .8));
    _drive(0x6f, 0, .2, 1);
    _drive(0x73, 0, .35, .85);
    _drive(0x77, 4, .21, 1);
    _later(.06999999999999999, () => _drive(0x77, 0, .315, .8));
    _drive(0x7f, 20, .06999999999999999, 1);
    _later(.06999999999999999, () => _drive(0x7f, 0, .42, 1));
    for (final field in [0x87, 0x8f]) {
      _drive(field, math.min(anchor.width, anchor.height) * .3, _close, .8);
    }
    _later(.06999999999999999, () {
      for (final field in [0x87, 0x8f]) {
        _drive(field, 0, .27999999999999997, 1);
      }
    });
    _drive(0x97, 1, _close, .8);
  }

  bool step(double seconds) {
    var active = dynamics.stepFrame(seconds, beforeSubstep: advanceTime);
    active = active || _queue.isNotEmpty;
    if (closing &&
        !collapsed &&
        _queue.isEmpty &&
        dynamics.geometrySettled(tolerance: 1 / pixelRatio)) {
      collapsed = true;
    }
    return active;
  }

  void advanceTime(double part) {
    elapsed += part;
    // Keep insertion order: open may add callbacks to an unfinished close.
    for (var i = 0; i < _queue.length;) {
      if (_queue[i].$1 <= elapsed) {
        _queue.removeAt(i).$2();
      } else {
        i++;
      }
    }
  }

  NativeFontGeometry get geometry => NativeFontGeometry(
    menu: menu,
    anchor: anchor,
    progress: dynamics.geometryProgress,
    bodyArc: value(0x5f),
    seedArc: value(0x63),
    seedWidth: value(0x53),
    bodyRadius: value(0x4f),
    sigma: value(0x7f),
    anchorOnly: collapsed,
  );
}
