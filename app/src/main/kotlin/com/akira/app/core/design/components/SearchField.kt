// SPDX-License-Identifier: GPL-3.0-only
// Page bb28993 SearchDesign.kt SearchField: original field geometry and input.
package com.akira.app.core.design.components

import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

private val SearchFieldHeight = 48.dp
private object SearchFieldColors {
    private val light: Boolean @Composable get() = MaterialTheme.colorScheme.background.luminance() > .5f
    val Field: Color @Composable get() = if (light) Color(0xFFF2F2F4) else Color(0xFF141416)
    val FieldFocused: Color @Composable get() = if (light) Color(0xFFE7E7EA) else Color(0xFF1E1E21)
    val Primary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val Placeholder: Color @Composable get() = if (light) Color(0xFF68686E) else Color(0xFF77777D)
}

@Composable
fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    readOnlyClick: (() -> Unit)? = null,

    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SearchFieldHeight)
            .clip(RoundedCornerShape(50))
            .background(if (focused) SearchFieldColors.FieldFocused else SearchFieldColors.Field)
            .then(
                if (readOnlyClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = readOnlyClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(10.dp))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = SearchFieldColors.Placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (enabled) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(
                        TextStyle(color = SearchFieldColors.Primary, fontSize = 15.sp)
                    ),
                    cursorBrush = SolidColor(SearchFieldColors.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(value.text) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (onFocusChanged != null) {
                                Modifier.onFocusChanged { onFocusChanged(it.isFocused) }
                            } else {
                                Modifier
                            }
                        ),
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(10.dp))
            trailing()
        }
    }
}
