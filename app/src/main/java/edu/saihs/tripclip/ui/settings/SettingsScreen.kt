package edu.saihs.tripclip.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import edu.saihs.tripclip.TripClipApplication
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.saihs.tripclip.R
import edu.saihs.tripclip.ui.components.RoundIconButton

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppLanguage.current(context)) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            RoundIconButton(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
                size = 46.dp,
            )
            Text(
                stringResource(R.string.settings_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        SettingsSection(stringResource(R.string.settings_language)) {
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEachIndexed { index, option ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val localized = stringResource(option.localizedName)
                    LanguageRow(
                        title = option.nativeName,
                        subtitle = if (localized == option.nativeName) option.englishName else localized,
                        selected = option == language,
                        onClick = {
                            if (option != language) {
                                language = option
                                AppLanguage.apply(context, option)
                            }
                        },
                    )
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_about)) {
            AboutSection(Modifier.fillMaxWidth().heightIn(min = 260.dp))
        }

        // Debug builds only: fill the journal with the sample Taipei trip.
        val seeder = (context.applicationContext as TripClipApplication).mockDataSeeder
        if (seeder.isAvailable) {
            val scope = rememberCoroutineScope()
            var inserting by remember { mutableStateOf(false) }
            SettingsSection(stringResource(R.string.settings_developer)) {
                ActionRow(
                    title = stringResource(R.string.settings_insert_mock),
                    subtitle = stringResource(R.string.settings_insert_mock_hint),
                    enabled = !inserting,
                    onClick = {
                        inserting = true
                        scope.launch {
                            val added = runCatching { seeder.insert() }.getOrDefault(0)
                            inserting = false
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_insert_mock_done, added),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            title,
            fontSize = 17.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 在這裡放置icon與簡介還有寫你的小心得 */
@Composable
fun AboutSection(modifier: Modifier = Modifier) {
    Box(modifier)
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
    )
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun LanguageRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.padding(12.dp),
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
