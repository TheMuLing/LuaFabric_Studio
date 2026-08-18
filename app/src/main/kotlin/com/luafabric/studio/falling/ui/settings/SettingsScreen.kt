package com.luafabric.studio.falling.ui.settings

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataArray
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.LocaleListCompat
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.components.AppIconGrid
import com.luafabric.studio.falling.ui.components.ColorPickerDialog
import com.luafabric.studio.falling.ui.theme.ThemeType
import muling.views.tool.utils.IconManager
import muling.views.tool.utils.NonBlockingToastState
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ColorPreviewChip(
    color: Color,
    onClick: () -> Unit,
    size: Int = 28
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val checkerSize = 6f
            val rows = (this.size.height / checkerSize).toInt() + 1
            val cols = (this.size.width / checkerSize).toInt() + 1

            for (i in 0 until rows) {
                for (j in 0 until cols) {
                    val checkerColor = if ((i + j) % 2 == 0) Color.LightGray else Color.White
                    drawRect(
                        color = checkerColor,
                        topLeft = Offset(j * checkerSize, i * checkerSize),
                        size = Size(checkerSize, checkerSize)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(color)
        )
    }
}

enum class EditorFontType {
    JETBRAINS_MONO,
    FIRA_CODE,
    CUSTOM
}

enum class DarkMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK
}

enum class FontFamilyType {
    DEFAULT,
    SANS_SERIF,
    SERIF,
    MONOSPACE,
    JOSEFIN_SANS
}

object FontManager {
    fun getEditorTypeface(context: Context, settings: SettingsData): Typeface? {
        return try {
            when (settings.editorFontType) {
                EditorFontType.JETBRAINS_MONO -> {
                    ResourcesCompat.getFont(context, R.font.jetbrains_mono)
                }

                EditorFontType.FIRA_CODE -> {
                    ResourcesCompat.getFont(context, R.font.fira_code)
                }

                EditorFontType.CUSTOM -> {
                    if (settings.customFontPath.isNotBlank()) {
                        try {
                            Typeface.createFromFile(settings.customFontPath)
                        } catch (_: Exception) {
                            ResourcesCompat.getFont(context, R.font.jetbrains_mono)
                        }
                    } else {
                        ResourcesCompat.getFont(context, R.font.jetbrains_mono)
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    currentSettings: SettingsData,
    onSettingsChanged: (SettingsData) -> Unit,
    toast: NonBlockingToastState
) {
    val settingsManager = SettingsManager

    var appIconExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentSettingsState = settingsManager.currentSettings

    var appearanceExpanded by remember { mutableStateOf(false) }
    var editorConfigExpanded by remember { mutableStateOf(false) }
    var syntaxHighlightExpanded by remember { mutableStateOf(false) }
    var toastSettingsExpanded by remember { mutableStateOf(false) }

    var fontMenuExpanded by remember { mutableStateOf(false) }
    var editorFontMenuExpanded by remember { mutableStateOf(false) }

    var customFontPath by remember { mutableStateOf(currentSettingsState.customFontPath) }

    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerTitle by remember { mutableStateOf("") }
    var colorToEdit by remember { mutableStateOf<Color?>(null) }
    var onColorSelected by remember { mutableStateOf<(Color) -> Unit>({}) }

    // 使用资源ID，避免在remember中直接调用stringResource
    val fontFamilyOptions = remember {
        listOf(
            R.string.settings_font_default to FontFamilyType.DEFAULT,
            R.string.settings_font_sans_serif to FontFamilyType.SANS_SERIF,
            R.string.settings_font_serif to FontFamilyType.SERIF,
            R.string.settings_font_monospace to FontFamilyType.MONOSPACE,
            R.string.settings_font_josefin_sans to FontFamilyType.JOSEFIN_SANS
        )
    }

    val editorFontOptions = remember {
        listOf(
            R.string.settings_font_jetbrains_mono to EditorFontType.JETBRAINS_MONO,
            R.string.settings_font_fira_code to EditorFontType.FIRA_CODE,
            R.string.settings_font_custom to EditorFontType.CUSTOM
        )
    }

    fun updateSettingsWithSave(newSettings: SettingsData) {
        settingsManager.updateSettings(newSettings)
        settingsManager.saveSettings(context)
        onSettingsChanged(newSettings)
    }

    fun isValidFontFile(path: String): Boolean {
        if (path.isBlank()) return false
        val file = File(path)
        return file.exists() && file.isFile && file.canRead() &&
                (file.extension.equals("ttf", ignoreCase = true) ||
                        file.extension.equals("otf", ignoreCase = true))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {

            item {
                SettingsCardGroup(
                    title = stringResource(R.string.settings_theme_appearance),
                    icon = Icons.Filled.Palette,
                    initiallyExpanded = appearanceExpanded,
                    onExpandedChange = { appearanceExpanded = it }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_theme_color),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ThemeColorOption(
                                    color = Color(0xFF2E6A44),
                                    name = stringResource(R.string.settings_theme_green),
                                    isSelected = currentSettingsState.themeType == ThemeType.GREEN,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(themeType = ThemeType.GREEN))
                                    }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                ThemeColorOption(
                                    color = Color(0xFF36618E),
                                    name = stringResource(R.string.settings_theme_blue),
                                    isSelected = currentSettingsState.themeType == ThemeType.BLUE,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(themeType = ThemeType.BLUE))
                                    }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                ThemeColorOption(
                                    color = Color(0xFF8D4A5A),
                                    name = stringResource(R.string.settings_theme_pink),
                                    isSelected = currentSettingsState.themeType == ThemeType.PINK,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(themeType = ThemeType.PINK))
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_theme_mode),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                DarkModeOption(
                                    icon = Icons.Filled.Settings,
                                    name = stringResource(R.string.settings_theme_follow_system),
                                    isSelected = currentSettingsState.darkMode == DarkMode.FOLLOW_SYSTEM,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(darkMode = DarkMode.FOLLOW_SYSTEM))
                                    }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                DarkModeOption(
                                    icon = Icons.Filled.LightMode,
                                    name = stringResource(R.string.settings_theme_light),
                                    isSelected = currentSettingsState.darkMode == DarkMode.LIGHT,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(darkMode = DarkMode.LIGHT))
                                    }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                DarkModeOption(
                                    icon = Icons.Filled.DarkMode,
                                    name = stringResource(R.string.settings_theme_dark),
                                    isSelected = currentSettingsState.darkMode == DarkMode.DARK,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(darkMode = DarkMode.DARK))
                                    }
                                )
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )

                        SettingsListItem(
                            title = stringResource(R.string.settings_dynamic_color),
                            subtitle = stringResource(R.string.settings_dynamic_color_desc),
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = currentSettingsState.dynamicColor,
                                    onCheckedChange = {
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(dynamicColor = it)
                                        )
                                    }
                                )
                            },
                            onClick = {
                                updateSettingsWithSave(currentSettingsState.copy(dynamicColor = !currentSettingsState.dynamicColor))
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_shape_corner),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        ShapeSizeSelector(
                            selectedIndex = currentSettingsState.shapeSizeIndex,
                            onIndexSelected = { newIndex ->
                                updateSettingsWithSave(currentSettingsState.copy(shapeSizeIndex = newIndex))
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_font_size),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        FontSizeSelector(
                            currentScale = currentSettingsState.fontSizeScale,
                            onScaleSelected = { newScale ->
                                updateSettingsWithSave(currentSettingsState.copy(fontSizeScale = newScale))
                            }
                        )
                    }
                    
HorizontalDivider(
    modifier = Modifier.padding(vertical = 4.dp),
    thickness = 0.5.dp,
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
)

Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
    Text(
        text = stringResource(R.string.settings_app_language),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
        selected = currentSettingsState.languageTag == "zh",
        onClick = {
            SettingsManager.setAppLanguage(context, "zh")
        },
        label = { Text(stringResource(R.string.language_chinese)) }
    )
    
    FilterChip(
        selected = currentSettingsState.languageTag == "en",
        onClick = {
            SettingsManager.setAppLanguage(context, "en")
        },
        label = { Text(stringResource(R.string.language_english)) }
    )
    }
}

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_font_family),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        ExposedDropdownMenuBox(
                            expanded = fontMenuExpanded,
                            onExpandedChange = { fontMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when (currentSettingsState.fontFamilyType) {
                                    FontFamilyType.DEFAULT -> stringResource(R.string.settings_font_default)
                                    FontFamilyType.SANS_SERIF -> stringResource(R.string.settings_font_sans_serif)
                                    FontFamilyType.SERIF -> stringResource(R.string.settings_font_serif)
                                    FontFamilyType.MONOSPACE -> stringResource(R.string.settings_font_monospace)
                                    FontFamilyType.JOSEFIN_SANS -> stringResource(R.string.settings_font_josefin_sans)
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.settings_select_font_family)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontMenuExpanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = fontMenuExpanded,
                                onDismissRequest = { fontMenuExpanded = false }
                            ) {
                                fontFamilyOptions.forEach { (nameRes, type) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(nameRes),
                                                fontWeight = if (currentSettingsState.fontFamilyType == type)
                                                    FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            updateSettingsWithSave(
                                                currentSettingsState.copy(fontFamilyType = type)
                                            )
                                            fontMenuExpanded = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingsCardGroup(
                    title = stringResource(R.string.settings_app_icon),
                    icon = Icons.Filled.Apps,
                    initiallyExpanded = appIconExpanded,
                    onExpandedChange = { appIconExpanded = it }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_select_app_icon),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val currentIcon = remember {
                            IconManager.getCurrentIcon(context)
                        }

                        AppIconGrid(
                            selectedIcon = currentIcon,
                            onIconSelected = { newIcon: IconManager.AppIcon ->
                                scope.launch {
                                    try {
                                        IconManager.switchAppIcon(context, newIcon)

                                        val newSettings = currentSettings.copy(
                                            selectedAppIcon = newIcon
                                        )
                                        SettingsManager.updateSettings(newSettings)
                                        SettingsManager.saveSettings(context)
                                        onSettingsChanged(newSettings)

                                        toast.showToast(context.getString(R.string.settings_icon_restart_to_effect))
                                    } catch (e: Exception) {
                                        toast.showToast(context.getString(R.string.settings_icon_switch_failed, e.message ?: ""))
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                SettingsCardGroup(
                    title = stringResource(R.string.settings_editor_config),
                    icon = Icons.Filled.Edit,
                    initiallyExpanded = editorConfigExpanded,
                    onExpandedChange = { editorConfigExpanded = it }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_editor_font),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        ExposedDropdownMenuBox(
                            expanded = editorFontMenuExpanded,
                            onExpandedChange = { editorFontMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when (currentSettingsState.editorFontType) {
                                    EditorFontType.JETBRAINS_MONO -> stringResource(R.string.settings_font_jetbrains_mono)
                                    EditorFontType.FIRA_CODE -> stringResource(R.string.settings_font_fira_code)
                                    EditorFontType.CUSTOM -> stringResource(R.string.settings_font_custom)
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.settings_select_editor_font)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = editorFontMenuExpanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = editorFontMenuExpanded,
                                onDismissRequest = { editorFontMenuExpanded = false }
                            ) {
                                editorFontOptions.forEach { (nameRes, type) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(nameRes),
                                                fontWeight = if (currentSettingsState.editorFontType == type)
                                                    FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            val newSettings =
                                                currentSettingsState.copy(editorFontType = type)
                                            updateSettingsWithSave(newSettings)
                                            customFontPath = newSettings.customFontPath
                                            editorFontMenuExpanded = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = currentSettingsState.editorFontType == EditorFontType.CUSTOM,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Spacer(modifier = Modifier.height(8.dp))

                                var fontPathText by remember { mutableStateOf(currentSettingsState.customFontPath) }
                                var isFontPathValid by remember {
                                    mutableStateOf(
                                        isValidFontFile(fontPathText)
                                    )
                                }

                                OutlinedTextField(
                                    value = fontPathText,
                                    onValueChange = { newPath ->
                                        fontPathText = newPath
                                        isFontPathValid = isValidFontFile(newPath)

                                        if (isFontPathValid) {
                                            val newSettings =
                                                currentSettingsState.copy(customFontPath = newPath)
                                            updateSettingsWithSave(newSettings)
                                        }
                                    },
                                    label = { Text(stringResource(R.string.settings_font_file_path)) },
                                    placeholder = { Text(stringResource(R.string.settings_font_file_placeholder)) },
                                    trailingIcon = {
                                        if (fontPathText.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    fontPathText = ""
                                                    isFontPathValid = false
                                                    val newSettings =
                                                        currentSettingsState.copy(customFontPath = "")
                                                    updateSettingsWithSave(newSettings)
                                                }
                                            ) {
                                                Icon(Icons.Filled.Clear, stringResource(R.string.clear))
                                            }
                                        }
                                    },
                                    isError = fontPathText.isNotBlank() && !isFontPathValid,
                                    supportingText = {
                                        if (fontPathText.isNotBlank() && !isFontPathValid) {
                                            Text(stringResource(R.string.settings_font_file_invalid))
                                        } else if (isFontPathValid) {
                                            Text(stringResource(R.string.settings_font_file_valid))
                                        } else {
                                            Text(stringResource(R.string.settings_font_file_hint))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    SettingsListItem(
                        title = stringResource(R.string.settings_tab_history),
                        subtitle = stringResource(R.string.settings_tab_history_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.enableTabHistory,
                                onCheckedChange = {
                                    updateSettingsWithSave(
                                        currentSettingsState.copy(enableTabHistory = it)
                                    )
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(currentSettingsState.copy(enableTabHistory = !currentSettingsState.enableTabHistory))
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    SettingsListItem(
                        title = stringResource(R.string.settings_indent_guide),
                        subtitle = stringResource(R.string.settings_indent_guide_desc),
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.FormatIndentIncrease,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.indentGuideEnabled,
                                onCheckedChange = {
                                    updateSettingsWithSave(
                                        currentSettingsState.copy(indentGuideEnabled = it)
                                    )
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(currentSettingsState.copy(indentGuideEnabled = !currentSettingsState.indentGuideEnabled))
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    SettingsListItem(
                        title = stringResource(R.string.settings_completion_case_sensitive),
                        subtitle = stringResource(R.string.settings_completion_case_sensitive_desc),
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.MergeType,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.completionCaseSensitive,
                                onCheckedChange = {
                                    updateSettingsWithSave(
                                        currentSettingsState.copy(completionCaseSensitive = it)
                                    )
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(currentSettingsState.copy(completionCaseSensitive = !currentSettingsState.completionCaseSensitive))
                        }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    SettingsListItem(
                        title = stringResource(R.string.settings_hex_color_highlight),
                        subtitle = stringResource(R.string.settings_hex_color_highlight_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.ColorLens,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.hexColorHighlightEnabled,
                                onCheckedChange = {
                                    updateSettingsWithSave(currentSettingsState.copy(hexColorHighlightEnabled = it))
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(currentSettingsState.copy(hexColorHighlightEnabled = !currentSettingsState.hexColorHighlightEnabled))
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    SettingsListItem(
                        title = stringResource(R.string.settings_smart_sorting),
                        subtitle = stringResource(R.string.settings_smart_sorting_desc),
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.smartSortingEnabled,
                                onCheckedChange = {
                                    updateSettingsWithSave(
                                        currentSettingsState.copy(smartSortingEnabled = it)
                                    )
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(currentSettingsState.copy(smartSortingEnabled = !currentSettingsState.smartSortingEnabled))
                        }
                    )
                    
                    HorizontalDivider(
    modifier = Modifier.padding(vertical = 4.dp),
    thickness = 0.5.dp,
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
)

SettingsListItem(
    title = stringResource(R.string.settings_swipe_gesture),
    subtitle = stringResource(R.string.settings_swipe_gesture_desc),
    leadingIcon = {
        Icon(
            Icons.Filled.Swipe,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    },
    trailingContent = {
        Switch(
            checked = currentSettingsState.enableSwipeGesture,
            onCheckedChange = {
                updateSettingsWithSave(
                    currentSettingsState.copy(enableSwipeGesture = it)
                )
            }
        )
    },
    onClick = {
        updateSettingsWithSave(currentSettingsState.copy(enableSwipeGesture = !currentSettingsState.enableSwipeGesture))
    }
)

                }
            }

            item {
                SettingsCardGroup(
                    title = stringResource(R.string.settings_notification_toast),
                    icon = Icons.Filled.Info,
                    initiallyExpanded = toastSettingsExpanded,
                    onExpandedChange = { toastSettingsExpanded = it }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_toast_position),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = currentSettingsState.toastPosition == ToastPosition.TOP,
                                onClick = {
                                    updateSettingsWithSave(currentSettingsState.copy(toastPosition = ToastPosition.TOP))
                                },
                                label = { Text(stringResource(R.string.settings_toast_top)) }
                            )

                            FilterChip(
                                selected = currentSettingsState.toastPosition == ToastPosition.BOTTOM,
                                onClick = {
                                    updateSettingsWithSave(currentSettingsState.copy(toastPosition = ToastPosition.BOTTOM))
                                },
                                label = { Text(stringResource(R.string.settings_toast_bottom)) }
                            )
                        }

                        SettingsListItem(
                            title = stringResource(R.string.settings_toast_border),
                            subtitle = stringResource(R.string.settings_toast_border_desc),
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = currentSettingsState.toastBorderEnabled,
                                    onCheckedChange = {
                                        updateSettingsWithSave(currentSettingsState.copy(toastBorderEnabled = it))
                                    }
                                )
                            },
                            onClick = {
                                updateSettingsWithSave(currentSettingsState.copy(toastBorderEnabled = !currentSettingsState.toastBorderEnabled))
                            }
                        )
                    }
                }
            }

            item {
                SettingsCardGroup(
                    title = stringResource(R.string.settings_syntax_highlight),
                    icon = Icons.Filled.ColorLens,
                    initiallyExpanded = syntaxHighlightExpanded,
                    onExpandedChange = { syntaxHighlightExpanded = it }
                ) {
                    // 类名高亮
                    SettingsListItem(
                        title = stringResource(R.string.settings_class_name_color),
                        subtitle = stringResource(R.string.settings_class_name_color_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.classNameColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_class_name_color)
                                    colorToEdit = currentSettingsState.classNameColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(classNameColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_class_name_color)
                            colorToEdit = currentSettingsState.classNameColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(classNameColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 局部变量高亮
                    SettingsListItem(
                        title = stringResource(R.string.settings_local_variable_color),
                        subtitle = stringResource(R.string.settings_local_variable_color_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DataArray,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.localVariableColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_local_variable_color)
                                    colorToEdit = currentSettingsState.localVariableColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(localVariableColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_local_variable_color)
                            colorToEdit = currentSettingsState.localVariableColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(localVariableColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 关键词高亮
                    SettingsListItem(
                        title = stringResource(R.string.settings_keyword_color),
                        subtitle = stringResource(R.string.settings_keyword_color_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Keyboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.keywordColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_keyword_color)
                                    colorToEdit = currentSettingsState.keywordColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(keywordColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_keyword_color)
                            colorToEdit = currentSettingsState.keywordColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(keywordColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 函数名高亮
                    SettingsListItem(
                        title = stringResource(R.string.settings_function_name_color),
                        subtitle = stringResource(R.string.settings_function_name_color_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Functions,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.functionNameColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_function_name_color)
                                    colorToEdit = currentSettingsState.functionNameColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(functionNameColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_function_name_color)
                            colorToEdit = currentSettingsState.functionNameColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(functionNameColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 字符串文字高亮
                    SettingsListItem(
                        title = stringResource(R.string.settings_literal_color),
                        subtitle = stringResource(R.string.settings_literal_color_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.FormatQuote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.literalColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_literal_color)
                                    colorToEdit = currentSettingsState.literalColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(literalColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_literal_color)
                            colorToEdit = currentSettingsState.literalColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(literalColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 注释高亮
                    SettingsListItem(
                        title = stringResource(R.string.settings_comment_color),
                        subtitle = stringResource(R.string.settings_comment_color_desc),
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Comment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.commentColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_comment_color)
                                    colorToEdit = currentSettingsState.commentColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(commentColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_comment_color)
                            colorToEdit = currentSettingsState.commentColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(commentColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 选中行背景
                    SettingsListItem(
                        title = stringResource(R.string.settings_selected_line_background),
                        subtitle = stringResource(R.string.settings_selected_line_background_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Highlight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.selectedLineColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_selected_line_background)
                                    colorToEdit = currentSettingsState.selectedLineColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(selectedLineColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_selected_line_background)
                            colorToEdit = currentSettingsState.selectedLineColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(selectedLineColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                updateSettingsWithSave(
                                    currentSettingsState.copy(
                                        classNameColor = Color(0xFF6E81D9),
                                        localVariableColor = Color(0xFFAAAA88),
                                        keywordColor = Color(0xFFFF565E),
                                        functionNameColor = Color(0xFF2196F3),
                                        literalColor = Color(0xFF008080),
                                        commentColor = Color(0xFFA7A8A8),
                                        selectedLineColor = Color(0x1A000000)
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                Icons.Filled.RestartAlt,
                                contentDescription = stringResource(R.string.settings_reset),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_reset_default_colors))
                        }
                    }
                }
            }
        }
    }

    // 颜色选择器对话框
    if (showColorPicker && colorToEdit != null) {
        ColorPickerDialog(
            title = colorPickerTitle,
            initialColor = colorToEdit!!,
            onColorSelected = { newColor ->
                onColorSelected(newColor)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsListItem(
    title: String,
    subtitle: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        enabled = enabled,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 1.dp,
            focusedElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (leadingIcon != null) {
                    Box(modifier = Modifier.size(24.dp)) {
                        leadingIcon()
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )

                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            maxLines = 2
                        )
                    }
                }
            }

            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

@Composable
fun ShapeSizeSelector(
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit
) {
    val shapeOptions = listOf(
        stringResource(R.string.settings_shape_small) to 4.dp,
        stringResource(R.string.settings_shape_medium_small) to 8.dp,
        stringResource(R.string.settings_shape_medium) to 12.dp,
        stringResource(R.string.settings_shape_large) to 16.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        shapeOptions.forEachIndexed { index, (label, size) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .wrapContentHeight()
                    .padding(horizontal = 4.dp)
                    .clip(MaterialTheme.shapes.large)
                    .border(
                        width = if (selectedIndex == index) 2.dp else 0.dp,
                        color = if (selectedIndex == index)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.large
                    )
                    .background(
                        if (selectedIndex == index)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    )
                    .clickable(
                        onClick = { onIndexSelected(index) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(size))
                            .background(
                                MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (selectedIndex == index) 0.5f else 0.2f
                                )
                            )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedIndex == index)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "${size.value.toInt()}dp",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedIndex == index)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun FontSizeSelector(
    currentScale: Float,
    onScaleSelected: (Float) -> Unit
) {
    val fontSizeOptions = listOf(
        stringResource(R.string.settings_font_small) to 0.8f,
        stringResource(R.string.settings_font_medium_small) to 0.9f,
        stringResource(R.string.settings_font_standard) to 1.0f,
        stringResource(R.string.settings_font_large) to 1.1f,
        stringResource(R.string.settings_font_extra_large) to 1.2f
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        fontSizeOptions.forEach { (label, scale) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .wrapContentHeight()
                    .padding(horizontal = 2.dp)
                    .clip(MaterialTheme.shapes.large)
                    .border(
                        width = if (currentScale == scale) 2.dp else 0.dp,
                        color = if (currentScale == scale)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.large
                    )
                    .background(
                        if (currentScale == scale)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    )
                    .clickable(
                        onClick = { onScaleSelected(scale) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "A",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = when (scale) {
                                    0.8f -> 14.sp
                                    0.9f -> 16.sp
                                    1.0f -> 18.sp
                                    1.1f -> 20.sp
                                    1.2f -> 22.sp
                                    else -> 18.sp
                                }
                            ),
                            fontWeight = FontWeight.Bold,
                            color = if (currentScale == scale)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (currentScale == scale) FontWeight.Bold else FontWeight.Medium,
                        color = if (currentScale == scale)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCardGroup(
    title: String,
    icon: ImageVector,
    initiallyExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 0.dp,
                shape = MaterialTheme.shapes.large,
                clip = true
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = {
                            expanded = !expanded
                            onExpandedChange(expanded)
                        }
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.close) else stringResource(R.string.open_drawer),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ThemeColorOption(
    color: Color,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.large
            )
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(color)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DarkModeOption(
    icon: ImageVector,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.large
            )
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}