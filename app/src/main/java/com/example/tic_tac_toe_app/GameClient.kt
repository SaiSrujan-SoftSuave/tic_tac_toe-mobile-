package com.example.tic_tac_toe_app

import io.ktor.client.*
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Data classes for game messages
@Serializable
data class Move(val row: Int, val col: Int, val player: String)

@Serializable
data class GameState(
    val board: List<List<String>>,
    val currentPlayer: String,
    val winner: String? = null,
    val isDraw: Boolean = false
)

@Serializable
sealed class GameMessage {
    @Serializable
    data class PlayerMove(val move: Move) : GameMessage()
    @Serializable
    data class CurrentGameState(val state: GameState) : GameMessage()
    @Serializable
    data class GameNotification(val message: String) : GameMessage()
}

class GameClient(engine: HttpClientEngine = CIO.create()) { // Default to CIO, allow injection
    private val client = HttpClient(engine) { // Use the provided or default engine
        install(WebSockets)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private val clientScope = CoroutineScope(Dispatchers.IO + Job())

    // Using StateFlow for reactive updates (optional for now, but good practice)
    private val _gameStateFlow = MutableStateFlow<GameState?>(null)
    val gameStateFlow: StateFlow<GameState?> = _gameStateFlow.asStateFlow()

    private val _notificationFlow = MutableStateFlow<String?>(null)
    val notificationFlow: StateFlow<String?> = _notificationFlow.asStateFlow()

    suspend fun connect(
        host: String,
        port: Int,
        path: String,
        onGameStateUpdate: (GameState) -> Unit,
        onNotification: (String) -> Unit
    ) {
        try {
            client.webSocket(
                method = HttpMethod.Get,
                host = host,
                port = port,
                path = path
            ) {
                session = this
                // Launch a coroutine to listen for incoming messages
                clientScope.launch {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            try {
                                val gameMessage = Json.decodeFromString<GameMessage>(text)
                                when (gameMessage) {
                                    is GameMessage.CurrentGameState -> {
                                        _gameStateFlow.value = gameMessage.state
                                        onGameStateUpdate(gameMessage.state)
                                    }
                                    is GameMessage.GameNotification -> {
                                        _notificationFlow.value = gameMessage.message
                                        onNotification(gameMessage.message)
                                    }
                                    is GameMessage.PlayerMove -> {
                                        // Client typically doesn't receive PlayerMove unless it's an echo or error
                                        // For now, we can log or notify
                                        onNotification("Received PlayerMove message: ${gameMessage.move}")
                                    }
                                }
                            } catch (e: Exception) {
                                // Fallback for messages that are not GameMessage sealed type
                                // or if direct GameState is expected
                                try {
                                    val gameState = Json.decodeFromString<GameState>(text)
                                    _gameStateFlow.value = gameState
                                    onGameStateUpdate(gameState)
                                } catch (e2: Exception) {
                                     _notificationFlow.value = "Error deserializing message: ${e2.message}. Original: $text"
                                    onNotification("Error deserializing message: ${e2.message}. Original: $text")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _notificationFlow.value = "Connection error: ${e.message}"
            onNotification("Connection error: ${e.message}")
        }
    }

    suspend fun sendMove(move: Move) {
        val currentSession = session
        if (currentSession != null && currentSession.isActive) {
            try {
                val playerMoveMessage = GameMessage.PlayerMove(move)
                currentSession.sendSerialized(playerMoveMessage)
            } catch (e: Exception) {
                 _notificationFlow.value = "Error sending move: ${e.message}"
                // Notify UI about the error if needed via onNotification callback
            }
        } else {
            _notificationFlow.value = "Cannot send move: WebSocket session is not active."
             // Notify UI about the error if needed
        }
    }

    fun close() {
        clientScope.launch {
            session?.close()
            session = null
        }
        client.close()
    }
}
