package com.shifttac.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class GameMode { PVP, AI }

enum class GameState { PLACING, WAITING_FOR_SHIFT, AI_THINKING, GAME_OVER }

data class MoveLogEntry(val player: String, val label: String)

data class ShiftedAxis(val kind: String, val idx: Int, val dir: Int = 0)

class GameViewModel : ViewModel() {

    var board by mutableStateOf(emptyBoard())
        private set
    var turn by mutableStateOf("X")
        private set
    var moveCount by mutableStateOf(0)
        private set
    var lastPlaced by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var gameState by mutableStateOf(GameState.PLACING)
        private set
    var winInfo by mutableStateOf<WinInfo?>(null)
        private set
    var log by mutableStateOf(listOf<MoveLogEntry>())
        private set
    var shiftedAxis by mutableStateOf<ShiftedAxis?>(null)
        private set
    var mode by mutableStateOf(GameMode.PVP)
        private set

    fun startGame(gameMode: GameMode) {
        mode = gameMode
        resetState()
    }

    private fun resetState() {
        board = emptyBoard()
        turn = "X"
        moveCount = 0
        lastPlaced = null
        gameState = GameState.PLACING
        winInfo = null
        log = listOf()
        shiftedAxis = null
    }

    fun reset() {
        resetState()
    }

    fun place(r: Int, c: Int) {
        if (gameState != GameState.PLACING) return
        if (board[r][c] != null) return

        val nb = cloneBoard(board)
        nb[r][c] = turn
        val newCount = moveCount + 1
        val colLabel = ('A' + c).toString()
        val rowLabel = (r + 1).toString()

        board = nb
        lastPlaced = r to c
        moveCount = newCount
        log = log + MoveLogEntry(turn, "$turn $colLabel$rowLabel")

        val win = checkWin(nb)
        if (win != null) {
            winInfo = win
            gameState = GameState.GAME_OVER
            return
        }

        if (newCount >= SHIFT_UNLOCK_AT) {
            gameState = GameState.WAITING_FOR_SHIFT
        } else {
            val next = if (turn == "X") "O" else "X"
            turn = next
            if (mode == GameMode.AI && next == "O") {
                gameState = GameState.AI_THINKING
                triggerAIPlacement()
            } else {
                gameState = GameState.PLACING
            }
        }
    }

    fun applyShift(shift: ShiftMove) {
        if (gameState != GameState.WAITING_FOR_SHIFT) return
        val axisHasPiece = if (shift.kind == "row") board[shift.idx].any { it != null }
                           else (0 until GRID_SIZE).any { board[it][shift.idx] != null }
        if (!axisHasPiece) return

        val nb = if (shift.kind == "row") shiftRow(board, shift.idx, shift.dir)
                 else shiftCol(board, shift.idx, shift.dir)

        val dirSym = when {
            shift.kind == "row" && shift.dir > 0 -> "→"
            shift.kind == "row" -> "←"
            shift.dir > 0 -> "↓"
            else -> "↑"
        }
        val axisLabel = if (shift.kind == "row") "R${shift.idx + 1}"
                        else ('A' + shift.idx).toString()

        board = nb
        log = log + MoveLogEntry(turn, "$turn $dirSym$axisLabel")

        val win = checkWin(nb)
        if (win != null) {
            // Block input immediately; let the shift animation finish before revealing the overlay
            gameState = GameState.GAME_OVER
            viewModelScope.launch {
                shiftedAxis = ShiftedAxis(shift.kind, shift.idx, shift.dir)
                delay(650)
                shiftedAxis = null
                lastPlaced = null
                winInfo = win
            }
            return
        }

        lastPlaced = null
        val next = if (turn == "X") "O" else "X"
        turn = next

        viewModelScope.launch {
            shiftedAxis = ShiftedAxis(shift.kind, shift.idx, shift.dir)
            delay(600)
            shiftedAxis = null
        }

        if (mode == GameMode.AI && next == "O") {
            gameState = GameState.AI_THINKING
            triggerAIPlacement()
        } else {
            gameState = GameState.PLACING
        }
    }

    private fun triggerAIPlacement() {
        viewModelScope.launch {
            delay(700 + (Math.random() * 500).toLong())
            val move = aiPickPlacement(board, "O")
            if (move != null) {
                val nb = cloneBoard(board)
                nb[move.first][move.second] = "O"
                val newCount = moveCount + 1
                val colLabel = ('A' + move.second).toString()
                val rowLabel = (move.first + 1).toString()

                board = nb
                lastPlaced = move
                moveCount = newCount
                log = log + MoveLogEntry("O", "O $colLabel$rowLabel")

                val win = checkWin(nb)
                if (win != null) {
                    winInfo = win
                    gameState = GameState.GAME_OVER
                    return@launch
                }

                if (newCount >= SHIFT_UNLOCK_AT) {
                    gameState = GameState.WAITING_FOR_SHIFT
                    triggerAIShift()
                } else {
                    turn = "X"
                    gameState = GameState.PLACING
                }
            }
        }
    }

    private fun triggerAIShift() {
        viewModelScope.launch {
            delay(900 + (Math.random() * 400).toLong())
            val shift = aiPickShift(board, "O")
            val nb = if (shift.kind == "row") shiftRow(board, shift.idx, shift.dir)
                     else shiftCol(board, shift.idx, shift.dir)

            val dirSym = when {
                shift.kind == "row" && shift.dir > 0 -> "→"
                shift.kind == "row" -> "←"
                shift.dir > 0 -> "↓"
                else -> "↑"
            }
            val axisLabel = if (shift.kind == "row") "R${shift.idx + 1}"
                            else ('A' + shift.idx).toString()

            board = nb
            log = log + MoveLogEntry("O", "O $dirSym$axisLabel")

            shiftedAxis = ShiftedAxis(shift.kind, shift.idx, shift.dir)

            val win = checkWin(nb)
            if (win != null) {
                // Block input immediately; let animation finish before showing overlay
                gameState = GameState.GAME_OVER
                delay(650)
                shiftedAxis = null
                lastPlaced = null
                winInfo = win
                return@launch
            }

            lastPlaced = null
            turn = "X"
            gameState = GameState.PLACING
            launch {
                delay(600)
                shiftedAxis = null
            }
        }
    }
}
