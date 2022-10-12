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
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    getApi<Word>("http://0.0.0.0:8080/randomWord/7?minimumSize=4")
}

suspend fun getWordDefinition(word: String) = runCatching {
    getApi<Definition>("http://0.0.0.0:8080/wordDefinition/$word")
}

suspend fun getHighScores() = runCatching {
    getApi<Scores>("http://0.0.0.0:8080/highScores")
}

suspend fun postHighScore(name: String, score: Int) = runCatching {
    postApi<Scores>("http://0.0.0.0:8080/highScore/$name/$score")
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