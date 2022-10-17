import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

enum class MessageType {
    MESSAGE, SERVER, INFO, TYPING_INDICATOR
}

@Serializable
data class SendMessage(
    val user: ChatUser,
    val message: String,
    val type: MessageType?,
    val time: String = SimpleDateFormat("MM/dd hh:mm a").format(System.currentTimeMillis())
)

@Serializable
data class ChatUser(val name: String)

@Serializable
data class PostMessage(val name: String, val message: String)

fun input(prompt: String = "", scanner: Scanner = Scanner(System.`in`)): String =
    println(prompt).let { scanner.nextLine() }

private val json = Json {
    prettyPrint = true
    isLenient = true
    encodeDefaults = true
}

class Chat {

    private val json = Json {
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient {
        install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(json) }
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

    val messages = MutableSharedFlow<SendMessage>()

    val name = MutableStateFlow<String?>(null)

    suspend fun init() {
        client.ws(method = HttpMethod.Get, host = "0.0.0.0", port = 8080, path = "/anagramerChat") {
            incoming
                .consumeAsFlow()
                .filterIsInstance<Frame.Text>()
                .map { it.readText() }
                .map { text ->
                    if (name.value == null) name.emit(text)
                    println(text)
                    try {
                        json.decodeFromString<SendMessage>(text)
                    } catch (e: Exception) {
                        null
                    }
                }
                .filterNotNull()
                .onEach { messages.emit(it) }
                .collect()
        }
    }

    suspend fun sendMessage(message: String) {
        client.post("http://0.0.0.0:8080/anagramerMessage") {
            setBody(PostMessage(name.value.orEmpty(), message))
            contentType(ContentType.Application.Json)
        }
    }
}

fun main() = runBlocking {
    val scan = Scanner(System.`in`)
    val client = HttpClient { install(WebSockets) }

    //val host = input("Enter the host ip: (default can be 0.0.0.0)", scan).let { if (it.isBlank()) "0.0.0.0" else it }
    //val port = input("Enter the port: (default can be 8080)", scan).let { if (it.isBlank()) "8080" else it }.toInt()

    client.ws(method = HttpMethod.Get, host = "0.0.0.0", port = 8080, path = "/anagramerChat") {
        launch {
            incoming
                .consumeAsFlow()
                .filterIsInstance<Frame.Text>()
                .map { it.readText() }
                .map { text ->
                    try {
                        json.decodeFromString<SendMessage>(text).message
                    } catch (e: Exception) {
                        text
                    }
                }
                .onEach { println(it) }
                .collect()
        }

        gameLoop@ while (isActive) {
            val input = input("Chat:", scan)
            send(Frame.Text(input))
        }
    }
}