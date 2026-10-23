import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/hikari_ui.dart';

import 'foundation_preview.dart';
import 'media_preview.dart';
import 'page_navigation_preview.dart';
import 'picker_preview.dart';
import 'surface_preview.dart';

void main() => runApp(const HikariGallery());

/// Standalone developer entrypoint; never imported by production lib sources.
class HikariGallery extends StatefulWidget {
  const HikariGallery({super.key});

  @override
  State<HikariGallery> createState() => _HikariGalleryState();
}

class _HikariGalleryState extends State<HikariGallery> {
  bool _oled = false;
  bool _enabled = true;
  bool _loading = false;
  bool _stress = false;
  bool _reducedMotion = false;
  bool _rtl = false;
  double _scale = 1;
  double _width = 600;
  int _accent = 0;
  int _selection = 1;
  int _actions = 0;
  String _submission = '';
  String _section = 'Controls & feedback';
  HikariAsyncStatus _status = HikariAsyncStatus.loaded;
  final _search = TextEditingController();

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Hikari UI Gallery',
    theme: HikariTheme.darkTheme(
      oled: _oled,
      accentColor: _accent == 0
          ? HikariAccentSeeds.defaultSeed
          : HikariAccentSeeds.presets[_accent - 1].$2,
    ),
    // Apply stress settings above Navigator so dialogs and sheets inherit them.
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(
        textScaler: TextScaler.linear(_scale),
        disableAnimations: _reducedMotion,
      ),
      child: Directionality(
        textDirection: _rtl ? TextDirection.rtl : TextDirection.ltr,
        child: child!,
      ),
    ),
    home: Builder(
      builder: (context) => Scaffold(
        appBar: AppBar(title: const Text('Foundation / Preview')),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                FilterChip(
                  label: const Text('OLED'),
                  selected: _oled,
                  onSelected: (v) => setState(() => _oled = v),
                ),
                FilterChip(
                  label: const Text('Enabled'),
                  selected: _enabled,
                  onSelected: (v) => setState(() => _enabled = v),
                ),
                FilterChip(
                  label: const Text('Loading'),
                  selected: _loading,
                  onSelected: (v) => setState(() => _loading = v),
                ),
                FilterChip(
                  label: const Text('Stress text'),
                  selected: _stress,
                  onSelected: (v) => setState(() => _stress = v),
                ),
                FilterChip(
                  label: const Text('Reduced motion'),
                  selected: _reducedMotion,
                  onSelected: (v) => setState(() => _reducedMotion = v),
                ),
                FilterChip(
                  label: const Text('RTL'),
                  selected: _rtl,
                  onSelected: (v) => setState(() => _rtl = v),
                ),
              ],
            ),
            Wrap(
              spacing: 8,
              children: [
                for (final compact in [true, false])
                  TextButton(
                    onPressed: () =>
                        showPickerPreview(context, compact: compact),
                    child: Text(compact ? 'Picker compact' : 'Picker expanded'),
                  ),
              ],
            ),
            HikariChoiceMenu<String>(
              key: const ValueKey('gallery-showcase'),
              label: 'Showcase',
              value: _section,
              options: [
                for (final section in [
                  'Foundations',
                  'Controls & feedback',
                  'Media',
                  'Page & navigation',
                  'Surfaces',
                ])
                  HikariChoiceOption(value: section, label: section),
              ],
              onChanged: (value) => setState(() => _section = value!),
            ),
            HikariChoiceMenu<double>(
              label: 'Text scale',
              value: _scale,
              options: [
                for (final v in [1.0, 2.0, 3.0])
                  HikariChoiceOption(value: v, label: '${v}x'),
              ],
              onChanged: (v) => setState(() => _scale = v!),
            ),
            HikariChoiceMenu<double>(
              label: 'Local width',
              value: _width,
              options: [
                for (final v in [
                  220.0,
                  375.0,
                  599.0,
                  600.0,
                  601.0,
                  839.0,
                  840.0,
                  841.0,
                  1200.0,
                  1440.0,
                ])
                  HikariChoiceOption(value: v, label: '$v dp'),
              ],
              onChanged: (v) => setState(() => _width = v!),
            ),
            HikariChoiceMenu<int>(
              label: 'Accent',
              value: _accent,
              options: [
                const HikariChoiceOption(value: 0, label: 'Default'),
                for (var i = 0; i < HikariAccentSeeds.presets.length; i++)
                  HikariChoiceOption(
                    value: i + 1,
                    label: HikariAccentSeeds.presets[i].$1,
                  ),
              ],
              onChanged: (v) => setState(() => _accent = v!),
            ),
            if (_section == 'Controls & feedback')
              Align(
                alignment: AlignmentDirectional.topStart,
                child: ConstrainedBox(
                  constraints: BoxConstraints(maxWidth: _width),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      HikariSectionHeader(
                        title: 'Actions',
                        description: 'Actions: $_actions',
                        actions: [
                          TextButton(
                            onPressed: () => setState(() => _actions = 0),
                            child: const Text('Reset count'),
                          ),
                        ],
                      ),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          for (final variant in HikariButtonVariant.values)
                            HikariButton(
                              label: _stress
                                  ? 'Long translated action with meaningful complete label'
                                  : variant.name,
                              variant: variant,
                              isLoading: _loading,
                              onPressed: _enabled
                                  ? () => setState(() => _actions++)
                                  : null,
                            ),
                          for (final variant in HikariIconButtonVariant.values)
                            HikariIconButton(
                              icon: const Icon(Icons.refresh),
                              tooltip: 'Refresh ${variant.name}',
                              variant: variant,
                              onPressed: _enabled
                                  ? () => setState(() => _actions++)
                                  : null,
                            ),
                        ],
                      ),
                      HikariChipRow(
                        children: [
                          for (var i = 1; i <= 5; i++)
                            HikariFilterChip(
                              label: _stress
                                  ? 'Long translated filter $i'
                                  : 'Filter $i',
                              selected: _selection == i,
                              onSelected: _enabled
                                  ? (_) => setState(() => _selection = i)
                                  : null,
                            ),
                        ],
                      ),
                      HikariSearchField(
                        controller: _search,
                        enabled: _enabled,
                        onSubmitted: (v) => setState(() => _submission = v),
                      ),
                      Text('Submitted: $_submission'),
                      TextButton(
                        onPressed: () =>
                            _search.text = 'External controller update',
                        child: const Text('Set external text'),
                      ),
                      HikariSegmentedControl<int>(
                        value: _selection <= 2 ? _selection : 1,
                        segments: [
                          ButtonSegment(
                            value: 1,
                            label: Text(
                              _stress ? 'First translated selection' : 'First',
                            ),
                          ),
                          ButtonSegment(
                            value: 2,
                            label: Text(
                              _stress
                                  ? 'Second translated selection'
                                  : 'Second',
                            ),
                          ),
                        ],
                        onChanged: _enabled
                            ? (v) => setState(() => _selection = v)
                            : null,
                      ),
                      HikariChoiceMenu<HikariAsyncStatus>(
                        label: 'Async state',
                        value: _status,
                        options: [
                          for (final s in HikariAsyncStatus.values)
                            HikariChoiceOption(value: s, label: s.name),
                        ],
                        onChanged: (v) => setState(() => _status = v!),
                      ),
                      SizedBox(
                        height: 360,
                        child: HikariAsyncStateView(
                          status: _status,
                          message: 'Offline. Previous content retained.',
                          onRetry: () => setState(
                            () => _status = HikariAsyncStatus.refreshing,
                          ),
                          child: ListView(
                            children: [
                              HikariListRow(
                                title: const Text('Retained row'),
                                subtitle: const Text(
                                  'Scroll and focus remain owned here.',
                                ),
                                leading: const Icon(Icons.book),
                                enabled: _enabled,
                                onTap: () => setState(() => _actions++),
                              ),
                            ],
                          ),
                        ),
                      ),
                      for (final tone in HikariNoticeTone.values)
                        HikariNoticeBanner(
                          message:
                              '${tone.name}: visible text and semantic color pair',
                          tone: tone,
                        ),
                    ],
                  ),
                ),
              ),
            if (_section == 'Foundations') const FoundationPreview(),
            if (_section == 'Media')
              MediaPreview(width: _width, stress: _stress, enabled: _enabled),
            if (_section == 'Page & navigation')
              PageNavigationPreview(width: _width, stress: _stress),
            if (_section == 'Surfaces') const SurfacePreview(),
          ],
        ),
      ),
    ),
  );
}
