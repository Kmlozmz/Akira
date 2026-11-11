import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import '../recovered/components.dart';

class AppearanceSettings extends RecoveredLabModel {
  AppearanceSettings({this.persist = true});
  static const channel = MethodChannel('rea.review/settings');
  final bool persist;
  int theme = 2;
  int accentArgb = 0xffe4af0a;
  String style = 'Glass';
  String font = 'system';
  String customFontName = 'Custom';
  String locale = 'en';
  String ui(String text) => locale.toLowerCase().startsWith('es') ? const {
    'Appearance':'Apariencia', 'Settings':'Ajustes', 'Font & Size':'Fuente y tamaño',
    'Font':'Fuente', 'Default':'Predeterminada', 'Custom':'Personalizada',
    'Color Theme':'Paleta de colores', 'Select color':'Elegir color', 'Close':'Cerrar', 'Done':'Listo',
    'Choose the font and text size for your music library and player.':'Elige la fuente y el tamaño del texto de tu biblioteca y reproductor.',
  }[text] ?? text : text;
  bool reduceTransparency = false;
  Brightness systemBrightness = Brightness.dark;
  String? saveError;
  Widget Function(BuildContext)? musicPreviewBuilder;
  Future<void> _writes = Future.value();
  @override
  Color get accent => Color(accentArgb);
  String get fontLabel => font == 'serif'
      ? 'Serif'
      : font == 'custom'
      ? customFontName
      : ui('Default');
  String get noteFamily => font == 'serif'
      ? 'serif'
      : font == 'custom'
      ? 'ReaCustomFont'
      : recoveredFont;
  @override
  String get interfaceFont => noteFamily;
  @override
  double get interfaceScale => noteSize / 17;
  Map<String, Object> toJson() => {
    'theme': theme,
    'accent': accentArgb,
    'style': style,
    'font': font,
    'customFontName': customFontName,
    'noteSizeIndex': noteSizeIndex,
    'reduceTransparency': reduceTransparency,
    'reduceMotion': reduceMotion,
  };
  Future<void> load() async {
    systemBrightness =
        WidgetsBinding.instance.platformDispatcher.platformBrightness;
    if (persist) {
      final value = await channel.invokeMethod<String>('read');
      if (value != null) {
        final data = jsonDecode(value) as Map;
        theme = (data['theme'] as int? ?? 2).clamp(0, 2);
        accentArgb = data['accent'] as int? ?? 0xffe4af0a;
        style = data['style'] == 'Classic' ? 'Classic' : 'Glass';
        font = ['serif', 'custom'].contains(data['font'])
            ? data['font'] as String
            : 'system';
        customFontName = data['customFontName'] as String? ?? 'Custom';
        noteSizeIndex = (data['noteSizeIndex'] as int? ?? 3).clamp(0, 6);
        reduceTransparency = data['reduceTransparency'] == true;
        reduceMotion = data['reduceMotion'] == true;
      }
    }
    if (font == 'custom') {
      try {
        final data = await channel.invokeMapMethod<String, dynamic>('readFont');
        if (data == null) {
          font = 'system';
        } else {
          await _loadFont(data);
        }
      } catch (_) {
        font = 'system';
        saveError = 'No se pudo cargar la fuente personalizada.';
      }
    }
    _resolve();
  }

  Future<void> _loadFont(Map<String, dynamic> data) async {
    final bytes = data['bytes'] as Uint8List;
    final loader = FontLoader('ReaCustomFont')
      ..addFont(Future.value(ByteData.sublistView(bytes)));
    await loader.load();
    customFontName = data['name'] as String? ?? 'Custom';
  }

  Future<void> pickCustomFont() async {
    if (!persist) return;
    try {
      final data = await channel.invokeMapMethod<String, dynamic>('pickFont');
      if (data == null) return;
      await _loadFont(data);
      change(() => font = 'custom');
    } catch (_) {
      saveError = 'No se pudo abrir esa fuente.';
      notifyListeners();
    }
  }

  void _resolve() =>
      dark = theme == 1 || (theme == 2 && systemBrightness == Brightness.dark);
  void platformBrightness(Brightness value) {
    systemBrightness = value;
    _resolve();
    notifyListeners();
  }

  void selectTheme(int value) => change(() {
    theme = value;
    _resolve();
  });
  @override
  void change(VoidCallback update) {
    super.change(update);
    if (persist) {
      final payload = jsonEncode(toJson());
      _writes = _writes.then((_) async {
        try {
          await channel.invokeMethod<void>('write', {'value': payload});
          if (saveError != null) {
            saveError = null;
            notifyListeners();
          }
        } catch (_) {
          saveError = 'No se pudieron guardar los ajustes.';
          notifyListeners();
        }
      });
    }
  }
}
