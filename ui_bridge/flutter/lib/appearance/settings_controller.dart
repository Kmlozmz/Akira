import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import '../bridge/appearance.g.dart';
import '../laboratory/review/settings.dart';

VisualPreferences copyPreferences(VisualPreferences p, {int? revision}) =>
    VisualPreferences(revision: revision ?? p.revision, theme: p.theme,
      accentArgb: p.accentArgb, glass: p.glass, font: p.font,
      fontName: p.fontName, sizeIndex: p.sizeIndex,
      reduceTransparency: p.reduceTransparency, reduceMotion: p.reduceMotion,
      fullscreenArtwork: p.fullscreenArtwork, animatedArtwork: p.animatedArtwork,
      glassOpacity: p.glassOpacity, diffusion: p.diffusion,
      dockDiffusion: p.dockDiffusion, locale: p.locale, fontId: p.fontId);

class AkiraAppearanceSettings extends AppearanceSettings implements AppearanceClient {
  AkiraAppearanceSettings(this.host, {this.receiveNativeEvents = true})
      : super(persist: false) {
    accentArgb = 0xfffa243c;
    if (receiveNativeEvents) AppearanceClient.setUp(this);
  }

  final AppearanceHost host;
  final bool receiveNativeEvents;
  VisualPreferences? _committed;
  Timer? _debounce;
  Future<void>? _write;
  bool _disposed = false;
  int _generation = 0;
  int _savedGeneration = 0;
  int _fontGeneration = 0;
  String? _fontId;
  bool _pickingFont = false;
  bool ready = false;
  bool loading = false;
  bool fullscreenArtwork = false;
  bool animatedArtwork = true;
  bool dockDiffusion = true;
  double glassOpacity = 0;
  double diffusion = 0;
  List<MusicPreview> tracks = [];
  String? previewError;
  bool get hasPendingChanges => _generation != _savedGeneration;
  bool get spanish => locale.toLowerCase().startsWith('es');
  String tr(String english, String spanishText) => spanish ? spanishText : english;

  @override
  String get defaultInterfaceFont => 'AkiraRobotoFlex';

  @override
  String get noteFamily => font == 'custom'
      ? 'AkiraCustomFont$_fontGeneration' : font == 'system' ? defaultInterfaceFont : super.noteFamily;

  @override
  void configureMaterial(Map<String, Object?> fields, {bool dock = false, bool readable = false}) {
    final blur = fields['uBlurP'];
    if (blur is List && blur.isNotEmpty) {
      fields['uBlurP'] = [...blur]..[0] = (blur[0] as num) *
        (dock && !dockDiffusion ? 0 : diffusion / 14.64);
    }
    final tint = fields['uTintO'];
    if (tint is List && tint.length == 4) {
      final base = (tint[3] as num).toDouble();
      final opacity = readable && glassOpacity < .12 ? .12 : glassOpacity;
      fields['uTintO'] = [...tint]..[3] = (base + (1 - base) * opacity).clamp(0, 1);
    }
  }

  Future<void> initialize() async {
    if (loading) return;
    loading = true;
    saveError = null;
    notifyListeners();
    try {
      final p = await host.read();
      if (_disposed) return;
      _receive(p);
      if (font == 'custom') {
        final data = await host.readFont();
        if (data != null) await _loadCustom(data);
      }
      ready = true;
      unawaited(loadTracks());
    } catch (_) {
      if (!_disposed) saveError = tr('Appearance could not be loaded. Retry.',
        'No se pudo cargar Apariencia. Reintenta.');
    } finally {
      if (!_disposed) { loading = false; notifyListeners(); }
    }
  }

  Future<void> loadTracks() async {
    try {
      final result = await host.preview();
      if (_disposed) return;
      tracks = result;
      previewError = null;
    } catch (_) {
      if (_disposed) return;
      previewError = tr('The music preview could not be loaded.',
        'No se pudo cargar la vista previa de tu música.');
    }
    notifyListeners();
  }

  void _receive(VisualPreferences p) {
    _committed = p;
    theme = switch (p.theme) { 'LIGHT' => 0, 'DARK' || 'AMOLED' => 1, _ => 2 };
    accentArgb = p.accentArgb;
    style = p.glass ? 'Glass' : 'Classic';
    font = p.font;
    customFontName = p.fontName;
    _fontId = p.fontId;
    noteSizeIndex = p.sizeIndex.clamp(0, 6);
    reduceTransparency = p.reduceTransparency;
    reduceMotion = p.reduceMotion;
    fullscreenArtwork = p.fullscreenArtwork;
    animatedArtwork = p.animatedArtwork;
    glassOpacity = p.glassOpacity;
    diffusion = p.diffusion;
    dockDiffusion = p.dockDiffusion;
    locale = p.locale;
    systemBrightness = WidgetsBinding.instance.platformDispatcher.platformBrightness;
    dark = theme == 1 || (theme == 2 && systemBrightness == Brightness.dark);
    notifyListeners();
  }

  VisualPreferences _snapshot() => VisualPreferences(
    revision: _committed!.revision,
    theme: switch (theme) { 0 => 'LIGHT', 1 => 'DARK', _ => 'SYSTEM' },
    accentArgb: accentArgb, glass: style == 'Glass', font: font,
    fontName: customFontName, sizeIndex: noteSizeIndex,
    reduceTransparency: reduceTransparency, reduceMotion: reduceMotion,
    fullscreenArtwork: fullscreenArtwork, animatedArtwork: animatedArtwork,
    glassOpacity: glassOpacity, diffusion: diffusion,
    dockDiffusion: dockDiffusion, locale: locale, fontId: _fontId);

  @override
  void change(VoidCallback update) {
    if (!ready || _disposed) return;
    super.change(update);
    _generation++;
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 160), flush);
  }

  void restoreGlassDefaults() => change(() {
    glassOpacity = .11;
    diffusion = 14.64;
    dockDiffusion = true;
  });

  Future<void> flush() {
    _debounce?.cancel();
    return _write ??= _drain().whenComplete(() => _write = null);
  }

  Future<void> _drain() async {
    while (!_disposed && hasPendingChanges && _committed != null) {
      final generation = _generation;
      final desired = _snapshot();
      try {
        final saved = await host.apply(desired);
        if (_disposed) return;
        _savedGeneration = generation;
        _committed = saved;
        saveError = null;
        if (_generation == generation) _receive(saved);
      } on PlatformException catch (error) {
        if (_disposed) return;
        if (error.code == 'stale-preferences') {
          try {
            final fresh = await host.read();
            if (_disposed) return;
            _receive(fresh);
            _savedGeneration = _generation;
          } catch (_) {
            if (_disposed) return;
          }
          saveError = tr('Settings changed elsewhere. Review your selection.',
            'Los ajustes cambiaron en otra pantalla. Revisa tu selección.');
        } else {
          saveError = tr('Changes could not be saved. Retry.',
            'No se pudieron guardar los cambios. Reintenta.');
        }
        notifyListeners();
        return;
      } catch (_) {
        if (_disposed) return;
        saveError = tr('Changes could not be saved. Retry.',
          'No se pudieron guardar los cambios. Reintenta.');
        notifyListeners();
        return;
      }
    }
  }

  @override
  void preferencesChanged(VisualPreferences p) {
    if (_disposed || (_committed != null && p.revision <= _committed!.revision)) return;
    if (!hasPendingChanges) _receive(p);
  }

  @override
  void refresh() { if (ready) unawaited(initialize()); }

  Future<void> _loadCustom(FontData data) async {
    final next = _fontGeneration + 1;
    final loader = FontLoader('AkiraCustomFont$next')
      ..addFont(Future.value(ByteData.sublistView(data.bytes)));
    await loader.load();
    if (_disposed) return;
    _fontGeneration = next;
    _fontId = data.id;
    customFontName = data.name;
  }

  @override
  Future<void> pickCustomFont() async {
    if (_pickingFont) return;
    _pickingFont = true;
    try {
      final data = await host.pickFont();
      if (_disposed || data == null) return;
      await _loadCustom(data);
      change(() => font = 'custom');
    } catch (_) {
      if (_disposed) return;
      saveError = tr('Choose a valid TTF or OTF font under 12 MB.',
        'Elige una fuente TTF u OTF válida de menos de 12 MB.');
      notifyListeners();
    } finally {
      _pickingFont = false;
    }
  }

  Future<void> close() async {
    await flush();
    if (!_disposed && !hasPendingChanges) await host.close();
  }

  @override
  void dispose() {
    _disposed = true;
    _debounce?.cancel();
    if (receiveNativeEvents) AppearanceClient.setUp(null);
    super.dispose();
  }
}
