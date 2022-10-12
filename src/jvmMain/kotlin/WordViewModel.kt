import androidx.compose.runtime.*
import kotlinx.coroutines.*

class WordViewModel(val viewModelScope: CoroutineScope) {

    var showHighScores by mutableStateOf(false)
    var showSubmitScore by mutableStateOf(false)
    var name by mutableStateOf("")

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
            showSubmitScore = false
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
        showSubmitScore = true
    }

    fun sendHighScore() {
        viewModelScope.launch {
            postHighScore(name, internalScore).fold(
                onSuccess = { showSubmitScore = false },
                onFailure = { it.printStackTrace() }
            )
        }
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
