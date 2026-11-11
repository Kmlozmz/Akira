import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'foundations.dart';

class LabPress extends StatefulWidget {
  const LabPress({
    super.key,
    required this.child,
    required this.onPressed,
    this.label,
    this.selected,
  });
  final Widget child;
  final VoidCallback? onPressed;
  final String? label;
  final bool? selected;
  @override
  State<LabPress> createState() => _LabPressState();
}

class _LabPressState extends State<LabPress> {
  bool down = false;
  bool focused = false;
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    final enabled = widget.onPressed != null;
    return Semantics(
      button: true,
      enabled: enabled,
      selected: widget.selected,
      label: widget.label,
      onTap: widget.onPressed,
      child: FocusableActionDetector(
        enabled: enabled,
        onShowFocusHighlight: (v) => setState(() => focused = v),
        mouseCursor: enabled
            ? SystemMouseCursors.click
            : SystemMouseCursors.basic,
        shortcuts: const {
          SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
          SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
        },
        actions: {
          ActivateIntent: CallbackAction<ActivateIntent>(
            onInvoke: (_) {
              widget.onPressed?.call();
              return null;
            },
          ),
        },
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          excludeFromSemantics: true,
          onTap: widget.onPressed,
          onTapDown: enabled ? (_) => setState(() => down = true) : null,
          onTapUp: enabled ? (_) => setState(() => down = false) : null,
          onTapCancel: () => setState(() => down = false),
          child: AnimatedScale(
            scale: down && !m.reduceMotion ? .97 : 1,
            duration: labDuration(context, 110),
            child: AnimatedOpacity(
              opacity: !enabled
                  ? .38
                  : down
                  ? .68
                  : 1,
              duration: labDuration(context, 110),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(14),
                  border: focused
                      ? Border.all(color: m.accent, width: 2)
                      : null,
                ),
                child: widget.child,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class LabRow extends StatelessWidget {
  const LabRow(
    this.title, {
    super.key,
    this.subtitle,
    this.leading,
    this.trailing,
    this.onPressed,
    this.destructive = false,
  });
  final String title;
  final String? subtitle;
  final Widget? leading;
  final Widget? trailing;
  final VoidCallback? onPressed;
  final bool destructive;
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    final content = ConstrainedBox(
      constraints: BoxConstraints(minHeight: m.rowHeight),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        child: Row(
          children: [
            if (leading != null) ...[leading!, const SizedBox(width: 12)],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      fontSize: 16,
                      color: destructive ? const Color(0xFFE56972) : m.text,
                    ),
                  ),
                  if (subtitle != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 3),
                      child: Text(
                        subtitle!,
                        style: TextStyle(fontSize: 12, color: m.secondary),
                      ),
                    ),
                ],
              ),
            ),
            if (trailing != null) ...[const SizedBox(width: 10), trailing!],
            if (onPressed != null) ...[
              const SizedBox(width: 5),
              Icon(Icons.chevron_right, size: 18, color: m.secondary),
            ],
          ],
        ),
      ),
    );
    return onPressed == null
        ? content
        : LabPress(onPressed: onPressed, child: content);
  }
}

/// Blur fallback for layout studies; not the recovered iNotes material.
class LabSurface extends StatelessWidget {
  const LabSurface({super.key, required this.child, this.radius = 28});
  final Widget child;
  final double radius;
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: BackdropFilter(
        filter: ui.ImageFilter.blur(
          sigmaX: m.solid ? 0 : m.blur,
          sigmaY: m.solid ? 0 : m.blur,
        ),
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: m.solid ? m.surface : m.surface.withValues(alpha: m.opacity),
            borderRadius: BorderRadius.circular(radius),
            border: Border.all(
              color: (m.dark ? Colors.white : Colors.black).withValues(
                alpha: m.dark ? .13 : .07,
              ),
              width: .7,
            ),
          ),
          child: child,
        ),
      ),
    );
  }
}

class LabCircle extends StatelessWidget {
  const LabCircle({
    super.key,
    required this.icon,
    required this.label,
    required this.onPressed,
  });
  final IconData icon;
  final String label;
  final VoidCallback? onPressed;
  @override
  Widget build(BuildContext context) => LabPress(
    label: label,
    onPressed: onPressed,
    child: LabSurface(
      radius: 24,
      child: SizedBox(
        width: 48,
        height: 48,
        child: Icon(icon, size: 23, color: LabScope.of(context).text),
      ),
    ),
  );
}

class LabSwitch extends StatelessWidget {
  const LabSwitch({
    super.key,
    required this.value,
    required this.onChanged,
    required this.label,
  });
  final bool value;
  final ValueChanged<bool>? onChanged;
  final String label;
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return Semantics(
      toggled: value,
      label: label,
      enabled: onChanged != null,
      onTap: onChanged == null ? null : () => onChanged!(!value),
      child: ExcludeSemantics(
        child: LabPress(
          onPressed: onChanged == null ? null : () => onChanged!(!value),
          child: SizedBox(
            width: 54,
            height: 44,
            child: Center(
              child: AnimatedContainer(
                duration: labDuration(context, 180),
                width: 51,
                height: 31,
                padding: const EdgeInsets.all(2),
                decoration: BoxDecoration(
                  color: value ? m.accent : m.secondary.withValues(alpha: .3),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: AnimatedAlign(
                  alignment: value
                      ? Alignment.centerRight
                      : Alignment.centerLeft,
                  duration: labDuration(context, 180),
                  curve: Curves.easeOutCubic,
                  child: Container(
                    width: 27,
                    height: 27,
                    decoration: const BoxDecoration(
                      color: Colors.white,
                      shape: BoxShape.circle,
                      boxShadow: [
                        BoxShadow(
                          color: Color(0x22000000),
                          blurRadius: 3,
                          offset: Offset(0, 1),
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
  }
}

class LabSlider extends StatelessWidget {
  const LabSlider({
    super.key,
    required this.value,
    required this.onChanged,
    required this.label,
    this.min = 0,
    this.max = 1,
  });
  final double value;
  final double min;
  final double max;
  final ValueChanged<double>? onChanged;
  final String label;
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    void step(double delta) =>
        onChanged?.call((value + (max - min) * delta).clamp(min, max));
    return Semantics(
      label: label,
      slider: true,
      enabled: onChanged != null,
      value: value.toStringAsFixed(2),
      increasedValue: (value + (max - min) * .05)
          .clamp(min, max)
          .toStringAsFixed(2),
      decreasedValue: (value - (max - min) * .05)
          .clamp(min, max)
          .toStringAsFixed(2),
      onIncrease: onChanged == null ? null : () => step(.05),
      onDecrease: onChanged == null ? null : () => step(-.05),
      child: ExcludeSemantics(
        child: SliderTheme(
          data: SliderThemeData(
            trackHeight: 4,
            activeTrackColor: m.accent,
            inactiveTrackColor: m.secondary.withValues(alpha: .25),
            thumbColor: Colors.white,
            overlayColor: m.accent.withValues(alpha: .12),
            thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 11),
            overlayShape: const RoundSliderOverlayShape(overlayRadius: 22),
            showValueIndicator: ShowValueIndicator.never,
          ),
          child: Slider(value: value, min: min, max: max, onChanged: onChanged),
        ),
      ),
    );
  }
}

class LabSegments extends StatelessWidget {
  const LabSegments({
    super.key,
    required this.labels,
    required this.index,
    required this.onChanged,
  });
  final List<String> labels;
  final int index;
  final ValueChanged<int> onChanged;
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return Container(
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: m.secondary.withValues(alpha: .15),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Row(
        children: [
          for (var i = 0; i < labels.length; i++)
            Expanded(
              child: LabPress(
                selected: index == i,
                onPressed: () => onChanged(i),
                child: AnimatedContainer(
                  duration: labDuration(context, 220),
                  constraints: const BoxConstraints(minHeight: 44),
                  alignment: Alignment.center,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 7,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: index == i ? m.surface : Colors.transparent,
                    borderRadius: BorderRadius.circular(22),
                  ),
                  child: Text(
                    labels[i],
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: index == i
                          ? FontWeight.w600
                          : FontWeight.w400,
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class LabSearch extends StatelessWidget {
  const LabSearch({
    super.key,
    required this.controller,
    this.onChanged,
    this.hint = 'Buscar en las muestras',
  });
  final TextEditingController controller;
  final ValueChanged<String>? onChanged;
  final String hint;
  @override
  Widget build(BuildContext context) {
    final m = LabScope.of(context);
    return LabSurface(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14),
        child: Row(
          children: [
            Icon(Icons.search, size: 20, color: m.secondary),
            const SizedBox(width: 8),
            Expanded(
              child: TextField(
                controller: controller,
                onChanged: onChanged,
                style: const TextStyle(fontSize: 15),
                decoration: InputDecoration(
                  hintText: hint,
                  hintStyle: TextStyle(color: m.secondary),
                  border: InputBorder.none,
                  isDense: true,
                  contentPadding: const EdgeInsets.symmetric(vertical: 16),
                ),
              ),
            ),
            ValueListenableBuilder(
              valueListenable: controller,
              builder: (context, value, _) => value.text.isEmpty
                  ? const SizedBox.shrink()
                  : SizedBox(
                      width: 44,
                      height: 44,
                      child: LabPress(
                        label: 'Borrar búsqueda',
                        onPressed: () {
                          controller.clear();
                          onChanged?.call('');
                        },
                        child: Icon(Icons.cancel, size: 18, color: m.secondary),
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
