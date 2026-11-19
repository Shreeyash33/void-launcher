package com.ark.voidlauncher.ui.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.AppInfo
import com.ark.voidlauncher.data.db.AppCustomizationEntity
import com.ark.voidlauncher.domain.AppFontWeight
import com.ark.voidlauncher.domain.FontFamily
import com.ark.voidlauncher.domain.TextAlign
import com.ark.voidlauncher.domain.TextTransform

/** Per-app typography overrides. APPLY emits the complete entity for one app. */
@Composable
fun AppTypographyEditorSheet(
    customizations: List<AppCustomizationEntity>,
    apps: List<AppInfo>,
    onCustomize: (AppCustomizationEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val byPackage = remember(customizations) { customizations.associateBy { it.packageName } }
    val packages = remember(customizations, apps) {
        (apps.map { it.packageName } + customizations.map { it.packageName }).distinct()
    }

    EditorSheetScaffold(title = "Typography", onDismiss = onDismiss) {
        item { EditorHint("APPLY SAVES THE WHOLE OVERRIDE FOR THAT APP") }
        packages.forEach { packageName ->
            item(key = "typography-$packageName") {
                val label = apps.firstOrNull { it.packageName == packageName }?.label ?: packageName
                TypographyRow(
                    label = label,
                    initial = byPackage[packageName]
                        ?: AppCustomizationEntity(packageName = packageName),
                    onCustomize = onCustomize,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@Composable
private fun TypographyRow(
    label: String,
    initial: AppCustomizationEntity,
    onCustomize: (AppCustomizationEntity) -> Unit,
) {
    var nickname by remember(initial.packageName) {
        mutableStateOf(initial.customNickname ?: "")
    }
    var isHidden by remember(initial.packageName) { mutableStateOf(initial.isHidden) }
    var isFavorite by remember(initial.packageName) { mutableStateOf(initial.isFavorite) }
    var fontFamily by remember(initial.packageName) { mutableStateOf(initial.fontFamily) }
    var fontSizeSp by remember(initial.packageName) {
        mutableStateOf(initial.fontSizeSp?.toString() ?: "")
    }
    var fontWeight by remember(initial.packageName) { mutableStateOf(initial.fontWeight) }
    var letterSpacingSp by remember(initial.packageName) {
        mutableStateOf(initial.letterSpacingSp?.toString() ?: "")
    }
    var textColorHex by remember(initial.packageName) {
        mutableStateOf(initial.textColorHex ?: "")
    }
    var textAlign by remember(initial.packageName) { mutableStateOf(initial.textAlign) }
    var textTransform by remember(initial.packageName) { mutableStateOf(initial.textTransform) }
    var error by remember(initial.packageName) { mutableStateOf<String?>(null) }

    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )

    EditorTextField(value = nickname, onValueChange = { nickname = it }, label = "NICKNAME")

    Row(modifier = Modifier.fillMaxWidth()) {
        EditorTextAction(
            text = if (isHidden) "HIDDEN: YES" else "HIDDEN: NO",
            onClick = { isHidden = !isHidden },
        )
        EditorTextAction(
            text = if (isFavorite) "FAVORITE: YES" else "FAVORITE: NO",
            onClick = { isFavorite = !isFavorite },
        )
    }

    val fontFamilies = listOf<FontFamily?>(null) + FontFamily.entries
    EditorChoiceRow(
        label = "FONT FAMILY",
        options = fontFamilies.map { it?.name ?: "DEFAULT" },
        selectedIndex = fontFamilies.indexOf(fontFamily),
        onSelect = { fontFamily = fontFamilies[it] },
    )

    EditorTextField(
        value = fontSizeSp,
        onValueChange = { fontSizeSp = it; error = null },
        label = "FONT SIZE SP",
        numeric = true,
    )
    EditorTextField(
        value = letterSpacingSp,
        onValueChange = { letterSpacingSp = it; error = null },
        label = "LETTER SPACING SP",
        numeric = true,
    )

    val fontWeights = listOf<AppFontWeight?>(null) + AppFontWeight.entries
    EditorChoiceRow(
        label = "FONT WEIGHT",
        options = fontWeights.map { it?.name ?: "DEFAULT" },
        selectedIndex = fontWeights.indexOf(fontWeight),
        onSelect = { fontWeight = fontWeights[it] },
    )

    EditorTextField(
        value = textColorHex,
        onValueChange = { textColorHex = it; error = null },
        label = "TEXT COLOR HEX",
    )
    if (textColorHex.isNotBlank() && !isValidHexColor(textColorHex)) {
        EditorHint(text = "INVALID HEX - USE #RRGGBB OR #AARRGGBB", isError = true)
    }

    val alignments = listOf<TextAlign?>(null) + TextAlign.entries
    EditorChoiceRow(
        label = "TEXT ALIGN",
        options = alignments.map { it?.name ?: "DEFAULT" },
        selectedIndex = alignments.indexOf(textAlign),
        onSelect = { textAlign = alignments[it] },
    )

    val transforms = listOf<TextTransform?>(null) + TextTransform.entries
    EditorChoiceRow(
        label = "TEXT TRANSFORM",
        options = transforms.map { it?.name ?: "DEFAULT" },
        selectedIndex = transforms.indexOf(textTransform),
        onSelect = { textTransform = transforms[it] },
    )

    error?.let { EditorHint(text = it, isError = true) }
    EditorTextAction(
        text = "APPLY",
        onClick = {
            val size = fontSizeSp.trim().ifEmpty { null }?.toFloatOrNull()
            val spacing = letterSpacingSp.trim().ifEmpty { null }?.toFloatOrNull()
            if ((fontSizeSp.isNotBlank() && size == null) ||
                (letterSpacingSp.isNotBlank() && spacing == null)
            ) {
                error = "ENTER A NUMBER"
            } else {
                onCustomize(
                    AppCustomizationEntity(
                        packageName = initial.packageName,
                        isFavorite = isFavorite,
                        isHidden = isHidden,
                        customNickname = nickname.trim().ifEmpty { null },
                        fontFamily = fontFamily,
                        fontSizeSp = size,
                        fontWeight = fontWeight,
                        letterSpacingSp = spacing,
                        textColorHex = textColorHex.trim().ifEmpty { null },
                        textAlign = textAlign,
                        textTransform = textTransform,
                    )
                )
                error = null
            }
        },
    )
}

/** Accepts #RRGGBB, #AARRGGBB and optional 0x prefixes; never throws on bad input. */
private fun isValidHexColor(input: String): Boolean {
    val hex = input.trim()
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")
    if (hex.length != 6 && hex.length != 8) return false
    if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return false
    return hex.toULongOrNull(16) != null
}
