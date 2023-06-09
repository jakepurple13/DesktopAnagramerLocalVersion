import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ApplicationScope.NumbersGame() {
    val state = rememberWindowState()

    Window(
        state = state,
        undecorated = true,
        transparent = true,
        onCloseRequest = ::exitApplication,
    ) {
        Surface(
            shape = when (hostOs) {
                OS.Linux -> RoundedCornerShape(8.dp)
                OS.Windows -> RectangleShape
                OS.MacOS -> RoundedCornerShape(8.dp)
                else -> RoundedCornerShape(8.dp)
            },
            modifier = Modifier.animateContentSize()
        ) {
            Scaffold(
                topBar = {
                    Column {
                        WindowDraggableArea(
                            modifier = Modifier.combinedClickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {},
                                onDoubleClick = {
                                    state.placement =
                                        if (state.placement != WindowPlacement.Maximized) {
                                            WindowPlacement.Maximized
                                        } else {
                                            WindowPlacement.Floating
                                        }
                                }
                            )
                        ) {
                            TopAppBar(
                                backgroundColor = MaterialTheme.colorScheme.surface,
                                elevation = 0.dp,
                            ) {
                                when (hostOs) {
                                    OS.Linux -> LinuxTopBar(state, ::exitApplication)
                                    OS.Windows -> WindowsTopBar(state, ::exitApplication)
                                    OS.MacOS -> MacOsTopBar(state, ::exitApplication)
                                    else -> {}
                                }
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            ) { padding ->
                Surface(modifier = Modifier.padding(padding)) {
                    //App(scope, vm, snackbarHostState)
                    NumbersGameScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumbersGameScreen() {
    val numbersViewModel = remember { NumbersViewModel() }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        animateIntAsState(numbersViewModel.randomNumber).value.toString(),
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    numbersViewModel.numberChoices.forEach { (t, u) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AnimatedVisibility(u == 0) {
                                IconButton(
                                    onClick = { numbersViewModel.chooseNumber(t, true) }
                                ) { Icon(Icons.Default.ArrowUpward, null) }
                            }
                            Text(animateIntAsState(u).value.toString())
                            AnimatedVisibility(u == 0) {
                                IconButton(
                                    onClick = { numbersViewModel.chooseNumber(t, false) }
                                ) { Icon(Icons.Default.ArrowDownward, null) }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { numbersViewModel.randomize() },
                        enabled = numbersViewModel.numberChoices.values.all { it != 0 }
                    ) { Text("Start") }
                    Button(
                        onClick = { numbersViewModel.reset() },
                    ) { Text("New Game") }
                }
            }
        }
    }

}

class NumbersViewModel {

    var randomNumber by mutableStateOf(0)

    val numberChoices = mutableStateMapOf<Int, Int>(
        0 to 0,
        1 to 0,
        2 to 0,
        3 to 0,
        4 to 0,
        5 to 0
    )

    fun randomize() {
        randomNumber = Random.nextInt(100, 1000)
    }

    fun chooseNumber(index: Int, highOrLow: Boolean) {
        numberChoices[index] = if (highOrLow) {
            listOf(25, 50, 75, 100).random()
        } else {
            Random.nextInt(1, 10)
        }
    }

    fun reset() {
        randomNumber = 0
        repeat(6) {
            numberChoices[it] = 0
        }
    }
}