import 'dart:math' as math;
import 'native_font_actor.dart';
import 'native_pane_motion.dart';
import 'native_font_material.dart';

class NativeFontPose {
  static double _clamp(double v) => v.clamp(0.0, 1.0);
  static Map<String, dynamic> build(
    NativeFontActor actor,
    NativePaneMotion material,
  ) {
    final geometry = actor.geometry;
    final p = _clamp(actor.value(0x4b));
    final effective = _clamp(actor.dynamics.foregroundProgress);
    final fg = _clamp(actor.value(0x6f));
    final extra = _clamp(actor.value(0x73));
    final alpha = _clamp(((1 - fg) + fg * extra) * p);
    final body = geometry.body, seed = geometry.seed;
    final shapes = [if (body != null) body, if (seed != null) seed];
    final uniforms = [
      for (final shape in shapes) shape.uniform,
      for (var i = shapes.length; i < 8; i++) <double>[0, 0, 0, 0],
    ];
    final radii = [
      if (body != null) -body.radius,
      if (seed != null) seed.radius,
      for (var i = shapes.length; i < 8; i++) 0.0,
    ];
    final sigma = math.max(actor.value(0x7f), 0.0);
    final union = sigma <= 0 ? 0.0 : -2 * sigma;
    final lightUniform = NativeFontMaterial.light(
      actor.value(0x4b),
      actor.closing,
    );
    final base = material.fontMaterial;
    final raw = geometry.rawBody;
    final b = actor.dynamics.foregroundBlur;
    final contentHeight = math.max(
      0.0,
      actor.menu.width * (1 - actor.dynamics.geometryProgress) +
          actor.menu.height * actor.dynamics.geometryProgress,
    );
    final tr = base['uTr'] as List;
    final needsMorph =
        actor.value(0x87) != 0 ||
        actor.value(0x8f) != 0 ||
        sigma != 0 ||
        (p != 0 && p != 1);
    return {
      'lean': {
        'uP': uniforms,
        'uRad': radii,
        'uShape': [shapes.length, union, alpha, 1 / actor.pixelRatio],
        'uLight': lightUniform,
        'uTr': [
          tr[0],
          tr[1],
          tr[2],
          NativeFontMaterial.style(actor.value(0x4b), actor.closing),
        ],
        'uShadow': [
          NativeFontMaterial.shadowAlpha(
            actor.value(0x4b),
            actor.value(0x6f),
            actor.value(0x73),
            actor.closing,
          ),
          NativeFontMaterial.shadowBlur(actor.value(0x4b), actor.closing),
          8,
          0,
        ],
        'uBlurP': base['uBlurP'],
      },
      'morph': needsMorph
          ? {
              'uP': uniforms,
              'uRad': radii,
              'uShape': [shapes.length, union, 1, 1 / actor.pixelRatio],
              'uWarp': [
                -math.max(actor.value(0x87), 0.0),
                actor.value(0x8f),
                1,
                actor.closing ? .5 * effective : .5,
              ],
              'uA': [
                raw.left + raw.width / 2,
                raw.top + raw.height / 2,
                raw.width / 2,
                raw.height / 2,
              ],
              // Zero selects the shader's complete SDF path; it does not skip shapes.
              'uDeep': [0, 0, 0, 0],
            }
          : null,
      'foreground': {
        'menu_alpha': effective * (actor.closing ? fg * extra : 1),
        'seed_alpha': actor.collapsed
            ? 1.0
            : (1 - effective) *
                  (actor.closing ? _clamp(1 - actor.value(0x6f)) : 1),
        'seed_blur': math.sqrt((4 * effective) * (4 * effective) + b * b),
        'menu_blur': math.sqrt(
          (4 * (1 - effective)) * (4 * (1 - effective)) + b * b,
        ),
      },
      'body_content_height': contentHeight,
      'body_content_radius': geometry.bodyScale > 0
          ? geometry.inputBodyRadius / geometry.bodyScale
          : 0.0,
      'seed_center': geometry.rawSeed.center,
      'body_rect': body?.rect,
      'raw_body_rect': geometry.rawBody,
    };
  }
}
