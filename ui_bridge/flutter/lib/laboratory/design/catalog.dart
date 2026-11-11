import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'controls.dart';
import 'foundations.dart';
import 'pages.dart';
import 'shader_workbench.dart';

Future<T?> labPush<T>(BuildContext context, Widget page) {
  final m = LabScope.of(context);
  final reduce = m.reduceMotion || MediaQuery.of(context).disableAnimations;
  return Navigator.of(context).push<T>(
    PageRouteBuilder<T>(
      pageBuilder: (_, _, _) => page,
      transitionDuration: reduce ? Duration.zero : m.duration(357),
      reverseTransitionDuration: reduce ? Duration.zero : m.duration(299),
      transitionsBuilder: (_, animation, secondary, child) => SlideTransition(
        position: Tween(begin: Offset.zero, end: const Offset(-.28, 0)).animate(
          CurvedAnimation(parent: secondary, curve: Curves.easeOutCubic),
        ),
        child: SlideTransition(
          position: Tween(begin: const Offset(1, 0), end: Offset.zero).animate(
            CurvedAnimation(parent: animation, curve: Curves.easeOutCubic),
          ),
          child: child,
        ),
      ),
    ),
  );
}

class LabPage extends StatelessWidget {
  const LabPage({
    super.key,
    required this.title,
    required this.children,
    this.note,
  });
  final String title;
  final List<Widget> children;
  final String? note;
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: EdgeInsets.fromLTRB(m.inset, 10, m.inset, 6),
              child: Row(
                children: [
                  LabCircle(
                    icon: Icons.chevron_left,
                    label: 'Volver',
                    onPressed: () => Navigator.pop(context),
                  ),
                  const Spacer(),
                  if (title != 'Apariencia')
                    LabCircle(
                      icon: Icons.tune,
                      label: 'Personalizar laboratorio',
                      onPressed: () => labPush(context, const SettingsPage()),
                    ),
                ],
              ),
            ),
            Expanded(
              child: ListView(
                padding: EdgeInsets.fromLTRB(m.inset, 14, m.inset, 30),
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      fontSize: 32,
                      height: 1.1,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -.7,
                    ),
                  ),
                  const SizedBox(height: 14),
                  if (note != null) EvidenceNote(note!),
                  ...children,
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class CatalogHome extends StatefulWidget {
  const CatalogHome({super.key});
  @override
  State<CatalogHome> createState() => _CatalogHomeState();
}

class _CatalogHomeState extends State<CatalogHome> {
  final search = TextEditingController();
  String query = '';
  int tab = 0;
  @override
  void dispose() {
    search.dispose();
    super.dispose();
  }

  static const entries = <(String, String, IconData)>[
    ('Fundamentos', 'Fuentes, escalas, colores y geometría', Icons.text_fields),
    (
      'Controles',
      'Switches, sliders, segmentos y estados',
      Icons.toggle_on_outlined,
    ),
    ('Superficies', 'Grupos, rellenos y transparencia', Icons.layers_outlined),
    (
      'Navegación',
      'Headers, búsqueda y barras flotantes',
      Icons.view_agenda_outlined,
    ),
    ('Menús y paneles', 'Menú anclado, hoja y confirmación', Icons.more_horiz),
    ('Movimiento', 'Presión, expansión y transiciones', Icons.swap_horiz),
    ('Iconografía', 'Subconjuntos originales del paquete', Icons.grid_view),
    ('Shaders', '8 programas, texturas y uniformes', Icons.blur_on),
  ];
  Widget page(int i) => switch (i) {
    0 => const FoundationsPage(),
    1 => const ControlsPage(),
    2 => const SurfacesPage(),
    3 => const NavigationPage(),
    4 => const OverlaysPage(),
    5 => const MotionPage(),
    6 => const IconographyPage(),
    _ => const ShaderWorkbench(),
  };
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: EdgeInsets.fromLTRB(m.inset, 12, m.inset, 0),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      'iNotes / Akira',
                      style: TextStyle(color: m.secondary, fontSize: 13),
                    ),
                  ),
                  LabCircle(
                    icon: Icons.tune,
                    label: 'Personalizar laboratorio',
                    onPressed: () => labPush(context, const SettingsPage()),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView(
                padding: EdgeInsets.fromLTRB(m.inset, 20, m.inset, 16),
                children: [
                  Text(
                    ['Laboratorio', 'Escenas', 'Evidencia'][tab],
                    style: const TextStyle(
                      fontSize: 34,
                      height: 1.1,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -.7,
                    ),
                  ),
                  const SizedBox(height: 12),
                  EvidenceNote(
                    tab == 0
                        ? 'Catálogo de reconstrucción. Cada familia indica qué está observado y qué sigue en calibración.'
                        : tab == 1
                        ? 'Composiciones con datos de muestra para evaluar el conjunto y sus estados.'
                        : 'Recursos y capturas de iNotes 2.2.2. Tener una muestra implementada todavía no acredita fidelidad.',
                  ),
                  if (tab == 0) ...[
                    LabSearch(
                      controller: search,
                      onChanged: (v) => setState(() => query = v),
                    ),
                    const SizedBox(height: 24),
                    LabGroup(
                      children: [
                        for (var i = 0; i < entries.length; i++)
                          if ('${entries[i].$1} ${entries[i].$2}'
                              .toLowerCase()
                              .contains(query.toLowerCase()))
                            LabRow(
                              entries[i].$1,
                              subtitle: entries[i].$2,
                              leading: Icon(
                                entries[i].$3,
                                size: 23,
                                color: m.accent,
                              ),
                              onPressed: () => labPush(context, page(i)),
                            ),
                      ],
                    ),
                    if (!entries.any(
                      (e) => '${e.$1} ${e.$2}'.toLowerCase().contains(
                        query.toLowerCase(),
                      ),
                    ))
                      const Padding(
                        padding: EdgeInsets.all(24),
                        child: Text(
                          'No hay muestras con ese nombre. Prueba otra búsqueda.',
                        ),
                      ),
                  ],
                  if (tab == 1) ...[
                    LabGroup(
                      children: [
                        LabRow(
                          'Carpetas',
                          subtitle:
                              'Reconstrucción de la composición Glass / Classic',
                          leading: Icon(Icons.folder_outlined, color: m.accent),
                          onPressed: () =>
                              labPush(context, const FolderScene()),
                        ),
                        LabRow(
                          'Apariencia',
                          subtitle: 'Prueba combinada de personalización',
                          onPressed: () =>
                              labPush(context, const SettingsPage()),
                        ),
                        LabRow(
                          'Estados de contenido',
                          subtitle: 'Vacío, carga, error y selección',
                          onPressed: () =>
                              labPush(context, const ContentStatesPage()),
                        ),
                      ],
                    ),
                    const SizedBox(height: 24),
                    const EvidenceNote(
                      'Las escenas usan los componentes del mismo directorio design/. Los cambios globales afectan todas las muestras.',
                    ),
                  ],
                  if (tab == 2) ...[
                    LabGroup(
                      children: [
                        LabRow(
                          'Referencia visual',
                          subtitle:
                              'Seis capturas del original; ampliar y comparar',
                          onPressed: () =>
                              labPush(context, const ReferencePage()),
                        ),
                        LabRow(
                          'Qué sabemos',
                          subtitle: 'Observado, recuperado y pendiente',
                          onPressed: () =>
                              labPush(context, const EvidencePage()),
                        ),
                        LabRow(
                          'Copiar configuración',
                          subtitle:
                              'JSON de los parámetros actuales del laboratorio',
                          onPressed: () async {
                            await Clipboard.setData(
                              ClipboardData(
                                text: jsonEncode({
                                  'kind': 'lab-hypothesis-not-inotes-tokens',
                                  'dark': m.dark,
                                  'classic': m.classic,
                                  'solid': m.solid,
                                  'font': m.font,
                                  'textScale': m.textScale,
                                  'radius': m.radius,
                                  'rowHeight': m.rowHeight,
                                  'blur': m.blur,
                                  'opacity': m.opacity,
                                  'accentIndex': m.accentIndex,
                                  'reduceMotion': m.reduceMotion,
                                  'slowMotion': m.slowMotion,
                                }),
                              ),
                            );
                            if (context.mounted)
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(
                                  content: Text('Configuración copiada'),
                                ),
                              );
                          },
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
            Padding(
              padding: EdgeInsets.fromLTRB(m.inset + 8, 8, m.inset + 8, 10),
              child: LabSurface(
                child: Padding(
                  padding: const EdgeInsets.all(4),
                  child: Row(
                    children: [
                      for (var i = 0; i < 3; i++)
                        Expanded(
                          child: LabPress(
                            selected: tab == i,
                            onPressed: () => setState(() => tab = i),
                            child: Container(
                              padding: const EdgeInsets.symmetric(vertical: 12),
                              decoration: BoxDecoration(
                                color: tab == i
                                    ? m.surface
                                    : Colors.transparent,
                                borderRadius: BorderRadius.circular(24),
                              ),
                              child: Text(
                                ['Catálogo', 'Escenas', 'Evidencia'][i],
                                textAlign: TextAlign.center,
                                style: TextStyle(
                                  fontSize: 13,
                                  color: tab == i ? m.text : m.secondary,
                                  fontWeight: tab == i
                                      ? FontWeight.w600
                                      : FontWeight.w400,
                                ),
                              ),
                            ),
                          ),
                        ),
                    ],
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

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return LabPage(
      title: 'Apariencia',
      note:
          'Estos valores controlan el laboratorio. Solo el primer acento y los colores base proceden de píxeles observados.',
      children: [
        LabSection(
          'Tema',
          child: LabSegments(
            labels: const ['Claro', 'Oscuro'],
            index: m.dark ? 1 : 0,
            onChanged: (i) => m.change(() => m.dark = i == 1),
          ),
        ),
        LabSection(
          'Variación',
          child: LabGroup(
            children: [
              LabRow(
                'Classic',
                trailing: LabSwitch(
                  label: 'Classic',
                  value: m.classic,
                  onChanged: (v) => m.change(() => m.classic = v),
                ),
              ),
              LabRow(
                'Reducir transparencia',
                trailing: LabSwitch(
                  label: 'Reducir transparencia',
                  value: m.solid,
                  onChanged: (v) => m.change(() => m.solid = v),
                ),
              ),
              LabRow(
                'Reducir movimiento',
                trailing: LabSwitch(
                  label: 'Reducir movimiento',
                  value: m.reduceMotion,
                  onChanged: (v) => m.change(() => m.reduceMotion = v),
                ),
              ),
              LabRow(
                'Animación ×4 más lenta',
                trailing: LabSwitch(
                  label: 'Animación más lenta',
                  value: m.slowMotion,
                  onChanged: (v) => m.change(() => m.slowMotion = v),
                ),
              ),
              LabRow(
                'Contornos de medición',
                trailing: LabSwitch(
                  label: 'Contornos de medición',
                  value: m.guides,
                  onChanged: (v) => m.change(() => m.guides = v),
                ),
              ),
            ],
          ),
        ),
        LabSection(
          'Acento',
          note:
              'Los cuatro colores alternativos son muestras de personalización, no valores recuperados.',
          child: Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              for (var i = 0; i < LabModel.accents.length; i++)
                LabPress(
                  label: 'Acento ${i + 1}',
                  selected: m.accentIndex == i,
                  onPressed: () => m.change(() => m.accentIndex = i),
                  child: Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: LabModel.accents[i],
                      shape: BoxShape.circle,
                    ),
                    child: m.accentIndex == i
                        ? const Icon(Icons.check, color: Colors.black)
                        : null,
                  ),
                ),
            ],
          ),
        ),
        LabSection(
          'Fuente candidata',
          note:
              'La familia real de cada elemento aún no está atribuida. Cambiar aquí no equivale al ajuste de fuente de las notas del original.',
          child: LabGroup(
            children: [
              for (final f in ['Sistema', 'Pretendard', 'Roboto', 'MiSans'])
                LabRow(
                  f,
                  trailing: m.font == f
                      ? Icon(Icons.check, color: m.accent)
                      : null,
                  onPressed: () => m.change(() => m.font = f),
                ),
            ],
          ),
        ),
        LabSection(
          'Escalas y material provisional',
          child: LabGroup(
            children: [
              _scale(
                context,
                'Escala de texto',
                m.textScale,
                .85,
                1.6,
                (v) => m.textScale = v,
              ),
              _scale(context, 'Radio', m.radius, 0, 40, (v) => m.radius = v),
              _scale(
                context,
                'Altura mínima de fila',
                m.rowHeight,
                44,
                76,
                (v) => m.rowHeight = v,
              ),
              _scale(
                context,
                'Blur aproximado',
                m.blur,
                0,
                40,
                (v) => m.blur = v,
              ),
              _scale(
                context,
                'Opacidad aproximada',
                m.opacity,
                .1,
                1,
                (v) => m.opacity = v,
              ),
            ],
          ),
        ),
        LabGroup(
          children: [LabRow('Restablecer laboratorio', onPressed: m.reset)],
        ),
      ],
    );
  }

  Widget _scale(
    BuildContext context,
    String label,
    double value,
    double min,
    double max,
    ValueChanged<double> update,
  ) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          '$label · ${value.toStringAsFixed(2)}',
          style: const TextStyle(fontSize: 14),
        ),
        LabSlider(
          label: label,
          value: value,
          min: min,
          max: max,
          onChanged: (v) => LabScope.of(context).change(() => update(v)),
        ),
      ],
    ),
  );
}
