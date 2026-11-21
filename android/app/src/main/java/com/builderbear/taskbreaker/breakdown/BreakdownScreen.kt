package com.builderbear.taskbreaker.breakdown

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.builderbear.taskbreaker.ai.ClaudeClient
import com.builderbear.taskbreaker.ui.ChakraPetch
import com.builderbear.taskbreaker.ui.FieldShape
import com.builderbear.taskbreaker.ui.Panel
import com.builderbear.taskbreaker.ui.PrimaryButton
import com.builderbear.taskbreaker.ui.ScreenTitle
import com.builderbear.taskbreaker.ui.SecondaryButton
import com.builderbear.taskbreaker.ui.SectionLabel
import com.builderbear.taskbreaker.ui.TbColors
import com.builderbear.taskbreaker.ui.TbType
import com.builderbear.taskbreaker.ui.hudShape
import com.builderbear.taskbreaker.ui.limeGlow
import com.builderbear.taskbreaker.ui.tbFieldColors

@Composable
fun BreakdownScreen(vm: BreakdownViewModel, onStartTimer: (index: Int, step: Step) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Break it down", "Stuck? Name the task. Get one small move.")
        TaskForm(s, vm)
        ObjectiveCard(s, vm, onStartTimer)
        StepLog(s, vm, onStartTimer)
    }
}

@Composable
private fun TaskForm(s: BreakdownState, vm: BreakdownViewModel) {
    var task by rememberSaveable { mutableStateOf("") }
    // Web-style labels: small uppercase text above the field, rather than Android's floating label.
    val fieldLabel = TbType.label.copy(fontSize = 14.sp, letterSpacing = 0.06.em)
    Panel {
        SectionLabel("What are you trying to start?", style = fieldLabel)
        OutlinedTextField(
            value = task,
            onValueChange = { task = it },
            placeholder = { Text("e.g. Do my expense report") },
            singleLine = true,
            colors = tbFieldColors(),
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("How stuck?", style = fieldLabel)
            Spacer(Modifier.weight(1f))
            SectionLabel(ClaudeClient.STUCK_LEVELS[s.level - 1], color = TbColors.Lime, style = TbType.button)
        }
        Slider(
            value = s.level.toFloat(),
            onValueChange = { vm.setLevel(Math.round(it)) },
            valueRange = 1f..5f,
            steps = 3, // 3 stops between the ends = 5 positions
            colors = SliderDefaults.colors(
                thumbColor = TbColors.Lime,
                activeTrackColor = TbColors.Lime,
                inactiveTrackColor = TbColors.Line,
                activeTickColor = Color.Black,
                inactiveTickColor = TbColors.Muted,
            ),
        )
        PrimaryButton(
            if (s.busy) "Working…" else "Break it down",
            onClick = { vm.breakDown(task) },
            enabled = !s.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        if (s.status.isNotBlank()) {
            Text(s.status, color = if (s.busy) TbColors.Lime else TbColors.Muted, fontSize = 15.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ObjectiveCard(s: BreakdownState, vm: BreakdownViewModel, onStartTimer: (Int, Step) -> Unit) {
    val n = s.steps.size
    val done = s.steps.count { it.done }
    val c = s.current
    // An active objective gets the dim-lime border, glowing left bar and inner glow (the web's .obj card).
    Panel(borderColor = if (n == 0) TbColors.Line else TbColors.Lime2, accent = n > 0) {
        when {
            n == 0 -> {
                SectionLabel("No objective")
                BigText("Type a task above and hit Break it down.")
            }
            c < 0 -> {
                ObjectiveLabel("Mission complete", " · $done/$n")
                Progress(done, n)
                BigText("Every step done. That counts.")
            }
            else -> {
                val step = s.steps[c]
                val context = if (s.task.isNotBlank()) " · ${s.task}" else ""
                ObjectiveLabel("Current objective", " · step ${c + 1} of $n$context")
                Progress(done, n)
                BigText(step.text)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Done", onClick = { vm.markDone(c) })
                    SecondaryButton("Timer ${step.minutes} min", onClick = { onStartTimer(c, step) })
                    SecondaryButton("Still too big", onClick = { vm.split(c) }, enabled = !s.busy)
                }
            }
        }
    }
}

/** "CURRENT OBJECTIVE · step 2 of 5": the heading in lime, the details in grey (like the web). */
@Composable
private fun ObjectiveLabel(heading: String, details: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = TbColors.Lime)) { append(heading.uppercase()) }
            withStyle(SpanStyle(color = TbColors.Muted)) { append(details.uppercase()) }
        },
        style = TbType.label,
    )
}

@Composable
private fun BigText(text: String) {
    Text(text, color = TbColors.Ink, style = TbType.objective)
}

/** Thin progress bar with a glowing lime fill. */
@Composable
private fun Progress(done: Int, total: Int) {
    val fraction by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, label = "progress")
    Box(Modifier.fillMaxWidth().height(6.dp).background(TbColors.Track)) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .limeGlow(RectangleShape)
                    .background(TbColors.Lime),
            )
        }
    }
}

@Composable
private fun StepLog(s: BreakdownState, vm: BreakdownViewModel, onStartTimer: (Int, Step) -> Unit) {
    var newStep by rememberSaveable { mutableStateOf("") }
    val c = s.current
    Panel {
        SectionLabel("Step log", style = TbType.label.copy(fontSize = 15.sp))
        Column {
            s.steps.forEachIndexed { i, step ->
                StepRow(
                    step = step,
                    isCurrent = i == c,
                    onToggle = { vm.toggleDone(i) },
                    onTimer = { onStartTimer(i, step) },
                    onDelete = { vm.delete(i) },
                )
                HorizontalDivider(color = TbColors.Line)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = newStep,
                onValueChange = { newStep = it },
                placeholder = { Text("Add a step yourself") },
                singleLine = true,
                colors = tbFieldColors(),
                shape = FieldShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.addStep(newStep); newStep = "" }),
                modifier = Modifier.weight(1f),
            )
            SecondaryButton("Add", onClick = { vm.addStep(newStep); newStep = "" })
        }
        SecondaryButton("Clear all", onClick = vm::clearAll, compact = true)
        if (s.example) {
            Text("Example steps shown. Type your own task above to replace them.", color = TbColors.Muted, fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun StepRow(step: Step, isCurrent: Boolean, onToggle: () -> Unit, onTimer: () -> Unit, onDelete: () -> Unit) {
    // Phone layout from the web CSS: check box on the left; step text with its buttons underneath on the right.
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Check box in the same slanted button shape; fills lime when done.
        OutlinedButton(
            onClick = onToggle,
            modifier = Modifier.size(44.dp),
            shape = hudShape(8.dp),
            border = BorderStroke(1.dp, if (step.done) TbColors.Lime else TbColors.Line),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (step.done) TbColors.Lime else TbColors.Panel2,
                contentColor = Color.Black,
            ),
            contentPadding = PaddingValues(0.dp),
        ) { Text(if (step.done) "✓" else "", style = TbType.button.copy(fontSize = 16.sp)) }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Tapping the step text starts its timer, like the web version. Minutes sit inline in small caps.
            Text(
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = when {
                                step.done -> TbColors.Faint
                                isCurrent -> TbColors.Lime
                                else -> TbColors.Ink
                            },
                            textDecoration = if (step.done) TextDecoration.LineThrough else null,
                        ),
                    ) { append(step.text) }
                    withStyle(SpanStyle(color = TbColors.Muted, fontFamily = ChakraPetch, fontSize = 13.sp)) {
                        append("\u00A0\u00A0${step.minutes}\u00A0min") // non-breaking spaces keep "5 min" together
                    }
                },
                fontSize = 17.sp,
                lineHeight = 23.sp,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onTimer).padding(vertical = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SecondaryButton("Timer", onClick = onTimer, compact = true)
                SecondaryButton("✕", onClick = onDelete, compact = true)
            }
        }
    }
}
