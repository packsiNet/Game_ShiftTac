package com.shifttac

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shifttac.game.GameMode
import com.shifttac.game.GameViewModel
import com.shifttac.ui.GameScreen
import com.shifttac.ui.ModeSelectScreen
import com.shifttac.ui.theme.Bg0
import com.shifttac.ui.theme.ShiftTacTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShiftTacTheme {
                // Fill the full screen (including under system bars) with the background color,
                // then inset the app content so nothing sits behind the status/nav bar or notch.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Bg0)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        ShiftTacApp()
                    }
                }
            }
        }
    }
}

@Composable
fun ShiftTacApp() {
    var screen by remember { mutableStateOf("mode") }
    val gameViewModel: GameViewModel = viewModel()

    when (screen) {
        "mode" -> ModeSelectScreen(
            onChoose = { mode ->
                gameViewModel.startGame(mode)
                screen = "game"
            }
        )
        "game" -> GameScreen(
            viewModel = gameViewModel,
            onExit = { screen = "mode" }
        )
    }
}
