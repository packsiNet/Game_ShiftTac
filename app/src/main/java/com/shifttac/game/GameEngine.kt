package com.shifttac.game

const val GRID_SIZE = 5
const val SHIFT_UNLOCK_AT = 10

typealias Board = Array<Array<String?>>

fun emptyBoard(): Board = Array(GRID_SIZE) { Array(GRID_SIZE) { null } }

fun cloneBoard(b: Board): Board = Array(GRID_SIZE) { r -> Array(GRID_SIZE) { c -> b[r][c] } }

data class WinInfo(val winner: String, val line: List<Pair<Int, Int>>)

fun checkWin(board: Board): WinInfo? {
    val lines = mutableListOf<List<Pair<Int, Int>>>()
    for (r in 0 until GRID_SIZE) lines.add((0 until GRID_SIZE).map { c -> r to c })
    for (c in 0 until GRID_SIZE) lines.add((0 until GRID_SIZE).map { r -> r to c })
    lines.add((0 until GRID_SIZE).map { i -> i to i })
    lines.add((0 until GRID_SIZE).map { i -> i to (GRID_SIZE - 1 - i) })

    for (line in lines) {
        val v0 = board[line[0].first][line[0].second] ?: continue
        if (line.all { (r, c) -> board[r][c] == v0 }) {
            return WinInfo(v0, line)
        }
    }
    val empties = (0 until GRID_SIZE).sumOf { r -> (0 until GRID_SIZE).count { c -> board[r][c] == null } }
    if (empties == 0) return WinInfo("draw", emptyList())
    return null
}

fun shiftRow(board: Board, row: Int, dir: Int): Board {
    val nb = cloneBoard(board)
    val orig = board[row].copyOf()
    for (c in 0 until GRID_SIZE) {
        nb[row][c] = orig[((c - dir) + GRID_SIZE) % GRID_SIZE]
    }
    return nb
}

fun shiftCol(board: Board, col: Int, dir: Int): Board {
    val nb = cloneBoard(board)
    val orig = Array(GRID_SIZE) { r -> board[r][col] }
    for (r in 0 until GRID_SIZE) {
        nb[r][col] = orig[((r - dir) + GRID_SIZE) % GRID_SIZE]
    }
    return nb
}

fun aiPickPlacement(board: Board, me: String): Pair<Int, Int>? {
    val opp = if (me == "X") "O" else "X"
    val empties = mutableListOf<Pair<Int, Int>>()
    for (r in 0 until GRID_SIZE) for (c in 0 until GRID_SIZE) if (board[r][c] == null) empties.add(r to c)
    if (empties.isEmpty()) return null

    for ((r, c) in empties) {
        val nb = cloneBoard(board); nb[r][c] = me
        if (checkWin(nb)?.winner == me) return r to c
    }
    for ((r, c) in empties) {
        val nb = cloneBoard(board); nb[r][c] = opp
        if (checkWin(nb)?.winner == opp) return r to c
    }

    var best: Pair<Int, Int> = empties[0]
    var bestScore = Double.NEGATIVE_INFINITY
    for ((r, c) in empties) {
        var score = -(Math.abs(r - 2) + Math.abs(c - 2)) * 0.5
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr; val nc = c + dc
            if (nr in 0 until GRID_SIZE && nc in 0 until GRID_SIZE) {
                if (board[nr][nc] == me) score += 1.0
                if (board[nr][nc] == opp) score += 0.4
            }
        }
        score += Math.random() * 0.5
        if (score > bestScore) { bestScore = score; best = r to c }
    }
    return best
}

data class ShiftMove(val kind: String, val idx: Int, val dir: Int)

fun aiPickShift(board: Board, me: String): ShiftMove {
    val opp = if (me == "X") "O" else "X"
    val options = mutableListOf<ShiftMove>()
    for (r in 0 until GRID_SIZE) {
        if (board[r].any { it != null }) {
            options.add(ShiftMove("row", r, 1)); options.add(ShiftMove("row", r, -1))
        }
    }
    for (c in 0 until GRID_SIZE) {
        if ((0 until GRID_SIZE).any { board[it][c] != null }) {
            options.add(ShiftMove("col", c, 1)); options.add(ShiftMove("col", c, -1))
        }
    }
    if (options.isEmpty()) return ShiftMove("row", 0, 1)

    for (opt in options) {
        val nb = if (opt.kind == "row") shiftRow(board, opt.idx, opt.dir) else shiftCol(board, opt.idx, opt.dir)
        if (checkWin(nb)?.winner == me) return opt
    }

    val safe = options.filter { opt ->
        val nb = if (opt.kind == "row") shiftRow(board, opt.idx, opt.dir) else shiftCol(board, opt.idx, opt.dir)
        checkWin(nb)?.winner != opp
    }

    val pool = if (safe.isNotEmpty()) safe else options
    var best = pool[0]
    var bestScore = Double.NEGATIVE_INFINITY
    for (opt in pool) {
        val nb = if (opt.kind == "row") shiftRow(board, opt.idx, opt.dir) else shiftCol(board, opt.idx, opt.dir)
        val score = scoreLongestRun(nb, me) - scoreLongestRun(nb, opp) * 0.7 + Math.random()
        if (score > bestScore) { bestScore = score; best = opt }
    }
    return best
}

private fun scoreLongestRun(board: Board, who: String): Int {
    val lines = mutableListOf<List<String?>>()
    for (r in 0 until GRID_SIZE) lines.add((0 until GRID_SIZE).map { c -> board[r][c] })
    for (c in 0 until GRID_SIZE) lines.add((0 until GRID_SIZE).map { r -> board[r][c] })
    lines.add((0 until GRID_SIZE).map { i -> board[i][i] })
    lines.add((0 until GRID_SIZE).map { i -> board[i][GRID_SIZE - 1 - i] })
    var max = 0
    for (ln in lines) {
        var run = 0
        for (v in ln) { if (v == who) { run++; if (run > max) max = run } else run = 0 }
    }
    return max
}
