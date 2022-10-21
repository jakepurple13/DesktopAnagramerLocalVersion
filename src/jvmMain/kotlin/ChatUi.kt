import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import com.mikepenz.markdown.Markdown
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(
    ExperimentalMaterialApi::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun ApplicationScope.ChatUi(
    onCloseRequest: () -> Unit,
    scope: CoroutineScope = rememberCoroutineScope(),
    vm: ChatViewModel = remember { ChatViewModel(scope) }
) {
    WindowWithBar(
        onCloseRequest,
        bottomBar = {
            Column {
                AnimatedVisibility(
                    vm.typingIndicator != null,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    vm.typingIndicator?.text?.let { Text(it) }
                }
                OutlinedTextField(
                    value = vm.text,
                    onValueChange = vm::updateText,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .fillMaxWidth()
                        .onPreviewKeyEvent {
                            when {
                                it.key == Key.Enter && !it.isShiftPressed && it.type == KeyEventType.KeyUp -> {
                                    vm.send()
                                    true
                                }

                                it.key == Key.Enter && it.isShiftPressed && it.type == KeyEventType.KeyUp -> {
                                    val value = vm.text.text + "\n"
                                    vm.updateText(TextFieldValue(value, selection = TextRange(value.length)))
                                    true
                                }

                                else -> false
                            }
                        },
                    trailingIcon = { IconButton(onClick = vm::send) { Icon(Icons.Default.Send, null) } },
                    label = { Text("You are: ${vm.name?.user?.name}") }
                )
            }
        }
    ) {
        Row {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .weight(8f)
            ) {
                stickyHeader { CenterAlignedTopAppBar(title = { Text("Chat") }) }
                items(vm.messages) {
                    OutlinedCard(
                        border = if (it.user.name == vm.name?.user?.name) BorderStroke(1.dp, Emerald)
                        else CardDefaults.outlinedCardBorder()
                    ) {
                        ListItem(
                            headlineText = { Text(it.user.name) },
                            overlineText = { Text(it.time) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            supportingText = {
                                Column {
                                    Divider()
                                    Markdown(it.message)
                                }
                            }
                        )
                    }
                }
            }
            Divider(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.primary
            )
            LazyColumn(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .weight(2f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                stickyHeader { CenterAlignedTopAppBar(title = { Text("Users") }) }
                vm.users?.userList?.let {
                    items(it) { user ->
                        OutlinedCard {
                            ListItem(
                                headlineText = { Text(user.name) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

class ChatViewModel(
    private
    val viewModelScope: CoroutineScope
) {

    private val chat = Chat()

    var text by mutableStateOf(TextFieldValue(""))
    val messages = mutableStateListOf<MessageMessage>()

    var name by mutableStateOf<SetupMessage?>(null)

    var typingIndicator by mutableStateOf<TypingIndicatorMessage?>(null)
    var users by mutableStateOf<UserListMessage?>(null)

    private var hasSent = false
    private var job: Job? = null

    init {
        viewModelScope.launch { chat.init() }

        chat.messages
            .onEach { messages.add(it) }
            .launchIn(viewModelScope)

        chat.name
            .filterNotNull()
            .onEach { name = it }
            .launchIn(viewModelScope)

        chat.users
            .onEach { users = it }
            .launchIn(viewModelScope)

        chat.arePeopleTyping
            .onEach { typingIndicator = it }
            .launchIn(viewModelScope)
    }

    fun send() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chat.sendMessage(text.text)
                chat.isTyping(false)
            }
            hasSent = false
            text = TextFieldValue("")
        }
    }

    fun updateText(message: TextFieldValue) {
        text = message
        job?.cancel()
        job = viewModelScope.launch {
            if (!hasSent) {
                chat.isTyping(text.text.isNotEmpty())
                hasSent = true
            }
            delay(5000)
            if (text.text.isEmpty()) chat.isTyping(false)
            hasSent = false
        }
    }
}