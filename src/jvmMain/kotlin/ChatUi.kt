import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ApplicationScope.ChatUi(
    onCloseRequest: () -> Unit,
    scope: CoroutineScope = rememberCoroutineScope(),
    vm: ChatViewModel = remember { ChatViewModel(scope) }
) {
    WindowWithBar(
        onCloseRequest,
        bottomBar = {
            BottomAppBar {
                OutlinedTextField(
                    value = vm.text,
                    onValueChange = vm::updateText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent {
                            when {
                                it.key == Key.Enter && it.type == KeyEventType.KeyUp -> {
                                    vm.send()
                                    true
                                }

                                else -> false
                            }
                        },
                    trailingIcon = { IconButton(onClick = vm::send) { Icon(Icons.Default.Send, null) } },
                    singleLine = true,
                    label = { Text("You are: ${vm.name?.user?.name}") }
                )
            }
        }
    ) {
        Row {
            Column(Modifier.weight(8f)) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    stickyHeader { CenterAlignedTopAppBar(title = { Text("Chat") }) }
                    items(vm.messages) {
                        Card(
                            border = if (it.user.name == vm.name?.user?.name) BorderStroke(1.dp, Emerald) else null
                        ) {
                            ListItem(
                                icon = { Text(it.user.name) },
                                text = { Text(it.message) },
                                overlineText = { Text(it.time) }
                            )
                        }
                    }
                }
                vm.typingIndicator?.text?.let { Text(it) }
            }
            Divider(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colors.primary
            )
            LazyColumn(
                modifier = Modifier.weight(2f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                stickyHeader { CenterAlignedTopAppBar(title = { Text("Users") }) }
                vm.users?.userList?.let {
                    items(it) { user ->
                        Card { ListItem(text = { Text(user.name) }) }
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

    var text by mutableStateOf("")
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
            withContext(Dispatchers.IO) { chat.sendMessage(text) }
            text = ""
        }
    }

    fun updateText(message: String) {
        text = message
        job?.cancel()
        job = viewModelScope.launch {
            if (!hasSent) {
                chat.isTyping(text.isNotEmpty())
                hasSent = true
            }
            delay(2500)
            chat.isTyping(false)
            hasSent = false
        }
    }
}