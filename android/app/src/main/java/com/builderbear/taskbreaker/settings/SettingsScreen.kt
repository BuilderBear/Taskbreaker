package com.builderbear.taskbreaker.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.builderbear.taskbreaker.ai.ClaudeClient
import com.builderbear.taskbreaker.ui.FieldShape
import com.builderbear.taskbreaker.ui.Panel
import com.builderbear.taskbreaker.ui.PrimaryButton
import com.builderbear.taskbreaker.ui.ScreenTitle
import com.builderbear.taskbreaker.ui.SecondaryButton
import com.builderbear.taskbreaker.ui.SectionLabel
import com.builderbear.taskbreaker.ui.TbColors
import com.builderbear.taskbreaker.ui.tbFieldColors

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(SettingsStore.apiKey(context)) }
    var draft by remember { mutableStateOf(saved) }
    var show by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Settings", "Set up the AI for Break it down.")

        Panel {
            SectionLabel("Claude API key")
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show", color = TbColors.Lime) }
                },
                colors = tbFieldColors(),
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Save", onClick = {
                    SettingsStore.setApiKey(context, draft)
                    saved = draft.trim()
                    draft = saved
                })
                SecondaryButton("Remove key", onClick = {
                    SettingsStore.setApiKey(context, "")
                    saved = ""
                    draft = ""
                })
            }
            Text(
                if (saved.isBlank()) "No key saved. AI breakdown is off, but you can still add steps by hand."
                else "Key saved on this phone.",
                color = if (saved.isBlank()) TbColors.Hot else TbColors.Lime,
                fontSize = 15.sp,
            )
        }

        Panel {
            SectionLabel("What this does")
            Text(
                "When you tap Break it down or Still too big, the task text (and nothing else) is sent to " +
                    "Anthropic's Claude API using your key. Each request costs a small amount on your Anthropic account. " +
                    "Get a key from the Claude Console on Anthropic's website.",
                color = TbColors.Muted,
                fontSize = 15.sp,
            )
            Text(
                "The key is stored only in this app's private storage on this phone, and is left out of phone backups.",
                color = TbColors.Muted,
                fontSize = 15.sp,
            )
            Text("Model: ${ClaudeClient.MODEL}", color = TbColors.Faint, fontSize = 13.sp)
        }
    }
}
