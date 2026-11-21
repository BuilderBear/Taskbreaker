package com.builderbear.taskbreaker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Small building blocks shared by every screen, so buttons and cards look the same everywhere. */

private val buttonShape = CutCornerShape(8.dp)
private val minTap = 48.dp // comfortably above the 44 px minimum tap target

/** Lime, filled. One per area, for the main action. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = minTap),
        enabled = enabled,
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = TbColors.Lime,
            contentColor = Color.Black,
            disabledContainerColor = TbColors.Lime2.copy(alpha = 0.5f),
            disabledContentColor = Color.Black,
        ),
    ) { ButtonText(text) }
}

/** Dark with a thin border. `selected` lights it up lime (used for the active timer preset). */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = minTap),
        enabled = enabled,
        shape = buttonShape,
        border = BorderStroke(1.dp, if (selected) TbColors.Lime else TbColors.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = TbColors.Panel2,
            contentColor = if (selected) TbColors.Lime else TbColors.Ink,
        ),
        contentPadding = if (compact) PaddingValues(horizontal = 12.dp) else ButtonDefaults.ContentPadding,
    ) { ButtonText(text) }
}

@Composable
private fun ButtonText(text: String) {
    Text(text.uppercase(), fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 14.sp)
}

/** A card with angled corners. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    borderColor: Color = TbColors.Line,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CutCornerShape(16.dp),
        color = TbColors.Panel,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title.uppercase(),
            color = TbColors.Ink,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Text(subtitle, color = TbColors.Muted, fontSize = 16.sp)
    }
}

/** Small uppercase heading, like a HUD label. */
@Composable
fun SectionLabel(text: String, color: Color = TbColors.Muted) {
    Text(
        text.uppercase(),
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
fun tbFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TbColors.Lime,
    unfocusedBorderColor = TbColors.Line,
    cursorColor = TbColors.Lime,
    focusedTextColor = TbColors.Ink,
    unfocusedTextColor = TbColors.Ink,
    focusedContainerColor = TbColors.Field,
    unfocusedContainerColor = TbColors.Field,
    focusedLabelColor = TbColors.Lime,
    unfocusedLabelColor = TbColors.Muted,
    focusedPlaceholderColor = TbColors.Faint,
    unfocusedPlaceholderColor = TbColors.Faint,
)
