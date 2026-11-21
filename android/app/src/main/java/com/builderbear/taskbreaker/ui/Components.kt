package com.builderbear.taskbreaker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Small building blocks shared by every screen, so buttons and cards look the same everywhere. */

/**
 * The web version's clip-path shape: only the top-left and bottom-right corners are cut,
 * which gives the slanted "HUD" look.
 */
fun hudShape(cut: Dp): Shape = CutCornerShape(topStart = cut, topEnd = 0.dp, bottomEnd = cut, bottomStart = 0.dp)

val ButtonShape = hudShape(8.dp)
val PanelShape = hudShape(16.dp)

private val minTap = 48.dp // comfortably above the 44 px minimum tap target

/**
 * The lime glow from the web CSS (`box-shadow: 0 0 6px lime/55%, 0 0 22px lime/25%`):
 * a tight bright halo plus a wide soft one.
 */
fun Modifier.limeGlow(shape: Shape, strength: Float = 1f): Modifier = this
    .dropShadow(shape, Shadow(radius = 22.dp, color = TbColors.Lime.copy(alpha = 0.25f * strength)))
    .dropShadow(shape, Shadow(radius = 6.dp, color = TbColors.Lime.copy(alpha = 0.55f * strength)))

/** Lime, filled, glowing. One per area, for the main action. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Button(
        onClick = onClick,
        modifier = modifier
            .then(if (enabled) Modifier.limeGlow(ButtonShape) else Modifier)
            .heightIn(min = minTap),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pressed) TbColors.LimeHover else TbColors.Lime,
            contentColor = Color.Black,
            disabledContainerColor = TbColors.Lime.copy(alpha = 0.5f),
            disabledContentColor = Color.Black,
        ),
        border = BorderStroke(1.dp, if (enabled) TbColors.Lime else TbColors.Lime.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(horizontal = 18.dp),
        interactionSource = interaction,
    ) { ButtonText(text) }
}

/**
 * Dark with a thin border; turns lime while pressed (the web's hover).
 * `selected` lights it up permanently with an inner lime ring (the active timer preset).
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lit = selected || pressed
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = if (compact) 40.dp else minTap)
            .then(if (selected) Modifier.innerShadow(ButtonShape, Shadow(radius = 1.dp, spread = 1.dp, color = TbColors.Lime)) else Modifier),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, if (lit) TbColors.Lime else TbColors.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = TbColors.Panel2,
            contentColor = if (lit) TbColors.Lime else TbColors.Ink,
            disabledContainerColor = TbColors.Panel2,
            disabledContentColor = TbColors.Muted,
        ),
        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 18.dp),
        interactionSource = interaction,
    ) { ButtonText(text, small = compact) }
}

@Composable
private fun ButtonText(text: String, small: Boolean = false) {
    Text(text.uppercase(), style = TbType.button.copy(fontSize = if (small) 13.sp else 15.sp))
}

/**
 * A card: dark gradient, thin border, slanted corners (the web's `.panel`).
 * `accent` adds the objective card's glowing lime bar down the left edge and a faint inner glow.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    borderColor: Color = TbColors.Line,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Brush.verticalGradient(listOf(TbColors.Panel, TbColors.PanelEnd)))
            .then(if (accent) Modifier.innerShadow(PanelShape, Shadow(radius = 40.dp, color = TbColors.Lime.copy(alpha = 0.06f))) else Modifier)
            .border(1.dp, borderColor, PanelShape),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        if (accent) {
            // Glowing 4dp lime bar on the left, starting below the cut corner (like the web's .obj::before).
            // matchParentSize makes this layer exactly as tall as the card's content.
            Box(Modifier.matchParentSize().padding(top = 16.dp)) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .limeGlow(RectangleShape)
                        .background(TbColors.Lime),
                )
            }
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String, centered: Boolean = false) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(title.uppercase(), style = TbType.title, color = TbColors.Ink, textAlign = align)
        Text(subtitle, color = TbColors.Muted, textAlign = align)
    }
}

/** Small uppercase heading, like a HUD label. */
@Composable
fun SectionLabel(text: String, color: Color = TbColors.Muted, style: TextStyle = TbType.label) {
    Text(text.uppercase(), color = color, style = style)
}

/** Square, dark text fields with a lime border when focused (the web inputs have no cut corners). */
val FieldShape: Shape = RectangleShape

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
