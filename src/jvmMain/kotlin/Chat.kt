import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import io.ktor.websocket.serialization.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.text.SimpleDateFormat
import java.util.*

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
    serializersModule = SerializersModule {
        polymorphic(Message::class) {
            subclass(MessageMessage::class)
            subclass(SetupMessage::class)
            subclass(UserListMessage::class)
        }
    }
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

    val messages = MutableSharedFlow<Message>()

    val name = MutableStateFlow<SetupMessage?>(null)

    suspend fun init() {
        client.ws(method = HttpMethod.Get, host = "0.0.0.0", port = 8080, path = "/anagramerChat") {
            incoming
                .consumeAsFlow()
                .filterIsInstance<Frame.Text>()
                .map { it.readText() }
                .map { text ->
                    println(text)
                    try {
                        json.decodeFromString<Message>(text)
                    } catch (e: Exception) {
                        try {
                            json.decodeFromString<SetupMessage>(text)
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                .filterNotNull()
                .onEach {
                    when (it) {
                        is MessageMessage -> messages.emit(it)
                        is SetupMessage -> name.emit(it)
                        is UserListMessage -> messages.emit(it)
                    }
                }
                .collect()
        }
    }

    suspend fun sendMessage(message: String) {
        client.post("http://0.0.0.0:8080/anagramerMessage") {
            setBody(PostMessage(name.value?.user?.name.orEmpty(), message))
            contentType(ContentType.Application.Json)
        }
    }
}

private val jsonConverter = KotlinxWebsocketSerializationConverter(json)

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
                    println(text)
                    try {
                        json.decodeFromString<Message>(text)
                    } catch (e: Exception) {
                        try {
                            json.decodeFromString<SetupMessage>(text)
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                .filterNotNull()
                .onEach {
                    println(it)
                    when (it) {
                        is MessageMessage -> println("Message: ${it.message}")
                        is SetupMessage -> println("Setup: ${it.userColor}")
                        is UserListMessage -> println("UserList: ${it.userList}")
                    }
                }
                .collect()
        }

        gameLoop@ while (isActive) {
            val input = input("Chat:", scan)
            send(Frame.Text(input))
        }
    }
}

enum class MessageType {
    MESSAGE, SERVER, INFO, TYPING_INDICATOR, SETUP
}

@Serializable
sealed class Message {
    abstract val user: ChatUser
    abstract val messageType: MessageType
    val time: String = SimpleDateFormat("MM/dd hh:mm a").format(System.currentTimeMillis())
}

@Serializable
@SerialName("MessageMessage")
data class MessageMessage(
    override val user: ChatUser,
    val message: String,
    override val messageType: MessageType = MessageType.MESSAGE
) : Message() {
}

@Serializable
@SerialName("SetupMessage")
data class SetupMessage(
    override val user: ChatUser,
    val userColor: Int,
    override val messageType: MessageType = MessageType.SETUP
) : Message()

@Serializable
@SerialName("UserListMessage")
data class UserListMessage(
    override val user: ChatUser,
    override val messageType: MessageType = MessageType.INFO,
    val userList: List<ChatUser>
) : Message()