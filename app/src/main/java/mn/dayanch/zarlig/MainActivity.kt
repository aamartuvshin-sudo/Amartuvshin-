package mn.dayanch.zarlig

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mn.dayanch.zarlig.core.ChatMessage
import mn.dayanch.zarlig.core.CommandProcessor
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var processor: CommandProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processor = CommandProcessor(applicationContext)

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1565C0))) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F5F5)) {
                    ZarligScreen(processor)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZarligScreen(processor: CommandProcessor) {
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "Сайн байна уу! Би Зарлиг — таны туслах.\n" +
                        "Юу хиймээр байгаагаа бичих эсвэл 🎤 товч дарж хэлээрэй.\n" +
                        "«тусламж» гэвэл бүх командыг харуулна.",
                fromUser = false
            )
        )
    }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Командыг гүйцэтгэх нэгдсэн функц
    fun runCommand(cmd: String) {
        if (cmd.isBlank()) return
        messages.add(ChatMessage(cmd, fromUser = true))
        val result = processor.process(cmd)
        messages.add(ChatMessage(result.message, fromUser = false, success = result.success))
    }

    // Зөвшөөрөл асуух
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* хэрэглэгч шийдлээ */ }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS
            )
        )
    }

    // Чат доошоо гүйлгэх
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Дуу таних
    val context = LocalContextSafe()
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                runCommand(spoken)
            } else {
                Toast.makeText(context, "Юу хэлснийг таньсангүй.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // Монгол хэл; дэмжээгүй бол систем ойролцоо хэлээр солино
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "mn-MN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "mn-MN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Хэлээрэй...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Дуу таних үйлчилгээ олдсонгүй.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Толгой хэсэг
        TopAppBar(
            title = { Text("Зарлиг", color = Color.White) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1565C0))
        )

        // Чатын жагсаалт
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(msg)
            }
        }

        // Оролтын талбар
        Surface(shadowElevation = 8.dp, color = Color.White) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Микрофон товч
                FilledIconButton(
                    onClick = { startVoice() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF1565C0)
                    )
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Дуугаар хэлэх", tint = Color.White)
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Команд бичих...") },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        runCommand(input.trim())
                        input = ""
                    })
                )

                // Илгээх товч
                FilledIconButton(
                    onClick = {
                        runCommand(input.trim())
                        input = ""
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF1565C0)
                    )
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Илгээх", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    val bg = when {
        msg.fromUser -> Color(0xFF1565C0)
        msg.success -> Color.White
        else -> Color(0xFFFFEBEE)
    }
    val textColor = if (msg.fromUser) Color.White else Color(0xFF212121)
    val align = if (msg.fromUser) Alignment.End else Alignment.Start

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(align)
                .widthIn(max = 300.dp)
                .background(bg, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = msg.text,
                color = textColor,
                fontSize = 15.sp,
                textAlign = TextAlign.Start
            )
        }
    }
}

/** Compose дотор Context авах аюулгүй туслах. */
@Composable
fun LocalContextSafe() = androidx.compose.ui.platform.LocalContext.current
