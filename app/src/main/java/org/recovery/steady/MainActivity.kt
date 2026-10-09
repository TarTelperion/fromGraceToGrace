package org.recovery.steady

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.recovery.steady.data.LapseRecord
import org.recovery.steady.data.GoalSettings
import org.recovery.steady.data.ReminderSettings
import org.recovery.steady.domain.calculateRecoveryDuration
import org.recovery.steady.domain.calculateGoalProgress
import org.recovery.steady.domain.SobrietyGoal
import org.recovery.steady.ui.AppState
import org.recovery.steady.ui.AppViewModel
import org.recovery.steady.ui.SteadyTheme
import org.recovery.steady.ui.ThemeColor
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            SteadyTheme(state.preferences.themeColorId) {
                SteadyApp(state = state, viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (text.isNotBlank()) viewModel.importSharedText(text)
    }
}

private enum class AppTab(val label: String) {
    HOME("Today"),
    FEED("Feed"),
    HISTORY("History"),
    SETTINGS("Settings"),
}

@Composable
private fun SteadyApp(state: AppState, viewModel: AppViewModel) {
    var tab by remember { mutableStateOf(AppTab.HOME) }
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Icon(
                                when (item) {
                                    AppTab.HOME -> Icons.Outlined.FavoriteBorder
                                    AppTab.FEED -> Icons.Outlined.AutoStories
                                    AppTab.HISTORY -> Icons.Outlined.History
                                    AppTab.SETTINGS -> Icons.Outlined.Settings
                                },
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                AppTab.HOME -> HomeScreen(
                    state = state,
                    onRecordLapse = viewModel::recordLapse,
                    onSetStart = viewModel::updateCurrentStart,
                    onOpenFeed = { tab = AppTab.FEED },
                )
                AppTab.FEED -> FeedScreen(state)
                AppTab.HISTORY -> HistoryScreen(state.lapses, viewModel::deleteLapse)
                AppTab.SETTINGS -> SettingsScreen(
                    reminder = state.reminder,
                    goalSettings = state.preferences.goals,
                    themeColorId = state.preferences.themeColorId,
                    onUpdateReminder = viewModel::updateReminder,
                    onUpdateGoals = viewModel::updateGoals,
                    onUpdateTheme = viewModel::updateThemeColor,
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: AppState,
    onRecordLapse: (String) -> Unit,
    onSetStart: (Long) -> Unit,
    onOpenFeed: () -> Unit,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val recovery = state.recovery
    val duration = recovery?.let {
        calculateRecoveryDuration(it.startedAtMillis, nowMillis)
    }
    val goalProgress = recovery?.let {
        calculateGoalProgress(it.startedAtMillis, nowMillis, state.preferences.goals.enabledGoalIds)
    }
    val days = duration?.completedDays ?: 0
    val startLabel = recovery?.startedAtMillis?.let(::formatDate) ?: "Starting…"

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Spacer(Modifier.height(18.dp)) }
        item {
            Text("One day at a time", style = MaterialTheme.typography.titleMedium)
        }
        item { DayCircle(days, startLabel, goalProgress?.fraction ?: 0f) }
        item {
            Text(
                when (val goal = goalProgress?.currentGoal) {
                    null -> if (goalProgress?.completedGoals?.isNotEmpty() == true) {
                        "All selected goals reached"
                    } else {
                        "Choose a goal in Settings"
                    }
                    else -> "Next goal: ${goal.label}  •  ${goalProgress.percent}%"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                if (days == 0L) "${duration?.hoursAfterLastDay ?: 0} hours into this new start"
                else "Started $startLabel",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        item {
            OutlinedButton(
                enabled = recovery != null,
                onClick = {
                    recovery?.let { showStartDateTimePicker(context, it.startedAtMillis, onSetStart) }
                },
            ) {
                Text("Adjust start date and time")
            }
        }
        item {
            EncouragementCard("You do not need to carry the whole future today. Choose the next faithful step.")
        }
        item {
            Button(onClick = onOpenFeed, modifier = Modifier.fillMaxWidth()) {
                Text("Open today’s encouragement")
            }
        }
        item {
            TextButton(onClick = { showDialog = true }) {
                Text("Record a lapse")
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }

    if (showDialog) {
        RecordLapseDialog(
            onDismiss = { showDialog = false },
            onConfirm = {
                onRecordLapse(it)
                showDialog = false
            },
        )
    }
}

@Composable
private fun DayCircle(days: Long, startLabel: String, goalFraction: Float) {
    val progress by animateFloatAsState(targetValue = goalFraction, label = "goal progress")
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .size(260.dp)
            .semantics {
                contentDescription = "$days completed days since $startLabel, ${(progress * 100).toInt()} percent toward the next goal"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 18.dp.toPx()
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(width / 2, width / 2),
                size = Size(size.width - width, size.height - width),
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
            drawArc(
                color = primary,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(width / 2, width / 2),
                size = Size(size.width - width, size.height - width),
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(days.toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.SemiBold)
            Text(if (days == 1L) "day" else "days", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun RecordLapseDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Begin a new start?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your completed interval stays in history. A note is optional and remains on this phone.")
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Private note") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(note) }) { Text("Begin new start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FeedScreen(state: AppState) {
    val context = LocalContext.current
    val builtIn = listOf(
        "Take one small step. You do not have to solve the whole day at once.",
        "A difficult moment does not cancel the progress that came before it.",
        "Reach outward instead of withdrawing. Connection can interrupt the pattern.",
        "Be honest without being cruel to yourself.",
        "Rest, food, movement, prayer, and conversation are practical forms of care.",
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Today",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 22.dp, bottom = 4.dp),
            )
        }
        item {
            ResourceLinkCard(
                title = "Addiction Recovery Program",
                body = "Open the official Church recovery guide.",
                onOpen = { context.openUrl(ARP_URL) },
            )
        }
        state.preferences.dailyScripture?.let { scripture ->
            item {
                ResourceLinkCard(
                    title = "Scripture for today • ${scripture.reference}",
                    body = scripture.text,
                    onOpen = { context.openUrl(scripture.sourceUrl) },
                )
            }
            item {
                Text(
                    "Updated daily from the community-run Open Scripture API and cached for offline use.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(state.resources, key = { "saved-${it.id}" }) { resource ->
            ResourceLinkCard(
                title = resource.title,
                body = resource.body,
                onOpen = resource.sourceUrl?.let { url -> { context.openUrl(url) } },
            )
        }
        items(builtIn) { EncouragementCard(it) }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun EncouragementCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(22.dp),
        )
    }
}

@Composable
private fun ResourceLinkCard(title: String, body: String, onOpen: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (onOpen != null) {
                TextButton(onClick = onOpen) { Text("Open source") }
            }
        }
    }
}

@Composable
private fun HistoryScreen(lapses: List<LapseRecord>, onDelete: (Long) -> Unit) {
    var pendingDelete by remember { mutableStateOf<LapseRecord?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "History",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 22.dp),
            )
        }
        if (lapses.isEmpty()) {
            item { Text("Past intervals will appear here. Nothing is erased when you begin again.") }
        }
        items(lapses, key = { it.id }) { lapse ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New start on ${formatDate(lapse.occurredAtMillis)}", fontWeight = FontWeight.SemiBold)
                    Text("Previous start: ${formatDate(lapse.priorStartMillis)}")
                    if (lapse.note.isNotBlank()) Text(lapse.note)
                    TextButton(onClick = { pendingDelete = lapse }) { Text("Delete this record") }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }

    pendingDelete?.let { lapse ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this record?") },
            text = {
                Text("If this is your most recent record, the prior start date will be restored. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(lapse.id)
                        pendingDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsScreen(
    reminder: ReminderSettings,
    goalSettings: GoalSettings,
    themeColorId: String,
    onUpdateReminder: (ReminderSettings) -> Unit,
    onUpdateGoals: (GoalSettings) -> Unit,
    onUpdateTheme: (String) -> Unit,
) {
    val context = LocalContext.current
    val reminderPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onUpdateReminder(reminder.copy(enabled = true))
    }
    val goalPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onUpdateGoals(goalSettings.copy(notificationsEnabled = true))
    }
    var localHour by remember(reminder.hour) { mutableIntStateOf(reminder.hour) }
    var localMinute by remember(reminder.minute) { mutableIntStateOf(reminder.minute) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 22.dp),
            )
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Color theme", fontWeight = FontWeight.SemiBold)
                    Text("Choose a calm accent color. Light and dark mode still follow your phone.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ThemeColor.entries.forEach { option ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(if (themeColorId == option.id) 48.dp else 42.dp)
                                        .clip(CircleShape)
                                        .background(option.preview)
                                        .clickable { onUpdateTheme(option.id) }
                                        .semantics {
                                            contentDescription = "${option.label} theme${if (themeColorId == option.id) ", selected" else ""}"
                                        },
                                )
                                Text(option.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sobriety goals", fontWeight = FontWeight.SemiBold)
                    Text("Select the milestones you want to work toward. At least one stays active.")
                    SobrietyGoal.entries.forEach { goal ->
                        val checked = goal.id in goalSettings.enabledGoalIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = if (checked) {
                                        goalSettings.enabledGoalIds - goal.id
                                    } else {
                                        goalSettings.enabledGoalIds + goal.id
                                    }
                                    if (updated.isNotEmpty()) {
                                        onUpdateGoals(goalSettings.copy(enabledGoalIds = updated))
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                            )
                            Text(goal.label)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Milestone notifications", fontWeight = FontWeight.SemiBold)
                            Text("A private encouragement when you reach a selected goal.")
                        }
                        Switch(
                            checked = goalSettings.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    goalPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onUpdateGoals(goalSettings.copy(notificationsEnabled = enabled))
                                }
                            },
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Gentle daily reminder", fontWeight = FontWeight.SemiBold)
                            Text("Private wording; no recovery details on the lock screen.")
                        }
                        Switch(
                            checked = reminder.enabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    reminderPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onUpdateReminder(reminder.copy(enabled = enabled))
                                }
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    localHour = hour
                                    localMinute = minute
                                    onUpdateReminder(reminder.copy(hour = hour, minute = minute))
                                },
                                localHour,
                                localMinute,
                                false,
                            ).show()
                        },
                    ) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                        Text("  Reminder at ${formatTime(localHour, localMinute)}")
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Local by design", fontWeight = FontWeight.SemiBold)
                    Text("Steady has no account, advertising, or analytics. Internet access is used only for the daily scripture; recovery notes and progress stay in private app storage and are excluded from backup and device transfer.")
                }
            }
        }
        item {
            Text(
                "Steady is a personal reflection and habit-support tool. It is not medical care or an emergency service.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

private fun formatTime(hour: Int, minute: Int): String {
    val suffix = if (hour >= 12) "PM" else "AM"
    val hour12 = when (val normalized = hour % 12) { 0 -> 12; else -> normalized }
    return "%d:%02d %s".format(hour12, minute, suffix)
}

private fun android.content.Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun showStartDateTimePicker(
    context: android.content.Context,
    currentMillis: Long,
    onSelected: (Long) -> Unit,
) {
    val current = Instant.ofEpochMilli(currentMillis).atZone(ZoneId.systemDefault())
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val chosen = ZonedDateTime.of(
                        year,
                        month + 1,
                        day,
                        hour,
                        minute,
                        0,
                        0,
                        ZoneId.systemDefault(),
                    ).toInstant().toEpochMilli()
                    onSelected(chosen.coerceAtMost(System.currentTimeMillis()))
                },
                current.hour,
                current.minute,
                false,
            ).show()
        },
        current.year,
        current.monthValue - 1,
        current.dayOfMonth,
    ).apply {
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}

private const val ARP_URL =
    "https://www.churchofjesuschrist.org/study/manual/addiction-recovery-program-a-guide-to-addiction-recovery-and-healing"
