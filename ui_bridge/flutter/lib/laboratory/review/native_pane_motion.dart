import '../recovered/render_bindings.dart';
import 'dart:convert';
import 'package:flutter/services.dart';

class NativePaneMotion {
  static Future<NativePaneMotion>? _loaded;
  static Future<NativePaneMotion> load() => _loaded ??= _read();
  static Future<NativePaneMotion> _read() async => NativePaneMotion(
    jsonDecode(
      await rootBundle.loadString('assets/review/font-pane-native.json'),
    ),
  );
  NativePaneMotion(Map<String, dynamic> data)
    : origin = (data['origin'] as List).cast<num>(),
      opening = (data['opening'] as List).cast<Map>(),
      closing = (data['closing'] as List).cast<Map>(),
      fontMaterial = Map<String, dynamic>.from(data['font_material']),
      colorMaterial = Map<String, dynamic>.from(data['color_material']);
  final List<num> origin;
  final List<Map> opening, closing;
  final Map<String, dynamic> fontMaterial, colorMaterial;
  Duration duration(bool dismissing) => Duration(
    microseconds: (((dismissing ? closing : opening).last['time'] as num) * 1e6)
        .round(),
  );

  Map<String, dynamic> sample(double fraction, bool dismissing) {
    final rows = dismissing ? closing : opening;
    final time = fraction.clamp(0.0, 1.0) * (rows.last['time'] as num);
    final next = rows.indexWhere((r) => (r['time'] as num) >= time);
    if (next <= 0)
      return copyRenderFields(rows[next < 0 ? rows.length - 1 : 0]);
    final a = rows[next - 1], b = rows[next];
    final t =
        ((time - (a['time'] as num)) /
                ((b['time'] as num) - (a['time'] as num)))
            .clamp(0.0, 1.0);
    dynamic mix(dynamic x, dynamic y) {
      if (x is num && y is num) return x + (y - x) * t;
      if (x is List && y is List)
        return [for (var i = 0; i < x.length; i++) mix(x[i], y[i])];
      if (x is Map && y is Map)
        return {for (final key in x.keys) key: mix(x[key], y[key] ?? x[key])};
      return t < .5 ? x : y;
    }

    final output = Map<String, dynamic>.from(mix(a, b));
    for (final key in ['lean', 'morph']) {
      final fields = output[key];
      if (fields is Map && fields['uShape'] is List) {
        fields['uShape'][0] =
            ((t < .5 ? a : b)[key]?['uShape']?[0] ?? fields['uShape'][0]);
      }
    }
    return output;
  }
}
