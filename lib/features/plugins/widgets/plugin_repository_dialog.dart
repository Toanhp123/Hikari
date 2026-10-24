import 'package:flutter/material.dart';
import 'package:hikari/core/ui/controls/hikari_choice_menu.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_controller.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/dialog/hikari_dialog.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';

typedef PluginRepositoryInput = ({String url, PluginEcosystem ecosystem});

/// Feature validation and values; shared host owns route, focus and insets.
class PluginRepositoryDialog extends StatefulWidget {
  const PluginRepositoryDialog({super.key, required this.controller});
  final HikariPresentationController<PluginRepositoryInput> controller;

  @override
  State<PluginRepositoryDialog> createState() => _PluginRepositoryDialogState();
}

class _PluginRepositoryDialogState extends State<PluginRepositoryDialog> {
  final _formKey = GlobalKey<FormState>();
  var _url = '';
  PluginEcosystem? _ecosystem;

  void _submit() {
    if (!_formKey.currentState!.validate()) return;
    _formKey.currentState!.save();
    widget.controller.complete((
      url: RepositoryId(_url).toString(),
      ecosystem: _ecosystem!,
    ));
  }

  @override
  Widget build(BuildContext context) => HikariDialog(
    title: const Text('Add repository'),
    onClose: () => widget.controller.cancel(HikariDismissReason.closeButton),
    body: Form(
      key: _formKey,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          FormField<PluginEcosystem>(
            autovalidateMode: AutovalidateMode.onUserInteraction,
            validator: (value) => value == null ? 'Choose an ecosystem.' : null,
            onSaved: (value) => _ecosystem = value,
            builder: (field) => HikariChoiceMenu<PluginEcosystem>(
              label: 'Ecosystem',
              value: field.value,
              options: [
                for (final value in PluginEcosystem.values)
                  HikariChoiceOption(
                    value: value,
                    label: pluginEcosystemLabel(value),
                  ),
              ],
              expanded: true,
              errorText: field.errorText,
              onChanged: field.didChange,
            ),
          ),
          const SizedBox(height: HikariSpacing.md),
          TextFormField(
            decoration: const InputDecoration(
              labelText: 'HTTPS repository URL',
            ),
            autovalidateMode: AutovalidateMode.onUserInteraction,
            onSaved: (value) => _url = value ?? '',
            validator: (value) {
              try {
                RepositoryId(value ?? '');
                return null;
              } catch (_) {
                return 'Enter a valid public HTTPS repository URL.';
              }
            },
            keyboardType: TextInputType.url,
            textInputAction: TextInputAction.done,
            onFieldSubmitted: (_) => _submit(),
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        autofocus: true,
        onPressed: () =>
            widget.controller.cancel(HikariDismissReason.cancelAction),
        child: const Text('Cancel'),
      ),
      FilledButton(onPressed: _submit, child: const Text('Add')),
    ],
  );
}
