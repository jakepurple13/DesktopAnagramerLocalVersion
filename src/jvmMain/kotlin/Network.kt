import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.stream.Collectors

val dict = getDictionaryList()

@Composable
fun highScores(vararg key: Any?) = produceState<Result<Scores>>(Result.Loading, keys = key) {
    value = Result.Loading
    val response = withContext(Dispatchers.IO) { getHighScores().getOrNull() }
    value = response?.let { Result.Success(it) } ?: Result.Error
}

sealed class Result<out R> {
    class Success<out T>(val value: T) : Result<T>()
    object Error : Result<Nothing>()
    object Loading : Result<Nothing>()
}

suspend fun getLetters() = runCatching {
    //getApi<Word>("http://0.0.0.0:8080/randomWord/7?minimumSize=4")
    val word = dict
        .filter { it.length == 7 }
        .random()

    val words = dict
        .filter { it.length >= 4 && compare(word, it) }
        .distinctBy { it.lowercase() }
    Word(
        word = word,
        anagrams = words
    )

}

suspend fun getWordDefinition(word: String) = runCatching {
    //getApi<Definition>("http://0.0.0.0:8080/wordDefinition/$word")
    //curl dict://dict.org/d:"${1}"
    val def = RunCommand.getDefinition(word).await()
    Definition(
        word = word,
        definition = def
    )
}

suspend fun getHighScores() = runCatching {
    //getApi<Scores>("http://0.0.0.0:8080/highScores")
    Scores(listOf(HighScore("", 0)))
}

suspend fun postHighScore(name: String, score: Int) = runCatching {
    //postApi<Scores>("http://0.0.0.0:8080/highScore/$name/$score")
    Scores(listOf(HighScore("", 0)))
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

suspend inline fun <reified T> postApi(
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
    val response: HttpResponse = client.post(url) { headers(headers) }
    return response.body<T>()
}

@Serializable
data class Word(val word: String, val anagrams: List<String>)

@Serializable
data class Definition(val word: String, val definition: String)

@Serializable
data class Scores(val list: List<HighScore>)

@Serializable
data class HighScore(val name: String, val score: Int)

private fun getDictionaryList(): List<String> {
    return File("/usr/share/dict/words")
        .readLines()
        .filterNot { it.contains("-") }
}

private fun compare(word: String, anagram: String): Boolean {
    val c = word.groupBy { it.lowercaseChar() }.mapValues { it.value.size }
    val a = anagram.groupBy { it.lowercaseChar() }.mapValues { it.value.size }

    for (i in a) {
        c[i.key]?.let { if (it < i.value) return false } ?: return false
    }

    return true
}

object RunCommand {
    suspend fun getDefinition(word: String) = GlobalScope.async {
        val command = "curl dict://dict.org/d:\"$word\""
        val process = Runtime.getRuntime().exec(command)
        process.waitFor()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        reader.lines().collect(Collectors.joining("\n"))
    }
}