import 'package:flutter/material.dart';

Duration labDuration(BuildContext context, int milliseconds) =>
    MediaQuery.of(context).disableAnimations
    ? Duration.zero
    : LabScope.of(context).duration(milliseconds);

/// Output colors were sampled from iNotes screenshots. Geometry remains a fit.
class LabModel extends ChangeNotifier {
  bool dark = true;
  bool classic = false;
  bool solid = false;
  bool reduceMotion = false;
  bool slowMotion = false;
  bool guides = false;
  double textScale = 1;
  double radius = 26;
  double rowHeight = 52;
  double blur = 18;
  double opacity = .6;
  int accentIndex = 0;
  String font = 'Sistema';

  static const accents = [
    Color(0xFFE4AF0A),
    Color(0xFF3478F6),
    Color(0xFF30A46C),
    Color(0xFFE95A6F),
    Color(0xFF9B6BDE),
  ];
  Color get accent => accents[accentIndex];
  Color get background => dark ? Colors.black : const Color(0xFFF2F2F7);
  Color get surface => dark ? const Color(0xFF1C1C1E) : Colors.white;
  Color get text => dark ? const Color(0xFFF5F5F7) : const Color(0xFF161618);
  Color get secondary =>
      dark ? const Color(0xFFAAAAAF) : const Color(0xFF67676D);
  Color get separator =>
      dark ? const Color(0xFF38383A) : const Color(0xFFDDDDDF);
  double get groupRadius => classic ? 10 : radius;
  double get inset => classic ? 20 : 16;
  String? get fontFamily => switch (font) {
    'Pretendard' => 'ReaPretendard',
    'Roboto' => 'ReaRoboto',
    'MiSans' => 'ReaMiSans',
    _ => null,
  };
  Duration duration(int milliseconds) => Duration(
    milliseconds: reduceMotion ? 0 : milliseconds * (slowMotion ? 4 : 1),
  );
  void change(VoidCallback update) {
    update();
    notifyListeners();
  }

  void reset() => change(() {
    dark = true;
    classic = false;
    solid = false;
    reduceMotion = false;
    slowMotion = false;
    guides = false;
    textScale = 1;
    radius = 26;
    rowHeight = 52;
    blur = 18;
    opacity = .6;
    accentIndex = 0;
    font = 'Sistema';
  });
}

class LabScope extends InheritedNotifier<LabModel> {
  const LabScope({super.key, required LabModel model, required super.child})
    : super(notifier: model);
  static LabModel of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<LabScope>()!.notifier!;
}

class LabSection extends StatelessWidget {
  const LabSection(this.title, {super.key, required this.child, this.note});
  final String title;
  final String? note;
  final Widget child;
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 28),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(10, 0, 10, 9),
            child: Text(
              title,
              style: TextStyle(fontSize: 14, color: m.secondary),
            ),
          ),
          child,
          if (note != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(10, 9, 10, 0),
              child: Text(
                note!,
                style: TextStyle(
                  fontSize: 12,
                  height: 1.45,
                  color: m.secondary,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class LabGroup extends StatelessWidget {
  const LabGroup({super.key, required this.children});
  final List<Widget> children;
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return Container(
      decoration: BoxDecoration(
        color: m.surface,
        borderRadius: BorderRadius.circular(m.groupRadius),
        border: m.guides ? Border.all(color: m.accent) : null,
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          for (var i = 0; i < children.length; i++) ...[
            if (i > 0)
              Padding(
                padding: const EdgeInsets.only(left: 16),
                child: Divider(height: 1, thickness: .5, color: m.separator),
              ),
            children[i],
          ],
        ],
      ),
    );
  }
}

class EvidenceNote extends StatelessWidget {
  const EvidenceNote(this.text, {super.key});
  final String text;
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 24),
      child: Text(
        text,
        style: TextStyle(fontSize: 13, height: 1.5, color: m.secondary),
      ),
    );
  }
}
