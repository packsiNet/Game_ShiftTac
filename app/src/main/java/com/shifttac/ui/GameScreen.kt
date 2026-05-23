package com.shifttac.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import android.app.Activity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shifttac.game.*
import com.shifttac.ui.theme.*
import kotlin.math.abs

// ─── Drag state (stable across recompositions) ────────────────────────────────

private class DragState {
    var startX = 0f
    var startY = 0f
    var row = 0
    var col = 0
    var fired = false
    var axisLocked = false   // true once we know H or V
    var isHorizontal = false // true = row shift, false = col shift
}

// ─── Glyph drawing ─────────────────────────────────────────────────────────────

@Composable
fun PlayerGlyph(player: String, size: Dp, glow: Boolean = false) {
    val color = if (player == "X") XColor else OColor
    androidx.compose.foundation.Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val sw = (w * 0.145f).coerceAtLeast(3f)
        if (player == "X") {
            drawLine(color = color, start = Offset(w * 0.22f, w * 0.22f), end = Offset(w * 0.78f, w * 0.78f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(color = color, start = Offset(w * 0.78f, w * 0.22f), end = Offset(w * 0.22f, w * 0.78f), strokeWidth = sw, cap = StrokeCap.Round)
        } else {
            drawCircle(color = color, radius = w * 0.32f, center = Offset(w * 0.5f, w * 0.5f), style = Stroke(sw))
        }
    }
}

// ─── Pulse ring ────────────────────────────────────────────────────────────────

@Composable
fun PulseRing(player: String) {
    val color = if (player == "X") XGlow else OGlow
    val anim = rememberInfiniteTransition(label = "ring")
    val alpha by anim.animateFloat(0.8f, 0f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "rA")
    val scale by anim.animateFloat(1f, 1.28f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "rS")
    Box(modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }.border(1.5.dp, color, RoundedCornerShape(10.dp)))
}

// ─── Cell ──────────────────────────────────────────────────────────────────────

@Composable
fun GameCell(
    value: String?,
    r: Int, c: Int,
    isLast: Boolean,
    isWin: Boolean,
    canTap: Boolean,
    isActiveAxis: Boolean,     // highlighted by drag preview
    shiftOffset: Float,        // px offset for slide-in animation
    onTap: () -> Unit,
) {
    val colorVar = if (value == "X") XColor else if (value == "O") OColor else null
    val softVar = if (value == "X") XSoft else if (value == "O") OSoft else Color.Transparent

    val popScale by animateFloatAsState(
        targetValue = if (value != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "pop-$r-$c"
    )

    val borderColor = when {
        isWin   -> colorVar ?: GridColor
        isLast  -> colorVar ?: GridColor
        isActiveAxis -> if (value == "X") XColor.copy(alpha = 0.5f) else if (value == "O") OColor.copy(alpha = 0.5f) else WarnColor.copy(alpha = 0.5f)
        else    -> GridColor
    }
    val borderWidth = if (isWin || isLast) 2.dp else if (isActiveAxis) 1.5.dp else 1.dp
    val bgColor = when {
        value != null -> softVar
        isActiveAxis  -> WarnColor.copy(alpha = 0.06f)
        else          -> CellBg
    }

    Box(
        modifier = Modifier
            .size(60.dp)
            .graphicsLayer { translationX = shiftOffset }
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .then(
                if (canTap && value == null) Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onTap
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (value != null) {
            Box(modifier = Modifier.graphicsLayer { scaleX = popScale; scaleY = popScale }) {
                PlayerGlyph(player = value, size = 33.dp, glow = isWin || isLast)
            }
        }
        if (isLast && !isWin && value != null) PulseRing(player = value)
        if (isWin && colorVar != null) {
            val wa = rememberInfiniteTransition(label = "wn")
            val wp by wa.animateFloat(0.7f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "wP")
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = wp }.background(colorVar.copy(alpha = 0.1f), RoundedCornerShape(10.dp)))
        }
    }
}

// ─── Win line ──────────────────────────────────────────────────────────────────

@Composable
fun WinLineOverlay(winInfo: WinInfo?) {
    if (winInfo == null || winInfo.line.size < 5) return
    val progress by animateFloatAsState(1f, tween(550, easing = FastOutSlowInEasing), label = "wl")
    val color = if (winInfo.winner == "X") XColor else OColor

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val cs = 60f; val gap = 8f; val pad = 14f
        fun cx(c: Int) = pad + c * (cs + gap) + cs / 2
        fun cy(r: Int) = pad + r * (cs + gap) + cs / 2
        val l = winInfo.line
        val x1 = cx(l[0].second); val y1 = cy(l[0].first)
        val x2 = cx(l[4].second); val y2 = cy(l[4].first)
        drawLine(color, Offset(x1, y1), Offset(x1 + (x2 - x1) * progress, y1 + (y2 - y1) * progress), 5.dp.toPx(), StrokeCap.Round)
    }
}

// ─── Shift arrows ──────────────────────────────────────────────────────────────

@Composable
fun ArrowIcon(color: Color, rotation: Float) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation }) {
        val w = size.width; val sw = 2.2.dp.toPx()
        drawLine(color, Offset(w * 0.15f, w * 0.5f), Offset(w * 0.82f, w * 0.5f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.82f, w * 0.5f), Offset(w * 0.54f, w * 0.28f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.82f, w * 0.5f), Offset(w * 0.54f, w * 0.72f), sw, StrokeCap.Round)
    }
}

@Composable
fun ShiftArrows(player: String) {
    val color = if (player == "X") XColor else OColor
    val anim = rememberInfiniteTransition(label = "arr")
    val d by anim.animateFloat(0f, 8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "aD")
    val a by anim.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "aA")
    Box(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.CenterEnd).offset(x = (20 + d).dp).graphicsLayer { alpha = a }) { ArrowIcon(color, 0f) }
        Box(Modifier.align(Alignment.CenterStart).offset(x = (-20 - d).dp).graphicsLayer { alpha = a }) { ArrowIcon(color, 180f) }
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-20 - d).dp).graphicsLayer { alpha = a }) { ArrowIcon(color, -90f) }
        Box(Modifier.align(Alignment.BottomCenter).offset(y = (20 + d).dp).graphicsLayer { alpha = a }) { ArrowIcon(color, 90f) }
    }
}

// ─── Board ─────────────────────────────────────────────────────────────────────

@Composable
fun GameBoard(
    board: Board,
    lastPlaced: Pair<Int, Int>?,
    winInfo: WinInfo?,
    shiftedAxis: ShiftedAxis?,
    turn: String,
    gameState: GameState,
    onTap: (Int, Int) -> Unit,
    onShift: (ShiftMove) -> Unit,
    onShiftFeedback: () -> Unit,
) {
    val CELL = 60.dp
    val GAP = 8.dp
    val PAD = 14.dp
    val boardSize = CELL * 5 + GAP * 4 + PAD * 2

    val canShift = gameState == GameState.WAITING_FOR_SHIFT
    val canTap = gameState == GameState.PLACING

    // Drag state — stable object, never reset by recomposition
    val drag = remember { DragState() }

    // Live drag preview: which row or col is under the finger right now
    var activeRow by remember { mutableStateOf(-1) }
    var activeCol by remember { mutableStateOf(-1) }

    // Edge glow
    val edgeAnim = rememberInfiniteTransition(label = "edge")
    val edgeAlpha by edgeAnim.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "eA")

    // Slide animation for shifted cells
    // Each cell in the shifted row/col animates from (dir * cellStep) → 0
    val shiftAnimatable = remember { Animatable(0f) }
    val shiftKey = remember(shiftedAxis) { shiftedAxis?.hashCode() ?: 0 }
    LaunchedEffect(shiftKey) {
        if (shiftedAxis != null) {
            val cellStep = 60f + 8f  // dp — in Canvas units (we apply toPx in drawing)
            val startOffset = -(shiftedAxis.dir * cellStep)
            shiftAnimatable.snapTo(startOffset)
            shiftAnimatable.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 380f))
        }
    }

    Box(modifier = Modifier.size(boardSize)) {
        // Edge glow ring
        if (canShift) {
            val glowColor = if (turn == "X") XGlow else OGlow
            Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = edgeAlpha }.drawBehind {
                drawRoundRect(glowColor, cornerRadius = CornerRadius(22.dp.toPx()), style = Stroke(3.5.dp.toPx()))
            })
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Bg1)
                .border(1.dp, GridColor, RoundedCornerShape(16.dp))
                .then(
                    if (canShift) Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            // Wait for first touch
                            val down = awaitFirstDown(requireUnconsumed = false)
                            drag.startX = down.position.x
                            drag.startY = down.position.y
                            val cellStep = (CELL + GAP).toPx()
                            val padPx = PAD.toPx()
                            drag.col = ((drag.startX - padPx) / cellStep).toInt().coerceIn(0, 4)
                            drag.row = ((drag.startY - padPx) / cellStep).toInt().coerceIn(0, 4)
                            drag.fired = false
                            drag.axisLocked = false
                            activeRow = -1
                            activeCol = -1

                            // Track subsequent moves
                            do {
                                val event = awaitPointerEvent()
                                val ptr = event.changes.firstOrNull() ?: break
                                if (!ptr.pressed) break

                                val totalDx = ptr.position.x - drag.startX
                                val totalDy = ptr.position.y - drag.startY

                                // Lock axis once we have 8dp of movement
                                if (!drag.axisLocked && (abs(totalDx) > 8.dp.toPx() || abs(totalDy) > 8.dp.toPx())) {
                                    drag.axisLocked = true
                                    drag.isHorizontal = abs(totalDx) > abs(totalDy)
                                }

                                // Update preview highlight
                                if (drag.axisLocked && !drag.fired) {
                                    if (drag.isHorizontal) {
                                        activeRow = drag.row; activeCol = -1
                                    } else {
                                        activeCol = drag.col; activeRow = -1
                                    }
                                }

                                // Fire shift once threshold reached — row/col must have at least one piece
                                val THRESHOLD = 14.dp.toPx()
                                if (!drag.fired && drag.axisLocked) {
                                    if (drag.isHorizontal && abs(totalDx) >= THRESHOLD) {
                                        if (board[drag.row].any { it != null }) {
                                            onShiftFeedback()
                                            onShift(ShiftMove("row", drag.row, if (totalDx > 0) 1 else -1))
                                        }
                                        drag.fired = true
                                        activeRow = -1; activeCol = -1
                                    } else if (!drag.isHorizontal && abs(totalDy) >= THRESHOLD) {
                                        if ((0 until GRID_SIZE).any { board[it][drag.col] != null }) {
                                            onShiftFeedback()
                                            onShift(ShiftMove("col", drag.col, if (totalDy > 0) 1 else -1))
                                        }
                                        drag.fired = true
                                        activeRow = -1; activeCol = -1
                                    }
                                }
                                ptr.consume()
                            } while (true)

                            // Pointer up — clear preview
                            activeRow = -1; activeCol = -1; drag.fired = false
                        }
                    } else Modifier
                )
        ) {
            // Scanlines
            Box(modifier = Modifier.fillMaxSize().drawBehind {
                var y = 0f; while (y < size.height) {
                    drawLine(Color.White.copy(alpha = 0.012f), Offset(0f, y), Offset(size.width, y), 1f); y += 4f
                }
            })

            Column(modifier = Modifier.padding(PAD), verticalArrangement = Arrangement.spacedBy(GAP)) {
                for (r in 0 until GRID_SIZE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                        for (c in 0 until GRID_SIZE) {
                            val isLast = lastPlaced?.first == r && lastPlaced.second == c
                            val isWin = winInfo?.line?.contains(r to c) == true
                            val isActiveAxis = (activeRow == r) || (activeCol == c)

                            // Slide offset for shifted cells
                            val inShifted = shiftedAxis != null &&
                                ((shiftedAxis.kind == "row" && shiftedAxis.idx == r) ||
                                 (shiftedAxis.kind == "col" && shiftedAxis.idx == c))
                            val rawOffset = if (inShifted) shiftAnimatable.value else 0f
                            // Convert dp offset to px for graphicsLayer (it's already in dp units from Animatable)
                            val shiftOffsetPx = rawOffset  // used in graphicsLayer which is in px

                            GameCell(
                                value = board[r][c],
                                r = r, c = c,
                                isLast = isLast, isWin = isWin,
                                canTap = canTap, isActiveAxis = isActiveAxis,
                                shiftOffset = shiftOffsetPx,
                                onTap = { onTap(r, c) }
                            )
                        }
                    }
                }
            }
            WinLineOverlay(winInfo = winInfo)
        }

        if (canShift) ShiftArrows(player = turn)
    }
}

// ─── Player Tag ────────────────────────────────────────────────────────────────

@Composable
fun PlayerTag(player: String, active: Boolean, isAI: Boolean = false) {
    val color = if (player == "X") XColor else OColor
    val soft = if (player == "X") XSoft else OSoft
    val pAnim = rememberInfiniteTransition(label = "pt$player")
    val bA by pAnim.animateFloat(0.7f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bA")

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) soft else Color.Transparent)
            .border(1.dp, if (active) color.copy(alpha = bA) else GridColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PlayerGlyph(player = player, size = 22.dp, glow = active)
        Column {
            Text(if (isAI && player == "O") "AI" else "P${if (player == "X") "1" else "2"}", fontSize = 9.sp, color = MutedColor, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
            Text(if (active) "TURN" else "WAIT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)
        }
    }
}

// ─── HUD ───────────────────────────────────────────────────────────────────────

@Composable
fun HUD(turn: String, mode: GameMode, moveCount: Int, gameState: GameState, onExit: () -> Unit, onReset: () -> Unit, feedbackMode: FeedbackMode, onToggleFeedback: () -> Unit) {
    val shiftUnlocked = moveCount >= SHIFT_UNLOCK_AT
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(onExit) { Text("‹", fontSize = 18.sp, color = MutedColor) }
        PlayerTag(player = "X", active = turn == "X" && gameState != GameState.GAME_OVER)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MOVE", fontSize = 9.sp, color = MutedColor, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(moveCount.toString().padStart(2, '0'), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextColor, fontFamily = FontFamily.Monospace)
                Text("/25", fontSize = 12.sp, color = DimColor, fontFamily = FontFamily.Monospace)
            }
            Text(
                if (shiftUnlocked) "◆ SHIFT ON" else "SHIFT IN ${(SHIFT_UNLOCK_AT - moveCount).coerceAtLeast(0)}",
                fontSize = 8.sp, letterSpacing = 1.5.sp, color = if (shiftUnlocked) WarnColor else DimColor, fontFamily = FontFamily.Monospace
            )
        }
        PlayerTag(player = "O", active = turn == "O" && gameState != GameState.GAME_OVER, isAI = mode == GameMode.AI)
        IconButton(onReset) { Text("↺", fontSize = 16.sp, color = MutedColor) }
        IconButton(onToggleFeedback) {
            Text(
                when (feedbackMode) {
                    FeedbackMode.SOUND     -> "♪"
                    FeedbackMode.VIBRATION -> "≋"
                    FeedbackMode.SILENT    -> "○"
                },
                fontSize = 13.sp,
                color = when (feedbackMode) {
                    FeedbackMode.SOUND     -> WarnColor
                    FeedbackMode.VIBRATION -> OColor
                    FeedbackMode.SILENT    -> DimColor
                }
            )
        }
    }
}

@Composable
private fun IconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
            .border(1.dp, GridColor, RoundedCornerShape(8.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ─── Status Banner ─────────────────────────────────────────────────────────────

@Composable
fun StatusBanner(gameState: GameState, turn: String, mode: GameMode, moveCount: Int) {
    val dotAnim = rememberInfiniteTransition(label = "dot")
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).background(Bg1)
            .drawBehind {
                drawLine(GridColor, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                drawLine(GridColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        when (gameState) {
            GameState.AI_THINKING -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI IS THINKING", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, color = OColor, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (i in 0..2) {
                            val a by dotAnim.animateFloat(0.2f, 1f, infiniteRepeatable(tween(1200, delayMillis = i * 200), RepeatMode.Reverse), label = "d$i")
                            Box(Modifier.size(4.dp).graphicsLayer { alpha = a }.background(OColor, CircleShape))
                        }
                    }
                }
            }
            GameState.WAITING_FOR_SHIFT -> {
                val c = if (turn == "X") XColor else OColor
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◆ SWIPE TO SHIFT", fontSize = 9.sp, letterSpacing = 3.sp, color = WarnColor, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(turn, fontSize = 12.sp, color = c, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(" · drag a row or column", fontSize = 12.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            GameState.PLACING -> {
                val c = if (turn == "X") XColor else OColor
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TAP TO PLACE", fontSize = 9.sp, letterSpacing = 3.sp, color = MutedColor, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(turn, fontSize = 12.sp, color = c, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(if (moveCount >= SHIFT_UNLOCK_AT) " · then swipe to shift" else " · ${SHIFT_UNLOCK_AT - moveCount} until shift unlocks",
                            fontSize = 12.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            else -> Box(Modifier.height(36.dp))
        }
    }
}

// ─── Move Log ──────────────────────────────────────────────────────────────────

@Composable
fun MoveLog(log: List<MoveLogEntry>) {
    val sc = rememberScrollState()
    LaunchedEffect(log.size) { sc.animateScrollTo(sc.maxValue) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(sc).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("LOG", fontSize = 9.sp, letterSpacing = 2.sp, color = DimColor, fontFamily = FontFamily.Monospace)
        if (log.isEmpty()) Text("—", fontSize = 10.sp, color = DimColor, fontFamily = FontFamily.Monospace)
        else log.forEach { entry ->
            val c = if (entry.player == "X") XColor else OColor
            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Bg1).border(1.dp, c, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                Text(entry.label, fontSize = 9.sp, color = c, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
            }
        }
    }
}

// ─── Shift hint footer ─────────────────────────────────────────────────────────

@Composable
fun ShiftHintFooter() {
    Box(
        modifier = Modifier.fillMaxWidth().background(Bg1)
            .border(1.dp, GridColor, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(vertical = 10.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("← Swipe row left/right →", fontSize = 10.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
            Text("·", color = DimColor, fontSize = 10.sp)
            Text("↑ Swipe col up/down ↓", fontSize = 10.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
        }
    }
}

// ─── Win Board Preview ─────────────────────────────────────────────────────────

@Composable
private fun WinBoardPreview(board: Board, winData: WinInfo, winnerColor: Color) {
    val cs = 32.dp
    val gap = 4.dp
    val pad = 8.dp
    val boardSize = cs * 5 + gap * 4 + pad * 2

    val glowAnim = rememberInfiniteTransition(label = "wbg")
    val gA by glowAnim.animateFloat(0.5f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "wbA")

    Box(
        modifier = Modifier
            .size(boardSize)
            .clip(RoundedCornerShape(14.dp))
            .background(Bg1)
            .border(1.5.dp, winnerColor.copy(alpha = gA), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (r in 0 until GRID_SIZE) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until GRID_SIZE) {
                        val v = board[r][c]
                        val isWin = winData.line.contains(r to c)
                        val softColor = if (v == "X") XSoft else if (v == "O") OSoft else Color.Transparent
                        Box(
                            modifier = Modifier
                                .size(cs)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    when {
                                        isWin -> winnerColor.copy(alpha = 0.18f)
                                        v != null -> softColor
                                        else -> CellBg
                                    }
                                )
                                .border(
                                    if (isWin) 2.dp else 1.dp,
                                    if (isWin) winnerColor.copy(alpha = gA) else GridColor.copy(alpha = 0.6f),
                                    RoundedCornerShape(7.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (v != null) PlayerGlyph(player = v, size = cs * 0.54f, glow = isWin)
                        }
                    }
                }
            }
        }
    }
}

// ─── Win Overlay ───────────────────────────────────────────────────────────────

@Composable
fun WinOverlay(winner: String?, winData: WinInfo?, board: Board?, mode: GameMode, onReplay: () -> Unit, onMenu: () -> Unit) {
    if (winner == null) return
    val isDraw = winner == "draw"
    val color = when (winner) { "X" -> XColor; "O" -> OColor; else -> WarnColor }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xEA0F1520))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Text(
                if (isDraw) "— STALEMATE —" else "— VICTORY —",
                fontSize = 10.sp, letterSpacing = 4.sp, color = MutedColor,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(18.dp))

            if (!isDraw && board != null && winData != null) {
                WinBoardPreview(board = board, winData = winData, winnerColor = color)
            } else {
                Box(
                    modifier = Modifier.size(90.dp).clip(RoundedCornerShape(20.dp))
                        .background(Bg1).border(2.dp, WarnColor, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("=", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = WarnColor)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                when {
                    isDraw -> "DRAW"
                    mode == GameMode.AI && winner == "O" -> "AI WINS"
                    else -> "$winner  WINS"
                },
                fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp,
                color = color, fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    isDraw -> "No five-in-a-line. Board is locked."
                    mode == GameMode.AI && winner == "O" -> "The machine claims this round."
                    mode == GameMode.AI && winner == "X" -> "You out-shifted the machine."
                    else -> "Five in a line. Well played."
                },
                fontSize = 11.sp, color = MutedColor, letterSpacing = 1.sp,
                textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(color)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onReplay)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("↺  PLAY AGAIN", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = Bg0, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .border(1.dp, GridColor, RoundedCornerShape(10.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onMenu)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("MAIN MENU", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = TextColor, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ─── Game Screen ───────────────────────────────────────────────────────────────

@Composable
fun GameScreen(viewModel: GameViewModel, onExit: () -> Unit) {
    val board = viewModel.board
    val turn = viewModel.turn
    val moveCount = viewModel.moveCount
    val lastPlaced = viewModel.lastPlaced
    val gameState = viewModel.gameState
    val winInfo = viewModel.winInfo
    val log = viewModel.log
    val mode = viewModel.mode
    val shiftedAxis = viewModel.shiftedAxis

    val isAITurn = mode == GameMode.AI && turn == "O"
    val displayState = if (isAITurn && gameState == GameState.WAITING_FOR_SHIFT) GameState.AI_THINKING else gameState

    val context = LocalContext.current
    val activity = context as? Activity
    val soundManager = remember { SoundManager(context) }
    val adManager = remember { AdManager(context) }
    var feedbackMode by remember { mutableStateOf(soundManager.mode) }

    // Sound/vibration on piece placement — read the board to know who placed
    LaunchedEffect(lastPlaced) {
        val pos = lastPlaced ?: return@LaunchedEffect
        if (winInfo != null) return@LaunchedEffect  // win sound covers this
        when (board[pos.first][pos.second]) {
            "X" -> soundManager.onPlaceX()
            "O" -> soundManager.onPlaceO()
        }
    }
    // Sound/vibration on win
    LaunchedEffect(winInfo) {
        if (winInfo != null) soundManager.onWin()
    }

    Box(modifier = Modifier.fillMaxSize().background(Bg0)) {
        Column(modifier = Modifier.fillMaxSize()) {
            HUD(
                turn = turn, mode = mode, moveCount = moveCount, gameState = gameState,
                onExit = onExit, onReset = { viewModel.reset() },
                feedbackMode = feedbackMode,
                onToggleFeedback = {
                    feedbackMode = when (feedbackMode) {
                        FeedbackMode.SOUND     -> FeedbackMode.VIBRATION
                        FeedbackMode.VIBRATION -> FeedbackMode.SILENT
                        FeedbackMode.SILENT    -> FeedbackMode.SOUND
                    }
                    soundManager.mode = feedbackMode
                }
            )
            StatusBanner(gameState = displayState, turn = turn, mode = mode, moveCount = moveCount)

            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Bg2).border(1.dp, GridColor, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(if (mode == GameMode.AI) "vs AI" else "2 Players", fontSize = 9.sp, color = MutedColor, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                GameBoard(
                    board = board, lastPlaced = lastPlaced, winInfo = winInfo,
                    shiftedAxis = shiftedAxis,
                    turn = turn, gameState = displayState,
                    onTap = { r, c -> viewModel.place(r, c) },
                    onShift = { shift -> viewModel.applyShift(shift) },
                    onShiftFeedback = { soundManager.onShift() }
                )
            }

            MoveLog(log = log)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(XColor); Text("P1", fontSize = 10.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
                Text("  ·  ", fontSize = 10.sp, color = DimColor)
                LegendDot(OColor); Text(if (mode == GameMode.AI) "AI" else "P2", fontSize = 10.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
                Text("  ·  ", fontSize = 10.sp, color = DimColor)
                Text("5 in a row wins", fontSize = 10.sp, color = MutedColor, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (gameState == GameState.GAME_OVER && winInfo != null) {
            WinOverlay(
                winner = winInfo.winner, winData = winInfo, board = board, mode = mode,
                onReplay = {
                    if (activity != null) adManager.showIfReady(activity) { viewModel.reset() }
                    else viewModel.reset()
                },
                onMenu = {
                    if (activity != null) adManager.showIfReady(activity) { onExit() }
                    else onExit()
                }
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(6.dp).background(color, CircleShape))
    Spacer(Modifier.width(4.dp))
}
