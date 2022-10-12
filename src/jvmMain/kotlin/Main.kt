// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs
import java.util.*
import androidx.compose.material3.MaterialTheme as M3MaterialTheme

@Composable
@Preview
fun App(
    scope: CoroutineScope,
    vm: WordViewModel,
    snackbarHostState: SnackbarHostState
) {
    WordUi(scope, vm, snackbarHostState)
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
fun main() = application {
    MaterialTheme(
        darkColors(
            primary = Color(0xff90CAF9),
            secondary = Color(0xff90CAF9)
        )
    ) {
        androidx.compose.material3.MaterialTheme(
            darkColorScheme(
                primary = Color(0xff90CAF9),
                secondary = Color(0xff90CAF9)
            )
        ) {
            val scope: CoroutineScope = rememberCoroutineScope()
            val vm: WordViewModel = remember { WordViewModel(scope) }
            val snackbarHostState = remember { SnackbarHostState() }
            val state = rememberWindowState()

            Window(
                state = state,
                undecorated = true,
                transparent = true,
                onCloseRequest = ::exitApplication,
                onPreviewKeyEvent = {
                    if (it.type == KeyEventType.KeyUp) {
                        when (it.key) {
                            Key.Backspace -> vm.updateGuess(vm.wordGuess.dropLast(1))

                            Key.Enter -> {
                                scope.launch {
                                    val message = vm.guess {}
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(
                                        message,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }

                            else -> it.awtEventOrNull?.keyChar?.let { c -> vm.updateGuess("${vm.wordGuess}$c") }
                        }
                    }
                    true
                }
            ) {
                androidx.compose.material3.Surface(
                    shape = when (hostOs) {
                        OS.Linux -> RoundedCornerShape(8.dp)
                        OS.Windows -> RectangleShape
                        OS.MacOS -> RoundedCornerShape(8.dp)
                        else -> RoundedCornerShape(8.dp)
                    },
                    modifier = Modifier.animateContentSize()
                ) {
                    androidx.compose.material3.Scaffold(
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
                                        backgroundColor = M3MaterialTheme.colorScheme.surface,
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
                                Divider(color = M3MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        containerColor = M3MaterialTheme.colorScheme.surface
                    ) { padding ->
                        androidx.compose.material3.Surface(modifier = Modifier.padding(padding)) {
                            App(scope, vm, snackbarHostState)
                        }
                    }
                }
            }
            if (vm.showHighScores) ShowHighScores(vm)
            if (vm.showSubmitScore) GameOver(vm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordUi(
    scope: CoroutineScope = rememberCoroutineScope(),
    vm: WordViewModel = remember { WordViewModel(scope) },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val gridState = rememberLazyListState()

    SnackbarHandler(vm = vm, snackbarHostState = snackbarHostState)

    WordDialogs(vm)

    NavigationDrawer(
        drawerContent = { DefinitionDrawer(vm) },
        drawerState = drawerState,
        gesturesEnabled = vm.definition != null
    ) {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = { Text("Guess the Words") },
                    actions = {
                        Text("${vm.wordGuesses.size}/${vm.anagramWords.size}")
                        TextButton(
                            onClick = { vm.finishGame = true },
                            enabled = !vm.finishedGame
                        ) { Text("Finish") }
                        TextButton(onClick = { vm.shouldStartNewGame = true }) { Text("New Game") }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            bottomBar = {
                BottomBar(
                    vm = vm,
                    snackbarHostState = snackbarHostState
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        ) { padding ->
            WordContent(
                padding = padding,
                vm = vm,
                gridState = gridState,
                drawerState = drawerState
            )
        }
    }

    LoadingDialog(showLoadingDialog = vm.isLoading)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun ApplicationScope.ShowHighScores(vm: WordViewModel) {
    WindowWithBar(
        onCloseRequest = { vm.showHighScores = false }
    ) {
        var retry by remember { mutableStateOf(0) }
        val scores by highScores(retry, vm.showSubmitScore)
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Crossfade(scores) { target ->
                when (target) {
                    is Result.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    is Result.Success<Scores> -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            stickyHeader { SmallTopAppBar(title = { Text("HighScores") }) }

                            itemsIndexed(target.value.list) { index, item ->
                                Card {
                                    ListItem(
                                        icon = { Text("$index.") },
                                        text = { Text(item.name) },
                                        trailing = { Text(item.score.toString()) }
                                    )
                                }
                            }
                        }
                    }

                    is Result.Error -> {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { retry++ },
                            modifier = Modifier.align(Alignment.Center)
                        ) { Text("Something went wrong. Please try again.") }
                    }
                }
            }
        }
    }
}

@Composable
fun ApplicationScope.GameOver(vm: WordViewModel) {
    WindowWithBar(
        onCloseRequest = { vm.showSubmitScore = false },
        bottomBar = {
            BottomAppBar {
                OutlinedTextField(
                    value = vm.name,
                    onValueChange = { vm.name = it },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = vm::sendHighScore) {
                            Icon(Icons.Default.Send, null)
                        }
                    }
                )
            }
        }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column {
                Text("Game Over!")
                Text("Final Score: ${vm.score}")
                Text("Do you want to submit it?")
            }
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterialApi::class
)
@Composable
fun WordContent(
    padding: PaddingValues,
    vm: WordViewModel,
    gridState: LazyListState,
    drawerState: DrawerState
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.padding(padding)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.OutlinedButton(
                onClick = vm::useHint,
                enabled = vm.hintCount > 0,
                modifier = Modifier.align(Alignment.CenterStart)
            ) { Text("?" + vm.hintCount.toString()) }

            androidx.compose.material3.OutlinedButton(
                onClick = { vm.showScoreInfo = true },
                enabled = vm.score > 0,
                modifier = Modifier.align(Alignment.Center)
            ) { Text("${animateIntAsState(vm.score).value} points") }

            androidx.compose.material3.OutlinedButton(
                onClick = { vm.showHighScores = true },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) { Text("View HighScores") }
        }
        LazyVerticalGrid(
            state = gridState,
            cells = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 2.dp)
        ) {
            items(vm.anagramWords.sortedByDescending { it.length }) { anagrams ->
                Crossfade(targetState = vm.wordGuesses.any { it.equals(anagrams, true) }) { state ->
                    if (state) {
                        Card(
                            onClick = { vm.getDefinition(anagrams) { scope.launch { drawerState.open() } } },
                        ) {
                            CustomListItem {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .align(Alignment.CenterVertically)
                                ) { Text(anagrams, style = M3MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    } else {
                        Card(onClick = {}, enabled = false) {
                            CustomListItem {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .align(Alignment.CenterVertically)
                                ) {
                                    Text(
                                        anagrams
                                            .uppercase()
                                            .replace(
                                                if (vm.hintList.isNotEmpty()) {
                                                    Regex("[^${vm.hintList.joinToString("")}]")
                                                } else {
                                                    Regex("\\w")
                                                },
                                                " _"
                                            ),
                                        style = M3MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${anagrams.length}",
                                    style = M3MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomBar(
    vm: WordViewModel,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    CustomBottomAppBar {
        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .animateContentSize()
                    .fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    vm.mainLetters.forEach {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { vm.updateGuess("${vm.wordGuess}$it") },
                            border = BorderStroke(1.dp, M3MaterialTheme.colorScheme.primary.copy(alpha = .5f)),
                        ) { Text(it.uppercase()) }
                    }
                }

                androidx.compose.material3.OutlinedButton(
                    onClick = vm::shuffle,
                ) { Icon(Icons.Default.Shuffle, null) }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .animateContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    vm.wordGuess.forEachIndexed { index, c ->
                        androidx.compose.material3.OutlinedButton(
                            onClick = { vm.updateGuess(vm.wordGuess.removeRange(index, index + 1)) },
                            border = BorderStroke(1.dp, M3MaterialTheme.colorScheme.primary)
                        ) { Text(c.uppercase()) }
                    }
                }

                androidx.compose.material3.OutlinedButton(
                    onClick = { vm.wordGuess = "" }
                ) { Icon(Icons.Default.Clear, null, tint = Alizarin) }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .animateContentSize()
                    .fillMaxWidth()
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = vm::bringBackWord
                ) { Icon(Icons.Default.Undo, null) }

                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        scope.launch {
                            val message = vm.guess {}
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message,
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    enabled = vm.wordGuess.isNotEmpty()
                ) {
                    Text(
                        "ENTER",
                        color = if (vm.wordGuess.isNotEmpty()) Emerald else androidx.compose.material3.LocalContentColor.current
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun DefinitionDrawer(vm: WordViewModel) {
    vm.definition?.let { definition ->
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            definition.word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        )
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        ) { padding ->
            LazyColumn(
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Card {
                        ListItem(
                            text = { Text(definition.word) },
                            secondaryText = {
                                Column {
                                    Text(definition.definition)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomBottomAppBar(
    modifier: Modifier = Modifier,
    containerColor: Color = M3MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        // TODO(b/209583788): Consider adding a shape parameter if updated design guidance allows
        shape = RectangleShape,
        modifier = modifier
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(contentPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun LoadingDialog(
    showLoadingDialog: Boolean,
) {
    if (showLoadingDialog) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        color = M3MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(28.0.dp)
                    )
            ) {
                Column {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(text = "Loading", Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun WordDialogs(vm: WordViewModel) {
    val isFinished by remember { derivedStateOf { vm.wordGuesses.size == vm.anagramWords.size } }

    if (vm.shouldStartNewGame) {
        AlertDialog(
            onDismissRequest = { vm.shouldStartNewGame = false },
            title = { Text("New Game?") },
            text = { Text("Are you sure?${if (!isFinished) " You will lose all your progress." else ""}") },
            confirmButton = { TextButton(onClick = vm::getWord) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { vm.shouldStartNewGame = false }) { Text("No") } }
        )
    }

    if (vm.finishGame) {
        AlertDialog(
            onDismissRequest = { vm.finishGame = false },
            title = { Text("Finish Game?") },
            text = { Text("Are you sure?${if (!isFinished) " You will lose all your progress." else ""}") },
            confirmButton = { TextButton(onClick = vm::endGame) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { vm.finishGame = false }) { Text("No") } }
        )
    }

    if (vm.showScoreInfo) {
        AlertDialog(
            onDismissRequest = { vm.showScoreInfo = false },
            title = { Text("Score Info") },
            text = {
                LazyColumn {
                    items(vm.scoreInfo.entries.toList()) {
                        ListItem(text = { Text("${it.key} = ${it.value} points") })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.showScoreInfo = false }) { Text("Done") } },
        )
    }
}

@Composable
fun SnackbarHandler(vm: WordViewModel, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(vm.error) {
        if (vm.error != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                vm.error!!,
                duration = SnackbarDuration.Long
            )
            when (result) {
                SnackbarResult.Dismissed -> vm.error = null
                SnackbarResult.ActionPerformed -> vm.error = null
            }
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    LaunchedEffect(vm.gotNewHint) {
        if (vm.gotNewHint) {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                "Got enough words for a new hint!",
                duration = SnackbarDuration.Short
            )
            vm.gotNewHint = when (result) {
                SnackbarResult.Dismissed -> false
                SnackbarResult.ActionPerformed -> false
            }
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }
}

@Composable
@ExperimentalMaterial3Api
private fun CustomListItem(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    containerColor: Color = M3MaterialTheme.colorScheme.surface,
    contentColor: Color = M3MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 4.dp,
    shadowElevation: Dp = 4.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 8.dp)
                .padding(PaddingValues(vertical = 16.dp, horizontal = 16.dp)),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

val Emerald = Color(0xFF2ecc71)
val Sunflower = Color(0xFFf1c40f)
val Alizarin = Color(0xFFe74c3c)