package com.shifttac.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shifttac.game.*
import com.shifttac.ui.theme.*
import kotlinx.coroutines.delay

// ─── Demo Script ───────────────────────────────────────────────────────────────

sealed class DemoStep(val delay: Long) {
    data class Place(val delayMs: Long, val r: Int, val c: Int, val p: String) : DemoStep(delayMs)
    data class Hint(val delayMs: Long, val kind: String, val idx: Int) : DemoStep(delayMs)
    data class Shift(val delayMs: Long, val kind: String, val idx: Int, val dir: Int) : DemoStep(delayMs)
    data class Win(val delayMs: Long, val line: List<Pair<Int, Int>>, val winner: String) : DemoStep(delayMs)
    data class Reset(val delayMs: Long) : DemoStep(delayMs)
}

val DEMO_SCRIPT = listOf(
    DemoStep.Place(500, 2, 0, "X"),
    DemoStep.Place(380, 0, 1, "O"),
    DemoStep.Place(380, 2, 1, "X"),
    DemoStep.Place(380, 3, 4, "O"),
    DemoStep.Place(380, 1, 2, "X"),
    DemoStep.Place(380, 2, 2, "O"),
    DemoStep.Place(380, 2, 3, "X"),
    DemoStep.Place(380, 4, 0, "O"),
    DemoStep.Place(380, 2, 4, "X"),
    DemoStep.Hint(750, "col", 2),
    DemoStep.Shift(900, "col", 2, 1),
    DemoStep.Win(500, listOf(2 to 0, 2 to 1, 2 to 2, 2 to 3, 2 to 4), "X"),
    DemoStep.Reset(2200),
)

// ─── Demo Board ────────────────────────────────────────────────────────────────

@Composable
fun DemoBoard() {
    val cellSize = 30.dp
    val gap = 4.dp
    val pad = 8.dp

    var board by remember { mutableStateOf(emptyBoard()) }
    var lastPlaced by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var winInfo by remember { mutableStateOf<WinInfo?>(null) }
    var hintAxis by remember { mutableStateOf<Pair<String, Int>?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            for (step in DEMO_SCRIPT) {
                delay(step.delay)
                when (step) {
                    is DemoStep.Place -> {
                        val nb = cloneBoard(board)
                        nb[step.r][step.c] = step.p
                        board = nb
                        lastPlaced = step.r to step.c
                        hintAxis = null
                    }
                    is DemoStep.Hint -> hintAxis = step.kind to step.idx
                    is DemoStep.Shift -> {
                        hintAxis = null
                        board = if (step.kind == "row") shiftRow(board, step.idx, step.dir)
                                else shiftCol(board, step.idx, step.dir)
                    }
                    is DemoStep.Win -> {
                        winInfo = WinInfo(step.winner, step.line)
                        lastPlaced = null
                    }
                    is DemoStep.Reset -> {
                        board = emptyBoard()
                        lastPlaced = null
                        winInfo = null
                        hintAxis = null
                    }
                }
            }
            delay(600)
        }
    }

    val boardPx = cellSize * 5 + gap * 4 + pad * 2

    Box(
        modifier = Modifier
            .size(boardPx)
            .clip(RoundedCornerShape(14.dp))
            .background(Bg1)
            .border(1.dp, GridColor, RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(pad),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            for (r in 0 until GRID_SIZE) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until GRID_SIZE) {
                        val v = board[r][c]
                        val isWin = winInfo?.line?.contains(r to c) == true
                        val isLast = lastPlaced == r to c
                        val colorVar = if (v == "X") XColor else if (v == "O") OColor else null
                        val softVar = if (v == "X") XSoft else if (v == "O") OSoft else Color.Transparent

                        val borderColor = when {
                            isWin -> colorVar ?: GridColor
                            isLast -> colorVar ?: GridColor
                            else -> GridColor
                        }
                        val borderWidth = if (isWin || isLast) 1.5.dp else 1.dp
                        val cellGlow = if (isWin && colorVar != null) colorVar.copy(alpha = 0.4f) else Color.Transparent

                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (v != null) softVar else CellBg)
                                .border(borderWidth, borderColor, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (v != null) {
                                PlayerGlyph(player = v, size = cellSize * 0.55f, glow = isWin || isLast)
                            }
                        }
                    }
                }
            }
        }

        // Hint highlight overlay
        hintAxis?.let { (kind, idx) ->
            val hintAlpha by rememberInfiniteTransition(label = "hint").animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "hintAlpha"
            )
            val top = if (kind == "col") pad else pad + (cellSize + gap) * idx - 2.dp
            val left = if (kind == "row") pad else pad + (cellSize + gap) * idx - 2.dp
            val width = if (kind == "row") cellSize * 5 + gap * 4 + 4.dp else cellSize + 4.dp
            val height = if (kind == "col") cellSize * 5 + gap * 4 + 4.dp else cellSize + 4.dp

            Box(
                modifier = Modifier
                    .offset(left, top)
                    .size(width, height)
                    .graphicsLayer { alpha = hintAlpha }
                    .border(1.dp, WarnColor, RoundedCornerShape(8.dp))
                    .background(WarnColor.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            )
        }
    }
}

// ─── Wordmark ──────────────────────────────────────────────────────────────────

@Composable
fun Wordmark() {
    val glitchTrans = rememberInfiniteTransition(label = "glitch")
    val glitch1 by glitchTrans.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 6000
                0f at 0
                0f at 5500
                (-3f) at 5580
                2f at 5640
                (-1f) at 5700
                0f at 5800
                0f at 6000
            },
            RepeatMode.Restart
        ),
        label = "glitch1"
    )

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "Shift",
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            color = TextColor,
            letterSpacing = (-2).sp,
            modifier = Modifier.graphicsLayer { translationX = glitch1 }
        )
        Text(
            text = "Tac",
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            color = TextColor,
            letterSpacing = (-2).sp,
        )
        Spacer(Modifier.width(6.dp))
        val dotPulse by glitchTrans.animateFloat(
            initialValue = 0.55f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "dotPulse"
        )
        Box(
            modifier = Modifier
                .padding(bottom = 10.dp)
                .size(8.dp)
                .graphicsLayer { alpha = dotPulse; scaleX = dotPulse; scaleY = dotPulse }
                .background(XColor, RoundedCornerShape(2.dp))
        )
    }
}

// ─── Stats Row ─────────────────────────────────────────────────────────────────

@Composable
fun StatRow() {
    val items = listOf("GRID" to "5×5", "PLAYERS" to "2", "WIN" to "5 LINE", "TWIST" to "SHIFT@10")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GridColor),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items.forEachIndexed { i, (k, v) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Bg1)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(k, fontSize = 8.sp, letterSpacing = 1.5.sp, color = MutedColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                Text(v, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextColor, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ─── Mode Button ───────────────────────────────────────────────────────────────

@Composable
fun ModeButton(
    label: String,
    sub: String,
    accent: Color,
    softAccent: Color,
    glowAccent: Color,
    icon: @Composable () -> Unit,
    kbd: String,
    enabled: Boolean = true,
    badge: String? = null,
    onClick: () -> Unit,
) {
    var hover by remember { mutableStateOf(false) }
    val effectiveAccent = if (enabled) accent else DimColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (hover && enabled) Brush.horizontalGradient(listOf(softAccent, Bg1))
                else Brush.horizontalGradient(listOf(Bg1, Bg1))
            )
            .border(1.dp, if (hover && enabled) accent else GridColor, RoundedCornerShape(12.dp))
            .then(
                if (enabled) Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick
                ) else Modifier
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Bg2)
                    .border(1.dp, effectiveAccent, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = if (enabled) TextColor else MutedColor,
                        letterSpacing = 1.sp,
                    )
                    if (badge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Bg2)
                                .border(1.dp, DimColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(badge, fontSize = 7.sp, color = DimColor, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                        }
                    }
                }
                Text(
                    sub,
                    fontSize = 10.sp,
                    color = if (enabled) MutedColor else DimColor,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Bg2)
                        .border(1.dp, GridColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(kbd, fontSize = 9.sp, color = if (enabled) MutedColor else DimColor, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                }
                Text("→", fontSize = 16.sp, color = effectiveAccent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Mode Select Screen ────────────────────────────────────────────────────────

@Composable
fun ModeSelectScreen(onChoose: (GameMode) -> Unit) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val dotPulse by pulseAnim.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "dotAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF1A2438), Bg0),
                    radius = 900f
                )
            )
    ) {
        // Accent blobs
        Box(
            modifier = Modifier
                .offset((-40).dp, (-40).dp)
                .size(220.dp)
                .background(
                    Brush.radialGradient(listOf(XSoft, Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset((-60).dp, 60.dp)
                .size(260.dp)
                .background(
                    Brush.radialGradient(listOf(OSoft, Color.Transparent)),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .padding(top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top meta bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .graphicsLayer { alpha = dotPulse }
                        .background(XColor, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text("v1.0 · BUILD 26.05", fontSize = 9.sp, letterSpacing = 2.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text("EST. 2026", fontSize = 9.sp, letterSpacing = 2.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
            }

            // Wordmark
            Wordmark()

            // Demo section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val warnPulse by pulseAnim.animateFloat(
                        initialValue = 0.55f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                        label = "warnPulse"
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .graphicsLayer { alpha = warnPulse }
                            .background(WarnColor, CircleShape)
                    )
                    Text("LIVE DEMO", fontSize = 9.sp, letterSpacing = 3.sp, color = MutedColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                    Text("· how it plays", fontSize = 9.sp, color = DimColor, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(10.dp))
                DemoBoard()
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(XColor, RoundedCornerShape(2.dp)))
                        Text("PLAYER X", fontSize = 9.sp, letterSpacing = 1.5.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
                    }
                    Text("·", fontSize = 9.sp, color = DimColor)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(OColor, RoundedCornerShape(2.dp)))
                        Text("PLAYER O", fontSize = 9.sp, letterSpacing = 1.5.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
                    }
                    Text("·", fontSize = 9.sp, color = DimColor)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("◆", fontSize = 9.sp, color = WarnColor)
                        Text("SHIFT", fontSize = 9.sp, letterSpacing = 1.5.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Mode buttons
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Section header
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(GridColor))
                    Text("CHOOSE MODE", fontSize = 9.sp, letterSpacing = 3.sp, color = MutedColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(GridColor))
                }

                ModeButton(
                    label = "TWO PLAYERS",
                    sub = "Pass and play · local duel",
                    accent = XColor, softAccent = XSoft, glowAccent = XGlow,
                    kbd = "1",
                    onClick = { onChoose(GameMode.PVP) },
                    icon = {
                        TwoPlayerIcon()
                    }
                )

                ModeButton(
                    label = "VS AI",
                    sub = "Tactical opponent · solo",
                    accent = OColor, softAccent = OSoft, glowAccent = OGlow,
                    kbd = "2",
                    onClick = { onChoose(GameMode.AI) },
                    icon = { BotIcon() }
                )

                ModeButton(
                    label = "ONLINE",
                    sub = "Cross-device · not yet available",
                    accent = DimColor, softAccent = Color.Transparent, glowAccent = DimColor,
                    kbd = "3",
                    enabled = false,
                    badge = "COMING SOON",
                    onClick = {},
                    icon = { OnlineIcon() }
                )
            }

            // Scrolling ticker
            TickerRibbon()
        }
    }
}

// ─── Ticker Ribbon ─────────────────────────────────────────────────────────────

@Composable
fun TickerRibbon() {
    val items = listOf(
        "TAP TO PLACE",
        "AFTER MOVE 10 · SHIFT UNLOCKS",
        "SWIPE A ROW OR COLUMN",
        "FIRST TO FIVE IN A LINE WINS",
    )
    val fullText = items.joinToString("  ◆  ") + "  ◆  " + items.joinToString("  ◆  ")

    val tickerAnim = rememberInfiniteTransition(label = "ticker")
    val offset by tickerAnim.animateFloat(
        initialValue = 0f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Restart),
        label = "tickerOffset"
    )

    Box(modifier = Modifier.fillMaxWidth().height(22.dp)) {
        Row(modifier = Modifier.graphicsLayer { translationX = offset * 1800f }) {
            Text(
                text = fullText,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                color = MutedColor,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
    }
}

// ─── Icons ─────────────────────────────────────────────────────────────────────

@Composable
fun TwoPlayerIcon() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val xColor = XColor
        val oColor = OColor
        val strokeWidth = 1.5.dp.toPx()

        drawCircle(color = xColor, radius = w * 0.12f, center = androidx.compose.ui.geometry.Offset(w * 0.34f, h * 0.37f), style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth))
        drawArc(color = xColor, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.05f, h * 0.54f),
            size = androidx.compose.ui.geometry.Size(w * 0.58f, h * 0.44f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
        )
        drawCircle(color = oColor, radius = w * 0.12f, center = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.37f), style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth))
        drawArc(color = oColor, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.40f, h * 0.54f),
            size = androidx.compose.ui.geometry.Size(w * 0.58f, h * 0.44f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
        )
    }
}

@Composable
fun BotIcon() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val oColor = OColor
        val strokeWidth = 1.5.dp.toPx()

        // Body rect
        drawRoundRect(color = oColor, topLeft = androidx.compose.ui.geometry.Offset(w * 0.16f, h * 0.29f),
            size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.54f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth))
        // Eyes
        drawCircle(color = oColor, radius = w * 0.065f, center = androidx.compose.ui.geometry.Offset(w * 0.37f, h * 0.54f))
        drawCircle(color = oColor, radius = w * 0.065f, center = androidx.compose.ui.geometry.Offset(w * 0.63f, h * 0.54f))
        // Antenna
        drawLine(color = oColor, start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.16f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.29f), strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(color = oColor, radius = w * 0.07f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.12f))
        // Mouth
        drawLine(color = oColor, start = androidx.compose.ui.geometry.Offset(w * 0.36f, h * 0.70f),
            end = androidx.compose.ui.geometry.Offset(w * 0.64f, h * 0.70f), strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
fun OnlineIcon() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.5.dp.toPx()
        val color = DimColor
        val cx = w * 0.5f
        val cy = h * 0.62f
        // Three wifi arcs
        drawArc(color = color, startAngle = 210f, sweepAngle = 120f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.13f, cy - h * 0.13f),
            size = androidx.compose.ui.geometry.Size(w * 0.26f, h * 0.26f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawArc(color = color, startAngle = 210f, sweepAngle = 120f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.30f, cy - h * 0.30f),
            size = androidx.compose.ui.geometry.Size(w * 0.60f, h * 0.60f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawArc(color = color, startAngle = 210f, sweepAngle = 120f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.47f, cy - h * 0.47f),
            size = androidx.compose.ui.geometry.Size(w * 0.94f, h * 0.94f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        // Center dot
        drawCircle(color = color, radius = w * 0.07f, center = androidx.compose.ui.geometry.Offset(cx, cy))
    }
}
