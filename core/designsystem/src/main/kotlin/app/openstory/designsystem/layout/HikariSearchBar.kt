package app.openstory.designsystem.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.designsystem.icon.HikariSearchGlyph
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariOpacity
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.theme.hikariTypography

@Composable
fun HikariSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val shape = MaterialTheme.hikariShapes.pill
    val dimensions = MaterialTheme.hikariDimensions
    val containerModifier = modifier
        .fillMaxWidth()
        .height(dimensions.minimumTouchTarget)
        .border(
            dimensions.borderThin,
            MaterialTheme.colorScheme.primary.copy(alpha = MaterialTheme.hikariOpacity.onArtworkSecondary),
            shape,
        )
        .background(
            MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.hikariOpacity.surfaceStrong),
            shape,
        )
        .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
        .then(
            nextFocusRequester?.let { next ->
                Modifier.focusProperties { this.next = next; down = next }
            } ?: Modifier,
        )
        .semantics {
            this.contentDescription = contentDescription
            traversalIndex = 0f
            if (readOnly) role = Role.Button
        }

    if (readOnly) {
        Row(
            modifier = containerModifier.clickable(role = Role.Button) { onClick?.invoke() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchBarContent(value, placeholder)
        }
    } else {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = MaterialTheme.hikariTypography.searchText.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = containerModifier,
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchBarContent(value, placeholder, innerTextField)
                }
            },
        )
    }
}

@Composable
private fun SearchBarContent(
    value: String,
    placeholder: String,
    innerTextField: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.hikariSpacing.space18),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        HikariSearchGlyph(Modifier.size(MaterialTheme.hikariDimensions.iconSmall))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.hikariTypography.searchText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                innerTextField?.invoke() ?: Text(
                    text = value,
                    style = MaterialTheme.hikariTypography.searchText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
