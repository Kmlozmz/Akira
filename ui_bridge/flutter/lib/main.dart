import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'appearance/appearance_page.dart';
import 'appearance/settings_controller.dart';
import 'bridge/appearance.g.dart';
import 'laboratory/review/laboratory.dart' show ReviewCapabilities;

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(
    AkiraAppearanceApp(settings: AkiraAppearanceSettings(AppearanceHost())),
  );
}

class AkiraAppearanceApp extends StatefulWidget {
  const AkiraAppearanceApp({
    super.key,
    required this.settings,
    this.shaders = true,
  });
  final AkiraAppearanceSettings settings;
  final bool shaders;
  @override
  State<AkiraAppearanceApp> createState() => _AkiraAppearanceAppState();
}

class _AkiraAppearanceAppState extends State<AkiraAppearanceApp>
    with WidgetsBindingObserver {
  static const _navigation = MethodChannel('akira/appearance/navigation');
  final _navigator = GlobalKey<NavigatorState>();
  final _routes = _AppearanceRoutes();
  bool _signaledInitialPage = false;

  void _signalInitialPage(BuildContext context) {
    final settings = widget.settings;
    if (_signaledInitialPage || (!settings.ready && settings.saveError == null)) {
      return;
    }
    _signaledInitialPage = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      // The optional artwork preview can arrive after the settings are usable.
      if (mounted && context.mounted) await _navigation.invokeMethod<void>('ready');
    });
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _navigation.setMethodCallHandler((call) async {
      if (call.method == 'pop') await _navigator.currentState?.maybePop();
    });
    widget.settings.initialize();
  }

  @override
  void didChangePlatformBrightness() => widget.settings.platformBrightness(
    WidgetsBinding.instance.platformDispatcher.platformBrightness,
  );

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.settings.dispose();
    _navigation.setMethodCallHandler(null);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.settings,
    builder: (context, _) {
      final s = widget.settings;
      return MaterialApp(
        navigatorKey: _navigator,
        navigatorObservers: [_routes],
        debugShowCheckedModeBanner: false,
        title: 'Akira',
        locale: Locale(s.spanish ? 'es' : 'en'),
        supportedLocales: const [Locale('en'), Locale('es')],
        localizationsDelegates: GlobalMaterialLocalizations.delegates,
        theme: ThemeData(
          fontFamily: s.noteFamily,
          brightness: s.dark ? Brightness.dark : Brightness.light,
          scaffoldBackgroundColor: s.background,
          colorScheme: ColorScheme.fromSeed(
            seedColor: s.accent,
            brightness: s.dark ? Brightness.dark : Brightness.light,
          ),
          splashFactory: NoSplash.splashFactory,
        ),
        builder: (context, child) {
          _signalInitialPage(context);
          return ReviewCapabilities(
            shaders: widget.shaders,
            child: MediaQuery(
              data: MediaQuery.of(context).copyWith(
                disableAnimations:
                    s.reduceMotion || MediaQuery.disableAnimationsOf(context),
              ),
              child: child!,
            ),
          );
        },
        home: s.ready
            ? AkiraAppearancePage(settings: s)
            : Scaffold(
                body: SafeArea(
                  child: Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (s.loading)
                            const CircularProgressIndicator.adaptive(),
                          if (s.saveError != null) ...[
                            Text(s.saveError!, textAlign: TextAlign.center),
                            const SizedBox(height: 16),
                            TextButton(
                              onPressed: s.initialize,
                              child: Text(s.tr('Retry', 'Reintentar')),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                ),
              ),
      );
    },
  );
}

class _AppearanceRoutes extends NavigatorObserver {
  @override
  void didChangeTop(Route<dynamic> topRoute, Route<dynamic>? previousTopRoute) {
    final route = topRoute is PopupRoute
        ? 'modal'
        : topRoute.isFirst ? 'root' : 'page';
    _AkiraAppearanceAppState._navigation.invokeMethod<void>('route', route);
  }
}
