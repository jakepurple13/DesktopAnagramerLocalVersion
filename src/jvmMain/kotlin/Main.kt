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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
                                            OS.Linux -> LinuxTopBar(state)
                                            OS.Windows -> WindowsTopBar(state)
                                            OS.MacOS -> MacOsTopBar(state)
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
        }
    }
}

@Composable
private fun ApplicationScope.LinuxTopBar(state: WindowState) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.Start
        ) {
            androidx.compose.material3.IconButton(onClick = ::exitApplication) {
                androidx.compose.material3.Icon(
                    Icons.Default.Close,
                    null
                )
            }
            androidx.compose.material3.IconButton(onClick = {
                state.isMinimized = !state.isMinimized
            }) { androidx.compose.material3.Icon(Icons.Default.Minimize, null) }
            androidx.compose.material3.IconButton(
                onClick = {
                    state.placement = if (state.placement != WindowPlacement.Maximized) WindowPlacement.Maximized
                    else WindowPlacement.Floating
                }
            ) { androidx.compose.material3.Icon(Icons.Default.Maximize, null) }
        }

        Text(
            "Anagramer",
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@Composable
private fun ApplicationScope.WindowsTopBar(state: WindowState) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.Start
        ) {
            androidx.compose.material3.IconButton(onClick = ::exitApplication) {
                androidx.compose.material3.Icon(
                    Icons.Default.Close,
                    null
                )
            }
            androidx.compose.material3.IconButton(onClick = {
                state.isMinimized = !state.isMinimized
            }) { androidx.compose.material3.Icon(Icons.Default.Minimize, null) }
            androidx.compose.material3.IconButton(
                onClick = {
                    state.placement = if (state.placement != WindowPlacement.Maximized) WindowPlacement.Maximized
                    else WindowPlacement.Floating
                }
            ) { androidx.compose.material3.Icon(Icons.Default.Maximize, null) }
        }

        Text(
            "Anagramer",
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun ApplicationScope.MacOsTopBar(state: WindowState) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.Start
        ) {
            androidx.compose.material3.IconButton(onClick = ::exitApplication) {
                androidx.compose.material3.Icon(
                    Icons.Default.Close,
                    null
                )
            }
            androidx.compose.material3.IconButton(onClick = {
                state.isMinimized = !state.isMinimized
            }) { androidx.compose.material3.Icon(Icons.Default.Minimize, null) }
            androidx.compose.material3.IconButton(
                onClick = {
                    state.placement = if (state.placement != WindowPlacement.Fullscreen) WindowPlacement.Fullscreen
                    else WindowPlacement.Floating
                }
            ) {
                androidx.compose.material3.Icon(
                    if (state.placement != WindowPlacement.Fullscreen) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                    null
                )
            }
        }

        Text(
            "Anagramer",
            modifier = Modifier.align(Alignment.Center),
        )
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
            FilledTonalButton(
                onClick = vm::useHint,
                enabled = vm.hintCount > 0,
                modifier = Modifier.align(Alignment.CenterStart)
            ) { Text("?" + vm.hintCount.toString()) }

            FilledTonalButton(
                onClick = { vm.showScoreInfo = true },
                enabled = vm.score > 0,
                modifier = Modifier.align(Alignment.Center)
            ) { Text("${animateIntAsState(vm.score).value} points") }
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

                FilledTonalButton(
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

                FilledTonalButton(
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
                FilledTonalButton(
                    onClick = vm::bringBackWord
                ) { Icon(Icons.Default.Undo, null) }

                FilledTonalButton(
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

class WordViewModel(val viewModelScope: CoroutineScope) {

    var shouldStartNewGame by mutableStateOf(false)
    var finishGame by mutableStateOf(false)
    var finishedGame by mutableStateOf(false)
    private var usedFinishGame = false
    var isLoading by mutableStateOf(false)

    var mainLetters by mutableStateOf("")

    private val anagrams = mutableStateListOf<String>()
    val anagramWords by derivedStateOf {
        val size = if (anagrams.size > 50) 4 else 3
        anagrams.filterNot { it.length < size }
    }

    val wordGuesses = mutableStateListOf<String>()
    var wordGuess by mutableStateOf("")
    private var prevGuess = ""

    var definition by mutableStateOf<Definition?>(null)
    private val definitionMap = mutableMapOf<String, Definition>()

    var error: String? by mutableStateOf(null)

    var usedHint by mutableStateOf(false)
    var hints by mutableStateOf(0)
    var hintList by mutableStateOf(emptySet<String>())
    var gotNewHint by mutableStateOf(false)
    val hintCount by derivedStateOf { hints + if (usedHint) 0 else 1 }

    var showScoreInfo by mutableStateOf(false)
    private var internalScore = 0
    val score by derivedStateOf {
        if (finishedGame) {
            internalScore
        } else {
            wordGuesses
                .groupBy { it.length }
                .map { it.key * (it.value.size + it.key) }
                .ifEmpty { listOf(0) }
                .reduce { acc, i -> acc + i }
        }
    }

    val scoreInfo by derivedStateOf {
        wordGuesses
            .sortedByDescending { it.length }
            .groupBy { it.length }
            .mapValues { (it.value.size + it.key) * it.key }
    }

    fun getWord() {
        viewModelScope.launch {
            shouldStartNewGame = false
            finishedGame = false
            isLoading = true
            internalScore = 0
            definitionMap.clear()
            if (
                (wordGuesses.size >= anagramWords.size / 2 || wordGuesses.any { it.length == 7 }) && !usedFinishGame
            ) {
                gotNewHint = true
                hints++
            }
            anagrams.clear()
            usedHint = false
            wordGuesses.clear()
            hintList = emptySet()
            usedFinishGame = false
            wordGuess = ""
            withContext(Dispatchers.IO) {
                getLetters().fold(
                    onSuccess = {
                        println(it)
                        error = null
                        mainLetters = it?.word
                            ?.toList()
                            ?.shuffled()
                            ?.joinToString("")
                            .orEmpty()
                        anagrams.addAll(it?.anagrams.orEmpty())
                    },
                    onFailure = {
                        it.printStackTrace()
                        error = "Something went Wrong"
                        ""
                    }
                )
            }
            isLoading = false
        }
    }

    fun endGame() {
        internalScore = score
        usedFinishGame = !(wordGuesses.size >= anagramWords.size / 2 || wordGuesses.any { it.length == 7 })
        wordGuesses.clear()
        wordGuesses.addAll(anagramWords)
        finishGame = false
        finishedGame = true
    }

    fun shuffle() {
        mainLetters = mainLetters.toList().shuffled().joinToString("")
    }

    fun updateGuess(word: String) {
        //TODO: Final thing is to make sure only the letters chosen can be pressed
        if (word.toList().all { mainLetters.contains(it) }) {
            wordGuess = word
        }
    }

    fun useHint() {
        if (hints > 0) {
            if (usedHint) {
                hints--
            }
            usedHint = true
            mainLetters
                .uppercase()
                .filterNot { hintList.contains(it.toString()) }
                .randomOrNull()
                ?.uppercase()
                ?.let {
                    val list = hintList.toMutableSet()
                    list.add(it)
                    hintList = list
                }
        }
    }

    fun bringBackWord() {
        wordGuess = prevGuess
    }

    fun guess(onAlreadyGuessed: (Int) -> Unit): String {
        return when {
            wordGuesses.contains(wordGuess) -> {
                onAlreadyGuessed(wordGuesses.indexOf(wordGuess))
                "Already Guessed"
            }

            anagramWords.any { it.equals(wordGuess, ignoreCase = true) } -> {
                wordGuesses.add(wordGuess)
                prevGuess = wordGuess
                wordGuess = ""
                "Got it!"
            }

            else -> "Not in List"
        }
    }

    fun getDefinition(word: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (definitionMap.contains(word) && definitionMap[word] != null) {
                onComplete()
                definition = definitionMap[word]
            } else {
                isLoading = true
                withContext(Dispatchers.IO) {
                    definition = withTimeoutOrNull(5000) { getWordDefinition(word) }?.fold(
                        onSuccess = { definition ->
                            error = null
                            definition?.also {
                                onComplete()
                                definitionMap[word] = it
                            }
                        },
                        onFailure = { null }
                    )
                    if (definition == null) error = "Something went Wrong"
                }
                isLoading = false
            }
        }
    }
}

suspend fun getLetters() = runCatching {
    getApi<Word>("http://0.0.0.0:8080/randomWord/7?minimumSize=4")
}

suspend fun getWordDefinition(word: String) = runCatching {
    getApi<Definition>("http://0.0.0.0:8080/wordDefinition/$word")
}

suspend inline fun <reified T> getApi(
    url: String,
    noinline headers: HeadersBuilder.() -> Unit = {}
): T? {
    val client = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    isLenient = true
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
            )
        }
    }
    val response: HttpResponse = client.get(url) { headers(headers) }
    return response.body<T>()
}

@Serializable
data class Word(val word: String, val anagrams: List<String>)

@Serializable
data class Definition(val word: String, val definition: String)

val Emerald = Color(0xFF2ecc71)
val Sunflower = Color(0xFFf1c40f)
val Alizarin = Color(0xFFe74c3c)