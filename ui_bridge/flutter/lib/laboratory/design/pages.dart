import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'catalog.dart';
import 'controls.dart';
import 'foundations.dart';

class FoundationsPage extends StatelessWidget {
  const FoundationsPage({super.key});
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return LabPage(
      title: 'Fundamentos',
      note:
          'Colores observados; fuente seleccionable y escala tipográfica provisionales. Las medidas lógicas dependen de la hipótesis DPR 3,05.',
      children: [
        LabSection(
          'Jerarquía de texto',
          note:
              '34 / 22 / 17 / 15 / 13 / 11 son tamaños de prueba, no tokens internos recuperados.',
          child: LabGroup(
            children: [
              for (final sample in <(double, FontWeight, String)>[
                (34, FontWeight.w700, 'Folders'),
                (22, FontWeight.w600, 'All Folders'),
                (17, FontWeight.w500, 'Notes'),
                (15, FontWeight.w400, 'Texto de lectura'),
                (13, FontWeight.w400, 'Información secundaria'),
                (11, FontWeight.w400, 'Pie y anotación'),
              ])
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          sample.$3,
                          style: TextStyle(
                            fontSize: sample.$1,
                            fontWeight: sample.$2,
                            height: 1.25,
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Text(
                        '${sample.$1.toInt()}',
                        style: TextStyle(fontSize: 12, color: m.secondary),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
        LabSection(
          'Píxeles observados',
          child: LabGroup(
            children: [
              for (final c in <(String, Color)>[
                ('Fondo oscuro · 000000', Colors.black),
                ('Grupo oscuro · 1C1C1E', const Color(0xFF1C1C1E)),
                ('Fondo claro · F2F2F7', const Color(0xFFF2F2F7)),
                ('Grupo claro · FFFFFF', Colors.white),
                ('Acento · E4AF0A', LabModel.accents.first),
                ('Gris · 8A8A8E', const Color(0xFF8A8A8E)),
              ])
                LabRow(
                  c.$1,
                  leading: Container(
                    width: 24,
                    height: 24,
                    decoration: BoxDecoration(
                      color: c.$2,
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: m.separator),
                    ),
                  ),
                ),
            ],
          ),
        ),
        LabSection(
          'Geometría',
          note:
              'Glass: margen ≈16 y radio circular ≈26. Classic: margen ≈20 y radio ≈10. Son ajustes de contorno; la superelipse sigue pendiente.',
          child: LabGroup(
            children: [
              LabRow('Margen actual', trailing: Text('${m.inset.toInt()}')),
              LabRow(
                'Radio actual',
                trailing: Text(m.groupRadius.toStringAsFixed(1)),
              ),
              LabRow(
                'Fila mínima',
                trailing: Text(m.rowHeight.toStringAsFixed(1)),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class ControlsPage extends StatefulWidget {
  const ControlsPage({super.key});
  @override
  State<ControlsPage> createState() => _ControlsPageState();
}

class _ControlsPageState extends State<ControlsPage> {
  bool toggle = true;
  bool check = false;
  double slider = .5;
  int segment = 0;
  int taps = 0;
  final search = TextEditingController();
  @override
  void dispose() {
    search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return LabPage(
      title: 'Controles',
      note:
          'Reconstrucciones interactivas. La apariencia y respuesta exactas todavía deben compararse con el original.',
      children: [
        LabSection(
          'Switch',
          child: LabGroup(
            children: [
              LabRow(
                'Interactivo',
                trailing: LabSwitch(
                  label: 'Switch interactivo',
                  value: toggle,
                  onChanged: (v) => setState(() => toggle = v),
                ),
              ),
              const LabRow(
                'Desactivado',
                trailing: LabSwitch(
                  label: 'Switch desactivado',
                  value: false,
                  onChanged: null,
                ),
              ),
              const LabRow(
                'Activo no editable',
                trailing: LabSwitch(
                  label: 'Switch activo no editable',
                  value: true,
                  onChanged: null,
                ),
              ),
            ],
          ),
        ),
        LabSection(
          'Slider',
          child: LabGroup(
            children: [
              LabRow('Valor', trailing: Text('${(slider * 100).round()} %')),
              LabSlider(
                label: 'Slider interactivo',
                value: slider,
                onChanged: (v) => setState(() => slider = v),
              ),
              const LabSlider(
                label: 'Slider desactivado',
                value: .65,
                onChanged: null,
              ),
            ],
          ),
        ),
        LabSection(
          'Segmentos',
          child: LabSegments(
            labels: const ['Lista', 'Cuadrícula', 'Compacta'],
            index: segment,
            onChanged: (i) => setState(() => segment = i),
          ),
        ),
        LabSection(
          'Botones y presión',
          child: Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              LabCircle(
                icon: Icons.add,
                label: 'Añadir muestra',
                onPressed: () => setState(() => taps++),
              ),
              const LabCircle(
                icon: Icons.add,
                label: 'Añadir desactivado',
                onPressed: null,
              ),
              LabPress(
                onPressed: () => setState(() => taps++),
                child: LabSurface(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 24,
                      vertical: 14,
                    ),
                    child: Text('Acción · $taps'),
                  ),
                ),
              ),
            ],
          ),
        ),
        LabSection(
          'Selección',
          child: LabGroup(
            children: [
              LabRow(
                'Muestra seleccionable',
                leading: Icon(
                  check ? Icons.check_circle : Icons.radio_button_unchecked,
                  color: check ? m.accent : m.secondary,
                ),
                onPressed: () => setState(() => check = !check),
              ),
            ],
          ),
        ),
        LabSection('Búsqueda y teclado', child: LabSearch(controller: search)),
        const EvidenceNote(
          'Los controles incluyen foco de teclado y semántica. Prueba también texto grande y Reduce Motion en Apariencia.',
        ),
      ],
    );
  }
}

class SurfacesPage extends StatelessWidget {
  const SurfacesPage({super.key});
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return LabPage(
      title: 'Superficies',
      note:
          'Los grupos sólidos siguen los colores observados. El material flotante usa blur provisional; el shader recuperado se estudia por separado.',
      children: [
        LabSection(
          'Grupo de contenido',
          child: LabGroup(
            children: [
              LabRow(
                'Notes',
                leading: Icon(Icons.folder_outlined, color: m.accent),
                trailing: const Text('4'),
              ),
              LabRow(
                'REA_Test',
                leading: Icon(Icons.folder_outlined, color: m.accent),
                trailing: const Text('2'),
              ),
            ],
          ),
        ),
        LabSection(
          'Barra sobre contenido',
          note:
              'Desplaza la página y cambia transparencia, blur y tema. La composición todavía no es la de iNotes.',
          child: SizedBox(
            height: 230,
            child: Stack(
              children: [
                Positioned.fill(
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(26),
                    child: const _SurfaceBackground(),
                  ),
                ),
                Positioned(
                  left: 16,
                  right: 16,
                  bottom: 24,
                  child: LabSurface(
                    child: Padding(
                      padding: const EdgeInsets.all(18),
                      child: Row(
                        children: [
                          Icon(Icons.search, color: m.secondary),
                          const SizedBox(width: 12),
                          const Text('Search'),
                        ],
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
        LabSection(
          'Tratamiento y límites',
          child: LabGroup(
            children: [
              LabRow(
                'Sólido / transparente',
                trailing: LabSwitch(
                  label: 'Material sólido',
                  value: m.solid,
                  onChanged: (v) => m.change(() => m.solid = v),
                ),
              ),
              LabRow(
                'Glass / Classic',
                trailing: LabSwitch(
                  label: 'Estilo Classic',
                  value: m.classic,
                  onChanged: (v) => m.change(() => m.classic = v),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _SurfaceBackground extends StatelessWidget {
  const _SurfaceBackground();
  @override
  Widget build(BuildContext context) => ColoredBox(
    color: LabScope.of(context).background,
    child: Column(
      children: [
        for (var i = 0; i < 5; i++)
          Expanded(
            child: Row(
              children: [
                Expanded(
                  child: ColoredBox(
                    color: [
                      const Color(0xFFB38940),
                      const Color(0xFF3D6A99),
                      const Color(0xFF7D6D8D),
                      const Color(0xFF598377),
                      const Color(0xFFAD6160),
                    ][i],
                    child: Center(
                      child: Text(
                        'Fondo ${i + 1}',
                        style: const TextStyle(color: Colors.white),
                      ),
                    ),
                  ),
                ),
                const Expanded(child: Center(child: Text('Texto / líneas'))),
              ],
            ),
          ),
      ],
    ),
  );
}

class NavigationPage extends StatefulWidget {
  const NavigationPage({super.key});
  @override
  State<NavigationPage> createState() => _NavigationPageState();
}

class _NavigationPageState extends State<NavigationPage> {
  final search = TextEditingController();
  int selected = 0;
  @override
  void dispose() {
    search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => LabPage(
    title: 'Navegación',
    note:
        'El original combina título grande, regreso circular y cápsulas. Las siguientes composiciones son candidatos de reconstrucción.',
    children: [
      LabSection(
        'Header compacto',
        child: Row(
          children: [
            LabCircle(
              icon: Icons.chevron_left,
              label: 'Probar regreso',
              onPressed: () => Navigator.pop(context),
            ),
            const Expanded(
              child: Text(
                'Appearance',
                textAlign: TextAlign.center,
                style: TextStyle(fontWeight: FontWeight.w600, fontSize: 17),
              ),
            ),
            LabCircle(
              icon: Icons.more_horiz,
              label: 'Abrir menú',
              onPressed: () => labMenu(context),
            ),
          ],
        ),
      ),
      LabSection(
        'Acciones agrupadas',
        child: Align(
          alignment: Alignment.centerRight,
          child: LabSurface(
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox(
                  width: 52,
                  height: 48,
                  child: LabPress(
                    label: 'Compartir muestra',
                    onPressed: () => labSheet(context),
                    child: const Icon(Icons.ios_share, size: 22),
                  ),
                ),
                SizedBox(
                  width: 52,
                  height: 48,
                  child: LabPress(
                    label: 'Más opciones',
                    onPressed: () => labMenu(context),
                    child: const Icon(Icons.more_horiz),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
      LabSection(
        'Búsqueda flotante',
        child: Row(
          children: [
            Expanded(
              child: LabSearch(controller: search, hint: 'Search'),
            ),
            const SizedBox(width: 12),
            LabCircle(
              icon: Icons.edit_square,
              label: 'Abrir detalle',
              onPressed: () => labPush(context, const DetailScene()),
            ),
          ],
        ),
      ),
      LabSection(
        'Barra de secciones',
        note:
            'La barra de secciones es una extensión para Akira; no está acreditada como un componente de iNotes.',
        child: LabSurface(
          child: Padding(
            padding: const EdgeInsets.all(4),
            child: LabSegments(
              labels: const ['Biblioteca', 'Álbumes', 'Artistas'],
              index: selected,
              onChanged: (i) => setState(() => selected = i),
            ),
          ),
        ),
      ),
      LabGroup(
        children: [
          LabRow(
            'Abrir composición completa',
            onPressed: () => labPush(context, const FolderScene()),
          ),
        ],
      ),
    ],
  );
}

Future<void> labMenu(BuildContext context, {Rect? anchor}) async {
  final m = LabScope.of(context);
  final size = MediaQuery.sizeOf(context);
  final top = (anchor?.bottom ?? (MediaQuery.paddingOf(context).top + 60))
      .clamp(12.0, size.height * .25);
  final choice = await showGeneralDialog<String>(
    context: context,
    barrierDismissible: true,
    barrierLabel: 'Cerrar menú',
    barrierColor: Colors.black26,
    transitionDuration: labDuration(context, 335),
    transitionBuilder: (_, a, _, child) => FadeTransition(
      opacity: a,
      child: ScaleTransition(
        alignment: Alignment.topRight,
        scale: Tween(begin: .35, end: 1.0).animate(
          CurvedAnimation(
            parent: a,
            curve: m.reduceMotion ? Curves.linear : Curves.easeOutBack,
          ),
        ),
        child: child,
      ),
    ),
    pageBuilder: (context, _, _) => SafeArea(
      child: Align(
        alignment: Alignment.topRight,
        child: Padding(
          padding: EdgeInsets.only(top: top, right: 16, left: 16),
          child: Material(
            color: Colors.transparent,
            child: SizedBox(
              width: 280,
              child: SingleChildScrollView(
                child: LabSurface(
                  radius: 26,
                  child: Column(
                    children: [
                      for (final item in <(String, IconData)>[
                        ('Buscar', Icons.search),
                        ('Mover', Icons.folder_outlined),
                        ('Opciones', Icons.tune),
                        ('Vista de adjuntos', Icons.view_agenda_outlined),
                        ('Eliminar muestra', Icons.delete_outline),
                      ])
                        LabRow(
                          item.$1,
                          leading: Icon(item.$2, size: 21),
                          destructive: item.$2 == Icons.delete_outline,
                          onPressed: () => Navigator.pop(context, item.$1),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  if (choice != null && context.mounted) {
    if (choice == 'Eliminar muestra') {
      await labConfirm(context);
    } else {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Acción de muestra: $choice')));
    }
  }
}

Future<void> labSheet(BuildContext context) => showModalBottomSheet<void>(
  context: context,
  backgroundColor: Colors.transparent,
  isScrollControlled: true,
  builder: (context) => SafeArea(
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: LabSurface(
        radius: 30,
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Center(
                child: Container(
                  width: 34,
                  height: 4,
                  decoration: BoxDecoration(
                    color: LabScope.of(context).secondary,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 20),
              const Text(
                'Panel de muestra',
                style: TextStyle(fontSize: 23, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 12),
              const Text(
                'Permite revisar superficie, borde, altura y cierre. Su movimiento todavía usa la transición del SDK.',
              ),
              const SizedBox(height: 20),
              LabGroup(
                children: [
                  LabRow(
                    'Cerrar panel',
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    ),
  ),
);

Future<bool?> labConfirm(BuildContext context) => showDialog<bool>(
  context: context,
  builder: (context) => Dialog(
    backgroundColor: Colors.transparent,
    child: LabSurface(
      child: Padding(
        padding: const EdgeInsets.all(22),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text(
              '¿Eliminar la muestra?',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 12),
            const Text('Solo cambia el estado del laboratorio.'),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: LabPress(
                    onPressed: () => Navigator.pop(context, false),
                    child: const Padding(
                      padding: EdgeInsets.all(12),
                      child: Text('Cancelar', textAlign: TextAlign.center),
                    ),
                  ),
                ),
                Expanded(
                  child: LabPress(
                    onPressed: () => Navigator.pop(context, true),
                    child: const Padding(
                      padding: EdgeInsets.all(12),
                      child: Text(
                        'Eliminar',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: Color(0xFFE56972)),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    ),
  ),
);

class OverlaysPage extends StatelessWidget {
  const OverlaysPage({super.key});
  @override
  Widget build(BuildContext context) => LabPage(
    title: 'Menús y paneles',
    note:
        'El menú prueba expansión desde la esquina superior derecha. Curva, anclaje preciso, hitboxes y composición de vidrio todavía están en calibración.',
    children: [
      LabGroup(
        children: [
          LabRow('Abrir menú anclado', onPressed: () => labMenu(context)),
          LabRow('Abrir panel inferior', onPressed: () => labSheet(context)),
          LabRow('Abrir confirmación', onPressed: () => labConfirm(context)),
        ],
      ),
    ],
  );
}

class MotionPage extends StatefulWidget {
  const MotionPage({super.key});
  @override
  State<MotionPage> createState() => _MotionPageState();
}

class _MotionPageState extends State<MotionPage> {
  bool expanded = false;
  int presses = 0;
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return LabPage(
      title: 'Movimiento',
      note:
          'Una grabación mostró actividad de ~357 ms al entrar, ~299 ms al volver y ~335 ms al expandir menú. No son constantes de código ni curvas recuperadas.',
      children: [
        LabSection(
          'Expansión',
          note:
              'La curva easeOutBack es una hipótesis para ensayar sobreextensión.',
          child: Align(
            alignment: Alignment.centerRight,
            child: LabPress(
              label: 'Expandir muestra',
              onPressed: () => setState(() => expanded = !expanded),
              child: AnimatedContainer(
                duration: labDuration(context, 335),
                curve: m.reduceMotion ? Curves.linear : Curves.easeOutBack,
                width: expanded ? 260 : 90,
                height: expanded ? 190 : 52,
                decoration: BoxDecoration(
                  color: m.surface,
                  borderRadius: BorderRadius.circular(26),
                ),
                child: Center(
                  child: Text(expanded ? 'Toca para contraer' : '•••'),
                ),
              ),
            ),
          ),
        ),
        LabSection(
          'Respuesta de presión',
          child: LabPress(
            onPressed: () => setState(() => presses++),
            child: LabSurface(
              child: Padding(
                padding: const EdgeInsets.all(22),
                child: Text(
                  'Mantén pulsado y suelta · $presses',
                  textAlign: TextAlign.center,
                ),
              ),
            ),
          ),
        ),
        LabSection(
          'Navegación',
          child: LabGroup(
            children: [
              LabRow(
                'Entrar a detalle',
                onPressed: () => labPush(context, const DetailScene()),
              ),
              LabRow('Menú desde su origen', onPressed: () => labMenu(context)),
              LabRow(
                'Reducir movimiento',
                trailing: LabSwitch(
                  label: 'Reducir movimiento',
                  value: m.reduceMotion,
                  onChanged: (v) => m.change(() => m.reduceMotion = v),
                ),
              ),
              LabRow(
                'Inspeccionar a ×4',
                trailing: LabSwitch(
                  label: 'Inspeccionar a cámara lenta',
                  value: m.slowMotion,
                  onChanged: (v) => m.change(() => m.slowMotion = v),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class FolderScene extends StatefulWidget {
  const FolderScene({super.key});
  @override
  State<FolderScene> createState() => _FolderSceneState();
}

class _FolderSceneState extends State<FolderScene> {
  final search = TextEditingController();
  bool editing = false;
  @override
  void dispose() {
    search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: EdgeInsets.fromLTRB(m.inset, 10, m.inset, 10),
              child: Row(
                children: [
                  LabCircle(
                    icon: Icons.chevron_left,
                    label: 'Salir de escena',
                    onPressed: () => Navigator.pop(context),
                  ),
                  const Spacer(),
                  LabCircle(
                    icon: Icons.create_new_folder_outlined,
                    label: 'Crear carpeta de muestra',
                    onPressed: () => labSheet(context),
                  ),
                  const SizedBox(width: 12),
                  LabPress(
                    onPressed: () => setState(() => editing = !editing),
                    child: LabSurface(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 22,
                          vertical: 14,
                        ),
                        child: Text(editing ? 'Done' : 'Edit'),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView(
                padding: EdgeInsets.symmetric(horizontal: m.inset),
                children: [
                  const Text(
                    'Folders',
                    style: TextStyle(
                      fontSize: 34,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -.7,
                    ),
                  ),
                  const SizedBox(height: 22),
                  if (m.classic) ...[
                    LabSearch(controller: search, hint: 'Search'),
                    const SizedBox(height: 18),
                  ],
                  Row(
                    children: [
                      const Expanded(
                        child: Text(
                          'All Folders',
                          style: TextStyle(
                            fontSize: 22,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      Icon(Icons.expand_more, color: m.secondary),
                    ],
                  ),
                  const SizedBox(height: 12),
                  LabGroup(
                    children: [
                      for (final item in [('Notes', '4'), ('REA_Test', '2')])
                        LabRow(
                          item.$1,
                          leading: Icon(
                            editing
                                ? Icons.remove_circle_outline
                                : Icons.folder_outlined,
                            color: m.accent,
                          ),
                          trailing: Text(
                            item.$2,
                            style: TextStyle(color: m.secondary),
                          ),
                          onPressed: () =>
                              labPush(context, const DetailScene()),
                        ),
                    ],
                  ),
                  const SizedBox(height: 26),
                  LabGroup(
                    children: [
                      LabRow(
                        'Unlimited Folders',
                        leading: Icon(
                          Icons.workspace_premium_outlined,
                          color: m.accent,
                        ),
                        onPressed: () => labSheet(context),
                      ),
                      LabRow(
                        'Pinned',
                        leading: Icon(Icons.push_pin_outlined, color: m.accent),
                        trailing: const Text('1'),
                        onPressed: () =>
                            labPush(context, const ContentStatesPage()),
                      ),
                      LabRow(
                        'Recently Deleted',
                        leading: Icon(Icons.delete_outline, color: m.accent),
                        trailing: const Text('0'),
                        onPressed: () =>
                            labPush(context, const ContentStatesPage()),
                      ),
                      LabRow(
                        'Settings',
                        leading: Icon(Icons.settings_outlined, color: m.accent),
                        onPressed: () => labPush(context, const SettingsPage()),
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),
                  const EvidenceNote(
                    'Escena reconstruida con datos de la referencia. Tipografía, iconos, controles y vidrio aún no están validados como equivalentes.',
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 10),
              child: Row(
                children: [
                  Expanded(
                    child: m.classic
                        ? const SizedBox(height: 48)
                        : LabSearch(controller: search, hint: 'Search'),
                  ),
                  const SizedBox(width: 12),
                  LabCircle(
                    icon: Icons.edit_square,
                    label: 'Nueva muestra',
                    onPressed: () => labPush(context, const DetailScene()),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class DetailScene extends StatelessWidget {
  const DetailScene({super.key});
  @override
  Widget build(BuildContext context) => LabPage(
    title: 'Detalle de muestra',
    children: [
      const EvidenceNote(
        'Esta pantalla permite revisar jerarquía, lectura y transición de entrada y regreso.',
      ),
      const Text(
        'Texto de lectura',
        style: TextStyle(fontSize: 22, fontWeight: FontWeight.w600),
      ),
      const SizedBox(height: 14),
      const Text(
        'Una superficie discreta deja el protagonismo al contenido. Aquí se comparan el tamaño de la letra, la distancia entre párrafos y la relación con los controles flotantes.',
        style: TextStyle(fontSize: 17, height: 1.5),
      ),
      const SizedBox(height: 28),
      LabGroup(
        children: [
          LabRow('Abrir opciones', onPressed: () => labMenu(context)),
          LabRow('Mostrar panel', onPressed: () => labSheet(context)),
        ],
      ),
    ],
  );
}

class ContentStatesPage extends StatefulWidget {
  const ContentStatesPage({super.key});
  @override
  State<ContentStatesPage> createState() => _ContentStatesPageState();
}

class _ContentStatesPageState extends State<ContentStatesPage> {
  int state = 0;
  final selected = <int>{};
  Timer? timer;
  void choose(int value) {
    timer?.cancel();
    setState(() => state = value);
    if (value == 1)
      timer = Timer(const Duration(seconds: 2), () {
        if (mounted) setState(() => state = 3);
      });
  }

  @override
  void dispose() {
    timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return LabPage(
      title: 'Estados',
      note:
          'Escenarios de resistencia del laboratorio. No se han acreditado estos textos ni estados como copias de iNotes.',
      children: [
        LabSegments(
          labels: const ['Vacío', 'Carga', 'Error', 'Lista'],
          index: state,
          onChanged: choose,
        ),
        const SizedBox(height: 26),
        if (state == 0)
          LabGroup(
            children: [
              LabRow(
                'Todavía no hay muestras',
                subtitle: 'Añade una para revisar selección y filas.',
                onPressed: () => choose(3),
              ),
            ],
          ),
        if (state == 1)
          const Padding(
            padding: EdgeInsets.all(30),
            child: Column(
              children: [
                CircularProgressIndicator(),
                SizedBox(height: 18),
                Text('Preparando muestras durante dos segundos…'),
              ],
            ),
          ),
        if (state == 2)
          LabGroup(
            children: [
              LabRow(
                'Error simulado',
                subtitle: 'Reintenta para ejecutar una carga local.',
                onPressed: () => choose(1),
              ),
            ],
          ),
        if (state == 3) ...[
          Text('${selected.length} seleccionadas'),
          const SizedBox(height: 12),
          LabGroup(
            children: [
              for (var i = 0; i < 4; i++)
                LabRow(
                  'Muestra ${i + 1}',
                  leading: Icon(
                    selected.contains(i)
                        ? Icons.check_circle
                        : Icons.radio_button_unchecked,
                    color: selected.contains(i) ? m.accent : m.secondary,
                  ),
                  onPressed: () => setState(
                    () => selected.contains(i)
                        ? selected.remove(i)
                        : selected.add(i),
                  ),
                ),
            ],
          ),
        ],
      ],
    );
  }
}

class IconographyPage extends StatefulWidget {
  const IconographyPage({super.key});
  @override
  State<IconographyPage> createState() => _IconographyPageState();
}

class _IconographyPageState extends State<IconographyPage> {
  late final inventory = rootBundle
      .loadString('assets/icons.json')
      .then((s) => jsonDecode(s) as List);
  final search = TextEditingController();
  int family = 0;
  double size = 27;
  String query = '';
  @override
  void dispose() {
    search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => LabPage(
    title: 'Iconografía',
    note:
        'Glyphs incluidos en el APK. Su presencia no identifica qué widget los usa. Las otras escenas emplean iconos del SDK como candidatos.',
    children: [
      LabSegments(
        labels: const ['Material del APK', 'Cupertino del APK'],
        index: family,
        onChanged: (i) => setState(() => family = i),
      ),
      const SizedBox(height: 16),
      LabSearch(
        controller: search,
        onChanged: (v) => setState(() => query = v),
      ),
      const SizedBox(height: 12),
      LabSlider(
        label: 'Tamaño de icono',
        value: size,
        min: 16,
        max: 48,
        onChanged: (v) => setState(() => size = v),
      ),
      FutureBuilder<List>(
        future: inventory,
        builder: (context, snapshot) {
          if (snapshot.hasError)
            return Text('No se pudo cargar el inventario: ${snapshot.error}');
          if (!snapshot.hasData) return const Text('Cargando glyphs del APK…');
          final list = snapshot.data!
              .where(
                (v) =>
                    v['family'] == family &&
                    '${v['name']} ${v['codepoint']}'.toLowerCase().contains(
                      query.toLowerCase(),
                    ),
              )
              .toList();
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('${list.length} puntos de código'),
              const SizedBox(height: 16),
              LayoutBuilder(
                builder: (context, constraints) => Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final item in list)
                      SizedBox(
                        width: (constraints.maxWidth - 16) / 3,
                        child: LabPress(
                          onPressed: () {
                            Clipboard.setData(
                              ClipboardData(
                                text: '${item['codepoint']} ${item['name']}',
                              ),
                            );
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text('${item['codepoint']} copiado'),
                              ),
                            );
                          },
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              vertical: 14,
                              horizontal: 5,
                            ),
                            decoration: BoxDecoration(
                              color: LabScope.of(context).surface,
                              borderRadius: BorderRadius.circular(14),
                            ),
                            child: Column(
                              children: [
                                Text(
                                  String.fromCharCode(
                                    int.parse(
                                      (item['codepoint'] as String).substring(
                                        2,
                                      ),
                                      radix: 16,
                                    ),
                                  ),
                                  style: TextStyle(
                                    fontFamily: family == 0
                                        ? 'ReaMaterial'
                                        : 'ReaCupertino',
                                    fontSize: size,
                                  ),
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  item['codepoint'],
                                  style: const TextStyle(fontSize: 11),
                                ),
                                Text(
                                  item['name'],
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                  textAlign: TextAlign.center,
                                  style: TextStyle(
                                    fontSize: 10,
                                    color: LabScope.of(context).secondary,
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
            ],
          );
        },
      ),
    ],
  );
}

class ReferencePage extends StatelessWidget {
  const ReferencePage({super.key});
  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                LabCircle(
                  icon: Icons.chevron_left,
                  label: 'Volver',
                  onPressed: () => Navigator.pop(context),
                ),
                const SizedBox(width: 14),
                const Expanded(child: Text('iNotes · capturas originales')),
              ],
            ),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
            child: EvidenceNote(
              'Amplía para inspeccionar. La hoja es una referencia de composición, no una comparación alineada de píxeles.',
            ),
          ),
          Expanded(
            child: InteractiveViewer(
              maxScale: 8,
              child: Center(
                child: Image.asset('assets/reference.jpg', fit: BoxFit.contain),
              ),
            ),
          ),
        ],
      ),
    ),
  );
}

class EvidencePage extends StatelessWidget {
  const EvidencePage({super.key});
  @override
  Widget build(BuildContext context) => const LabPage(
    title: 'Qué sabemos',
    children: [
      LabSection(
        'Observado',
        child: LabGroup(
          children: [
            LabRow(
              'Color de salida',
              subtitle: 'Negro, F2F2F7, 1C1C1E, blanco y E4AF0A en capturas.',
            ),
            LabRow(
              'Geometría parcial',
              subtitle: 'Márgenes y curvas ajustados desde una resolución.',
            ),
            LabRow(
              'Composición y movimiento',
              subtitle: 'Glass / Classic, barras flotantes y una grabación.',
            ),
          ],
        ),
      ),
      LabSection(
        'Recuperado del paquete',
        child: LabGroup(
          children: [
            LabRow(
              '8 shaders',
              subtitle:
                  'SkSL, GLSL y SPIR-V. Adaptaciones estructuradas compiladas.',
            ),
            LabRow(
              'Fuentes e iconos',
              subtitle: 'Pretendard variable y dos subconjuntos de símbolos.',
            ),
            LabRow(
              'Bindings adaptados',
              subtitle:
                  '206 comprobaciones de extremos y samplers en el dispositivo.',
            ),
          ],
        ),
      ),
      LabSection(
        'Pendiente para acreditar equivalencia',
        child: LabGroup(
          children: [
            LabRow(
              'Material real',
              subtitle:
                  'Uniformes originales, orden de pases y captura del fondo.',
            ),
            LabRow(
              'Escala y atribución',
              subtitle: 'Fuente, peso, tamaño y glyph por cada componente.',
            ),
            LabRow(
              'Estados y movimiento',
              subtitle:
                  'Repetir mediciones de gestos, curvas, presión e interrupciones.',
            ),
            LabRow(
              'Comparación equivalente',
              subtitle:
                  'Mismo contenido, fondo, escala y estado, en varias densidades.',
            ),
          ],
        ),
      ),
      EvidenceNote(
        'Fuentes de investigación: sistema-diseno-inotes.md, shaders-recuperados.md, superficies-medidas.json, inventario-fuentes-iconos.json y analisis-movimiento-profundo.md. Este catálogo no afirma una réplica completa.',
      ),
    ],
  );
}
