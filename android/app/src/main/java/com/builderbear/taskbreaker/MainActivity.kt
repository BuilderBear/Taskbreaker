package com.builderbear.taskbreaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.builderbear.taskbreaker.breakdown.BreakdownScreen
import com.builderbear.taskbreaker.breakdown.BreakdownViewModel
import com.builderbear.taskbreaker.settings.SettingsScreen
import com.builderbear.taskbreaker.timer.TimerNotifications
import com.builderbear.taskbreaker.timer.TimerScreen
import com.builderbear.taskbreaker.timer.TimerViewModel
import com.builderbear.taskbreaker.ui.TaskbreakerTheme
import com.builderbear.taskbreaker.ui.TbColors

/** The app's single screen container. Each tool is a "page" shown inside it. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TimerNotifications.createChannel(this)
        setContent { TaskbreakerTheme { TaskbreakerApp() } }
    }
}

private enum class Page(val label: String, val glyph: String) {
    TIMER("Timer", "◔"),
    BREAKDOWN("Breakdown", "☰"),
    SETTINGS("Settings", "⚙"),
}

@Composable
private fun TaskbreakerApp() {
    // Both ViewModels live as long as the app screen does, so the two pages can talk to each other.
    val timerVm: TimerViewModel = viewModel()
    val breakdownVm: BreakdownViewModel = viewModel()
    var page by rememberSaveable { mutableStateOf(Page.TIMER) }

    Scaffold(
        containerColor = TbColors.Bg,
        topBar = { BrandBar() },
        bottomBar = {
            NavigationBar(containerColor = TbColors.Panel) {
                Page.entries.forEach { p ->
                    NavigationBarItem(
                        selected = page == p,
                        onClick = { page = p },
                        icon = { Text(p.glyph, fontSize = 20.sp) },
                        label = { Text(p.label.uppercase(), fontWeight = FontWeight.Bold, letterSpacing = 1.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = TbColors.Lime,
                            indicatorColor = TbColors.Lime,
                            unselectedIconColor = TbColors.Muted,
                            unselectedTextColor = TbColors.Muted,
                        ),
                    )
                }
            }
        },
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
}

@Composable
private fun BrandBar() {
    // A Column so the divider sits under the title instead of on top of it.
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("TASKBREAKER", color = TbColors.Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Text(" //", color = TbColors.Muted, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
        }
        HorizontalDivider(color = TbColors.Line)
    }
}
