import 'package:flutter/material.dart';
import 'design/catalog.dart';
import 'design/foundations.dart';

void main() => runApp(const DesignLab());

class DesignLab extends StatefulWidget {
  const DesignLab({super.key, this.model});
  final LabModel? model;
  @override
  State<DesignLab> createState() => _DesignLabState();
}

class _DesignLabState extends State<DesignLab> {
  late final model = widget.model ?? LabModel();
  @override
  void dispose() {
    if (widget.model == null) model.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => LabScope(
    model: model,
    child: ListenableBuilder(
      listenable: model,
      builder: (context, _) => MaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'iNotes · Design Lab',
        theme: ThemeData(
          brightness: model.dark ? Brightness.dark : Brightness.light,
          fontFamily: model.fontFamily,
          scaffoldBackgroundColor: model.background,
          colorScheme: ColorScheme.fromSeed(
            seedColor: model.accent,
            brightness: model.dark ? Brightness.dark : Brightness.light,
            surface: model.surface,
          ),
          textTheme:
              ThemeData(
                brightness: model.dark ? Brightness.dark : Brightness.light,
              ).textTheme.apply(
                fontFamily: model.fontFamily,
                bodyColor: model.text,
                displayColor: model.text,
              ),
          splashFactory: NoSplash.splashFactory,
        ),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(
            textScaler: TextScaler.linear(model.textScale),
            disableAnimations:
                model.reduceMotion || MediaQuery.of(context).disableAnimations,
          ),
          child: child!,
        ),
        home: const CatalogHome(),
      ),
    ),
  );
}
