import 'dart:ui' as ui;

final _bindings = Expando<_ShaderBindings>();

class _ShaderBindings {
  final floats = <String, List<ui.UniformFloatSlot>>{};
  final samplers = <String, ui.ImageSamplerSlot>{};
}

// Flutter registers a weak slot for every getUniformFloat call. Retain each
// binding once per shader, rather than growing and scanning that list per frame.
void bindReviewFloats(
  ui.FragmentShader shader,
  Map<String, Object?> fields, {
  bool imageFilter = false,
}) {
  final cache = _bindings[shader] ??= _ShaderBindings();
  for (final field in fields.entries) {
    if (imageFilter && field.key == 'uSize') continue;
    final slots = cache.floats.putIfAbsent(field.key, () => []);
    var index = 0;
    void write(Object? value) {
      if (value is num) {
        if (index == slots.length) {
          slots.add(shader.getUniformFloat(field.key, index));
        }
        slots[index++].set(value.toDouble());
      } else if (value is List) {
        for (final item in value) {
          write(item);
        }
      } else {
        throw ArgumentError.value(value, 'uniform');
      }
    }

    write(field.value);
  }
}

int reviewSamplerIndex(ui.FragmentShader shader, String name) {
  final cache = _bindings[shader] ??= _ShaderBindings();
  return cache.samplers
      .putIfAbsent(name, () => shader.getImageSampler(name))
      .shaderIndex;
}

Map<String, dynamic> copyRenderFields(Map source) {
  dynamic copy(dynamic value) {
    if (value is Map)
      return value.map((key, item) => MapEntry(key, copy(item)));
    if (value is List) return [for (final item in value) copy(item)];
    return value;
  }

  return {for (final key in source.keys) key as String: copy(source[key])};
}
