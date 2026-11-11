import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:inotes_recovered/inotes_recovered.dart';
import '../recovered/components.dart';
import 'laboratory.dart';
import 'live_glass.dart';
import 'settings.dart';

class LiveReviewPage extends StatefulWidget {
  const LiveReviewPage({super.key, required this.settings});
  final AppearanceSettings settings;
  @override
  State<LiveReviewPage> createState() => _LiveReviewPageState();
}

class _LiveReviewPageState extends State<LiveReviewPage> {
  final probe = LiveGlassProbe();
  final scroll = ScrollController();
  Offset displacement = Offset.zero;
  int taps = 0;
  bool writing = false;
  Timer? timer;
  @override
  void initState() {
    super.initState();
    timer = Timer.periodic(const Duration(milliseconds: 500), (_) async {
      if (writing) return;
      writing = true;
      try {
        await File(
          '${Directory.systemTemp.path}/rea-review-live.json',
        ).writeAsString(
          jsonEncode({
            ...probe.toJson(),
            'scroll_offset': scroll.hasClients ? scroll.offset : 0,
            'panel_offset': [displacement.dx, displacement.dy],
            'taps': taps,
          }),
          flush: true,
        );
      } finally {
        writing = false;
      }
    });
  }

  @override
  void dispose() {
    timer?.cancel();
    scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.settings;
    return ReviewScaffold(
      settings: s,
      title: 'Glass en vivo',
      body: (context) => ListView(
        controller: scroll,
        padding: reviewInsets(context, top: 110),
        children: [
          Text(
            'Desplaza el fondo',
            style: nativeText(s, size: 28, weight: FontWeight.w700),
          ),
          const SizedBox(height: 12),
          Text(
            'También puedes arrastrar el panel de glass sobre las notas.',
            style: nativeText(s, size: 15, color: s.secondary),
          ),
          for (var i = 0; i < 24; i++)
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: NativeGroup(
                model: s,
                child: SizedBox(
                  height: 100,
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Row(
                      children: [
                        SizedBox.square(
                          dimension: 48,
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              color: inotesPresetColors[i % 9],
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: Text(
                            [
                              'Notas de viaje',
                              'Ideas para Akira',
                              'Diseño y movimiento',
                              'Archivo personal',
                            ][i % 4],
                            style: nativeText(
                              s,
                              size: 17,
                              weight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
      sceneForeground: Builder(
        builder: (context) => Stack(
          children: [
            Positioned(
              top: MediaQuery.paddingOf(context).top + 260 + displacement.dy,
              left: 32 + displacement.dx,
              width: MediaQuery.sizeOf(context).width - 64,
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onPanUpdate: (details) =>
                    setState(() => displacement += details.delta),
                child: LiveGlassPane(
                  source: ReviewCaptureScope.of(context),
                  probe: probe,
                  child: SizedBox(
                    height: 112,
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Glass',
                            style: nativeText(
                              s,
                              size: 28,
                              weight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            'Fondo capturado en vivo',
                            style: nativeText(s, size: 15),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
            Positioned(
              left: 32,
              right: 32,
              bottom: MediaQuery.paddingOf(context).bottom + 32,
              child: LiveGlassPane(
                source: ReviewCaptureScope.of(context),
                child: GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: () => setState(() => taps++),
                  child: SizedBox(
                    height: 52,
                    child: Center(
                      child: Text(
                        'Probar pulsación · $taps',
                        style: nativeText(s),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
