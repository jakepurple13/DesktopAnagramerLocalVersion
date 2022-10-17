import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    onValueChange = { vm.text = it },
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
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            stickyHeader { CenterAlignedTopAppBar(title = { Text("Chat") }) }
            items(vm.messages) {
                if (it is MessageMessage) {
                    Card(
                        border = if (it.user.name == vm.name?.user?.name) BorderStroke(1.dp, Emerald) else null
                    ) {
                        ListItem(
                            icon = { Text(it.user.name) },
                            text = { Text(it.message) },
                            overlineText = { Text(it.time) }
                        )
                    }
                } else if (it is UserListMessage) {
                    Card {
                        ListItem(
                            icon = { Text("Current Users:") },
                            text = { Text(it.userList.joinToString(",") { it.name }) },
                            overlineText = { Text(it.time) }
                        )
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
    val messages = mutableStateListOf<Message>()

    var name by mutableStateOf<SetupMessage?>(null)

    init {
        viewModelScope.launch { chat.init() }

        chat.messages
            .onEach { messages.add(it) }
            .launchIn(viewModelScope)

        chat.name
            .filterNotNull()
            .onEach { name = it }
            .launchIn(viewModelScope)
    }

    fun send() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { chat.sendMessage(text) }
            text = ""
        }
    }
}