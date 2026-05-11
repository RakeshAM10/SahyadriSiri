package com.sahyadrisiri.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sahyadrisiri.BuildConfig
import com.sahyadrisiri.ui.glass.GlassPanel
import com.sahyadrisiri.ui.glass.glassClickable
import com.sahyadrisiri.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBotSheet(onClose: () -> Unit) {
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var isLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val client = remember { OkHttpClient() }

    // Auto-focus and auto-scroll
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = Color(0xFFF9F9F9),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(width = 36.dp, height = 5.dp).clip(CircleShape).background(Color.Black.copy(0.1f)))
        }
    ) {
        Column(Modifier.fillMaxHeight(0.9f).fillMaxWidth()) {
            // Header
            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Blue007AFF, Color(0xFF5856D6)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SmartToy, null, modifier = Modifier.size(24.dp), tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Sahyadri AI Assistant", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Neutral800)
                    Text("Always here to help", fontSize = 13.sp, color = Neutral500)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.background(Color.Black.copy(0.05f), CircleShape)) {
                    Icon(Icons.Default.Close, null, tint = Neutral600, modifier = Modifier.size(20.dp))
                }
            }

            HorizontalDivider(color = Color.Black.copy(0.05f))

            // Chat Messages Area
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.SmartToy, 
                            null, 
                            modifier = Modifier.size(64.dp).graphicsLayer(alpha = 0.1f), 
                            tint = Color.Black
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("How can I help you today?", color = Neutral400, fontSize = 16.sp)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(messages) { msg ->
                        val isUser = msg.role == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                Modifier
                                    .widthIn(max = 280.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 20.dp,
                                            topEnd = 20.dp,
                                            bottomStart = if (isUser) 20.dp else 4.dp,
                                            bottomEnd = if (isUser) 4.dp else 20.dp
                                        )
                                    )
                                    .background(if (isUser) Blue007AFF else Color(0xFFE9E9EB))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    msg.text, 
                                    fontSize = 15.sp, 
                                    color = if (isUser) Color.White else Neutral800, 
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                    
                    if (isLoading) {
                        item {
                            Row {
                                Box(
                                    Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp))
                                        .background(Color(0xFFE9E9EB)).padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    TypingIndicator()
                                }
                            }
                        }
                    }
                }
            }

            // Input Area
            Surface(
                color = Color.White,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .clip(RoundedCornerShape(24.dp)),
                        placeholder = { Text("Message...", color = Neutral400) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF2F2F7),
                            unfocusedContainerColor = Color(0xFFF2F2F7),
                            disabledContainerColor = Color(0xFFF2F2F7),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage(scope, client, inputText, messages, { inputText = "" }, { isLoading = it }) })
                    )
                    Spacer(Modifier.width(10.dp))
                    IconButton(
                        onClick = { sendMessage(scope, client, inputText, messages, { inputText = "" }, { isLoading = it }) },
                        enabled = inputText.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank() && !isLoading) Blue007AFF else Color(0xFFE9E9EB))
                    ) {
                        Icon(Icons.Default.Send, null, tint = if (inputText.isNotBlank()) Color.White else Neutral400, modifier = Modifier.size(22.dp).graphicsLayer(rotationZ = -45f))
                    }
                }
            }
        }
    }
}

private fun sendMessage(
    scope: kotlinx.coroutines.CoroutineScope,
    client: OkHttpClient,
    text: String,
    messages: androidx.compose.runtime.snapshots.SnapshotStateList<ChatMessage>,
    onClear: () -> Unit,
    onLoading: (Boolean) -> Unit
) {
    if (text.isBlank()) return
    val userMsg = text.trim()
    onClear()
    messages.add(ChatMessage(userMsg, "user"))
    onLoading(true)

    // Convert current messages to JSON array for context
    val contentsArray = JSONArray()
    for (msg in messages) {
        val role = if (msg.role == "user") "user" else "model"
        val partsArray = JSONArray().put(JSONObject().put("text", msg.text))
        contentsArray.put(JSONObject().put("role", role).put("parts", partsArray))
    }
    
    val requestBodyJson = JSONObject()
        .put("contents", contentsArray)
        .put("generationConfig", JSONObject()
            .put("temperature", 0.7)
            .put("topK", 40)
            .put("topP", 0.95)
            .put("maxOutputTokens", 1024)
        ).toString()

    val request = Request.Builder()
        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
        .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
        .build()

    scope.launch(Dispatchers.IO) {
        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val modelText = parts.getJSONObject(0).optString("text")
                        withContext(Dispatchers.Main) {
                            messages.add(ChatMessage(modelText, "model"))
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            messages.add(ChatMessage("I couldn't generate a response.", "model"))
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage("I couldn't generate a response.", "model"))
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    messages.add(ChatMessage("Error: HTTP ${response.code}", "model"))
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                messages.add(ChatMessage("Error: ${e.message ?: "Failed to connect"}", "model"))
            }
        } finally {
            withContext(Dispatchers.Main) {
                onLoading(false)
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val infiniteTransition = rememberInfiniteTransition(label = "typing")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )
            Box(Modifier.size(6.dp).clip(CircleShape).background(Neutral500.copy(alpha)))
        }
    }
}

data class ChatMessage(val text: String, val role: String)
