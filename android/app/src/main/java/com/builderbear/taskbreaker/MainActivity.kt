package com.builderbear.taskbreaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.builderbear.taskbreaker.breakdown.BreakdownScreen
import com.builderbear.taskbreaker.breakdown.BreakdownViewModel
import com.builderbear.taskbreaker.settings.SettingsScreen
import com.builderbear.taskbreaker.timer.TimerNotifications
import com.builderbear.taskbreaker.timer.TimerScreen
import com.builderbear.taskbreaker.timer.TimerViewModel
import com.builderbear.taskbreaker.timer.formatTime
import com.builderbear.taskbreaker.ui.ChakraPetch
import com.builderbear.taskbreaker.ui.ScanlineOverlay
import com.builderbear.taskbreaker.ui.TaskbreakerTheme
import com.builderbear.taskbreaker.ui.TbColors
import com.builderbear.taskbreaker.ui.TbGlow
import com.builderbear.taskbreaker.ui.TbType
import com.builderbear.taskbreaker.ui.hudBackground
import com.builderbear.taskbreaker.ui.hudShape
import com.builderbear.taskbreaker.ui.limeGlow
import kotlinx.coroutines.delay

/** The app's single screen container. Each tool is a "page" shown inside it. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so always use light status-bar icons (clock, battery) and a clear bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        TimerNotifications.createChannel(this)
        setContent { TaskbreakerTheme { TaskbreakerApp() } }
    }
}

private enum class Page(val label: String) {
    TIMER("Timer"),
    BREAKDOWN("Breakdown"),
    SETTINGS("Settings"),
}

// Translucent bar color, like the web's rgba(7,8,10,.88) sticky header.
private val BarColor = TbColors.Bg.copy(alpha = 0.88f)

@Composable
private fun TaskbreakerApp() {
    // Both ViewModels live as long as the app screen does, so the two pages can talk to each other.
    val timerVm: TimerViewModel = viewModel()
    val breakdownVm: BreakdownViewModel = viewModel()
    var page by rememberSaveable { mutableStateOf(Page.TIMER) }

    // Background (grid + glow) underneath, the app in the middle, scanlines on top.
    Box(Modifier.fillMaxSize().hudBackground()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { BrandBar() },
            bottomBar = { TabBar(page, onSelect = { page = it }, timerVm = timerVm) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    Page.TIMER -> TimerScreen(
                        vm = timerVm,
                        isStepOpen = breakdownVm::isOpen,
                        onLinkedStepDone = { i ->
                            breakdownVm.markDone(i)
                            timerVm.clearLink()
                            page = Page.BREAKDOWN
                        },
                        onBackToSteps = { page = Page.BREAKDOWN },
                    )
                    Page.BREAKDOWN -> BreakdownScreen(
                        vm = breakdownVm,
                        onStartTimer = { i, step ->
                            timerVm.setTimer(step.minutes, step.text, linkedStep = i)
                            timerVm.start()
                            page = Page.TIMER
                        },
                    )
                    Page.SETTINGS -> SettingsScreen()
                }
            }
        }
        ScanlineOverlay()
    }
}

/** "TASKBREAKER //" logo with the lime glow. */
@Composable
private fun BrandBar() {
    Column(Modifier.fillMaxWidth().background(BarColor).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "TASKBREAKER",
                color = TbColors.Lime,
                style = TbType.title.copy(fontSize = 22.sp, lineHeight = 22.sp, letterSpacing = 0.06.em, shadow = TbGlow.text),
            )
            Text(
                " //",
                color = TbColors.Muted,
                style = TbType.title.copy(fontSize = 22.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
            )
        }
        HorizontalDivider(color = TbColors.Line)
    }
}

/** Bottom tabs styled like the web's top tabs: angled buttons, the active one lime and glowing. */
@Composable
private fun TabBar(page: Page, onSelect: (Page) -> Unit, timerVm: TimerViewModel) {
    val timer by timerVm.state.collectAsStateWithLifecycle()
    // While the timer runs, show the countdown on its tab (like the web's mini readout).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer.running) {
        while (timer.running) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    Column(Modifier.fillMaxWidth().background(BarColor).navigationBarsPadding()) {
        HorizontalDivider(color = TbColors.Line)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Page.entries.forEach { p ->
                val mini = if (p == Page.TIMER && timer.running) " " + formatTime(timer.remainingAt(now)) else ""
                HudTab(
                    text = p.label + mini,
                    selected = page == p,
                    onClick = { onSelect(p) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HudTab(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = hudShape(10.dp)
    Box(
        modifier
            .then(if (selected) Modifier.limeGlow(shape) else Modifier)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) TbColors.Lime else TbColors.Panel)
            .border(1.dp, if (selected) TbColors.Lime else TbColors.Line, shape)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            color = if (selected) Color.Black else TbColors.Muted,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 0.08.em,
            maxLines = 1,
        )
    }
}
