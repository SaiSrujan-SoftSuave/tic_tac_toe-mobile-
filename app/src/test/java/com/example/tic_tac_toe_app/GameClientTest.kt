package com.example.tic_tac_toe_app

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter


// Helper for QR Code Generation Logic (refactored from MainActivity/QrCodeGeneratorScreen)
object QrCodeUtil {
    fun generateQrBitMatrix(gameId: String, size: Int = 512): BitMatrix? {
        return try {
            val hints = mutableMapOf<com.google.zxing.EncodeHintType, Any>()
            QRCodeWriter().encode(gameId, BarcodeFormat.QR_CODE, size, size, hints)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}


@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Use per-class lifecycle for @BeforeAll if needed
class GameClientTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockEngine: MockEngine
    private lateinit var gameClient: GameClient
    private var receivedGameState: GameState? = null
    private var receivedNotification: String? = null
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        receivedGameState = null
        receivedNotification = null
        // Default setup for a responsive mock engine
        mockEngine = MockEngine { request ->
             respondWebSocket { // Simulates successful WebSocket upgrade by default
                // Can be overridden in specific tests by re-assigning mockEngine
            }
        }
        gameClient = GameClient(engine = mockEngine)
    }

    @Test
    fun `connect should establish connection and process initial GameState`() = testScope.runTest {
        val initialBoard = List(3) { MutableList(3) { "" } }.apply { this[0][0] = "X" }
        val initialGameState = GameState(board = initialBoard, currentPlayer = "O", winner = null, isDraw = false)
        val serverMessage = GameMessage.CurrentGameState(initialGameState)

        mockEngine = MockEngine { request ->
            if (request.url.encodedPath == "/game/testConnect") {
                respondWebSocket {
                    send(Frame.Text(json.encodeToString(GameMessage.serializer(), serverMessage)))
                }
            } else {
                respondError(HttpStatusCode.NotFound)
            }
        }
        gameClient = GameClient(engine = mockEngine) // Re-initialize with specific engine

        var gameStateUpdateCalled = false
        gameClient.connect("localhost", 8080, "/game/testConnect",
            onGameStateUpdate = { state ->
                receivedGameState = state
                gameStateUpdateCalled = true
            },
            onNotification = { receivedNotification = it }
        )
        // Advance time to allow coroutines launched in connect to execute
        advanceTimeBy(100) // Adjust as necessary, or use more robust synchronization

        assertTrue(gameStateUpdateCalled, "onGameStateUpdate should be called")
        assertNotNull(receivedGameState, "Received game state should not be null")
        assertEquals("O", receivedGameState?.currentPlayer)
        assertEquals("X", receivedGameState?.board?.get(0)?.get(0))
    }

    @Test
    fun `sendMove should serialize and send PlayerMove message`() = testScope.runTest {
        val move = Move(row = 1, col = 1, player = "X")
        val expectedMessage = GameMessage.PlayerMove(move)
        val outgoingFrames = Channel<Frame>(Channel.UNLIMITED) // To capture outgoing frames

        mockEngine = MockEngine { request ->
             respondWebSocket { // Generic handler, specific frames are checked
                this.outgoing = outgoingFrames // Assign the channel to capture frames
                // Keep the session open for a bit or until a specific condition
                delay(500) // Keep alive to allow sendMove to complete
            }
        }
        gameClient = GameClient(engine = mockEngine)

        // Establish connection first (simplified, assuming it works)
        launch { // Launch connect in a separate coroutine so it doesn't block sendMove
            gameClient.connect("localhost", 8080, "/game/testSend",
                onGameStateUpdate = { receivedGameState = it },
                onNotification = { receivedNotification = it }
            )
        }
        advanceTimeBy(100) // Allow connection to establish

        gameClient.sendMove(move)
        advanceTimeBy(100) // Allow sendMove to process

        val sentFrame = outgoingFrames.tryReceive().getOrNull()
        assertNotNull(sentFrame, "A frame should have been sent")
        assertTrue(sentFrame is Frame.Text, "Sent frame should be Text")

        val sentText = (sentFrame as Frame.Text).readText()
        val decodedMessage = json.decodeFromString(GameMessage.serializer(), sentText)
        assertTrue(decodedMessage is GameMessage.PlayerMove, "Sent message should be PlayerMove type")
        assertEquals(expectedMessage.move, (decodedMessage as GameMessage.PlayerMove).move)
    }

    @Test
    fun `should receive and process GameState updates`() = testScope.runTest {
        val updatedBoard = List(3) { MutableList(3) { "" } }.apply { this[1][1] = "O" }
        val updatedGameState = GameState(board = updatedBoard, currentPlayer = "X", winner = null, isDraw = false)
        val serverMessage = GameMessage.CurrentGameState(updatedGameState)

        val serverFrames = Channel<Frame>(Channel.UNLIMITED)
        serverFrames.trySend(Frame.Text(json.encodeToString(GameMessage.serializer(), serverMessage)))

        mockEngine = MockEngine {
            respondWebSocket {
                for (frame in serverFrames) { // Send frames from the channel
                    send(frame.copy()) // send a copy to avoid issues with frame reuse
                }
            }
        }
        gameClient = GameClient(engine = mockEngine)

        var gameStateUpdateCalled = false
        gameClient.connect("localhost", 8080, "/game/testReceiveState",
            onGameStateUpdate = { state ->
                receivedGameState = state
                gameStateUpdateCalled = true
            },
            onNotification = { receivedNotification = it }
        )
        advanceTimeBy(100)

        assertTrue(gameStateUpdateCalled)
        assertNotNull(receivedGameState)
        assertEquals("X", receivedGameState?.currentPlayer)
        assertEquals("O", receivedGameState?.board?.get(1)?.get(1))
    }

    @Test
    fun `should receive and process GameNotification`() = testScope.runTest {
        val notificationMessage = "Player O has joined!"
        val serverMessage = GameMessage.GameNotification(notificationMessage)
        val serverFrames = Channel<Frame>(Channel.UNLIMITED)
        serverFrames.trySend(Frame.Text(json.encodeToString(GameMessage.serializer(), serverMessage)))


        mockEngine = MockEngine {
            respondWebSocket {
                 for (frame in serverFrames) {
                    send(frame.copy())
                }
            }
        }
        gameClient = GameClient(engine = mockEngine)

        var notificationCalled = false
        gameClient.connect("localhost", 8080, "/game/testNotification",
            onGameStateUpdate = { receivedGameState = it },
            onNotification = {
                receivedNotification = it
                notificationCalled = true
            }
        )
        advanceTimeBy(100)

        assertTrue(notificationCalled)
        assertEquals(notificationMessage, receivedNotification)
    }
    
    @Test
    fun `connect should handle connection error and call onNotification`() = testScope.runTest {
        mockEngine = MockEngine {
            // Simulate a connection error
            respondError(HttpStatusCode.InternalServerError, "Simulated connection error")
        }
        gameClient = GameClient(engine = mockEngine)

        var notificationMessage: String? = null
        gameClient.connect("localhost", 8080, "/game/error",
            onGameStateUpdate = { },
            onNotification = { message -> notificationMessage = message }
        )
        advanceTimeBy(100)

        assertNotNull(notificationMessage)
        assertTrue(notificationMessage!!.contains("Connection error"), "Notification message should indicate a connection error. Actual: $notificationMessage")
    }

    @Test
    fun `close should close session and client`() = testScope.runTest {
        val outgoingFrames = Channel<Frame>(Channel.UNLIMITED)
        var closeReason: CloseReason? = null

         mockEngine = MockEngine { request ->
            respondWebSocket {
                this.outgoing = outgoingFrames
                try {
                    // Keep the connection open until closed by the client
                    for (frame in incoming) {
                        // Process incoming if necessary for test, or ignore
                    }
                } finally {
                    // Capture the close reason when the client closes the session from its end.
                    // This part is tricky as the server side of MockEngine doesn't directly expose this easily.
                    // We are more interested in the client's behavior.
                }
            }
        }
        gameClient = GameClient(engine = mockEngine)

        // Establish a connection
        launch {
            gameClient.connect("localhost", 8080, "/game/testClose",
                onGameStateUpdate = {},
                onNotification = {}
            )
        }
        advanceTimeBy(100) // Allow connection

        gameClient.close() // Call close on the client
        advanceTimeBy(100) // Allow close to propagate

        // Check if the client's internal session is null (implementation detail, but useful)
        // This requires making 'session' in GameClient accessible for testing, or inferring closure.
        // For now, we'll infer from the fact that Ktor's client.close() was called.
        // A more robust test would be to check if the mockEngine's session was closed.
        // Ktor's MockEngine doesn't directly expose an easy way to check if the *server side* of the mock session was closed by the client.
        // However, client.close() should ensure that future calls to sendMove fail or that the client's resources are released.

        // Attempting to send a move after close should ideally fail or not send
        val moveAfterClose = Move(0,0,"X")
        gameClient.sendMove(moveAfterClose)
        advanceTimeBy(100)
        
        val frameAfterClose = outgoingFrames.tryReceive().getOrNull()
        // This assertion depends on whether sendMove checks isActive before sending.
        // If it does, and session is nullified by close(), then no frame should be sent.
        // If GameClient's sendMove doesn't re-check session status after close, this might pass.
        // The current GameClient has a check: if (currentSession != null && currentSession.isActive)
        assertNull(frameAfterClose, "No frame should be sent after client is closed.")
    }

    // --- QR Code Logic Tests ---
    @Test
    fun `generateQrBitMatrix should produce non-null BitMatrix for valid gameId`() {
        val gameId = "testGame123"
        val bitMatrix = QrCodeUtil.generateQrBitMatrix(gameId)
        assertNotNull(bitMatrix, "BitMatrix should not be null for a valid game ID.")
        assertTrue(bitMatrix!!.width > 0 && bitMatrix.height > 0, "BitMatrix dimensions should be positive.")
    }

    @Test
    fun `generateQrBitMatrix should produce consistent BitMatrix for same gameId`() {
        val gameId = "consistentGameId"
        val matrix1 = QrCodeUtil.generateQrBitMatrix(gameId)
        val matrix2 = QrCodeUtil.generateQrBitMatrix(gameId)
        assertNotNull(matrix1)
        assertNotNull(matrix2)
        assertEquals(matrix1!!.toString(), matrix2!!.toString(), "BitMatrix should be consistent for the same game ID.")
    }

    @Test
    fun `generateQrBitMatrix should handle empty gameId gracefully`() {
        // Depending on QRCodeWriter behavior, this might throw or return a specific matrix.
        // For this test, we'll assume it should still produce a non-null (possibly trivial) matrix,
        // or handle it without crashing. The current QrCodeUtil returns null on exception.
        val gameId = ""
        val bitMatrix = QrCodeUtil.generateQrBitMatrix(gameId)
        // Based on current QrCodeUtil, ZXing writer might throw an exception for empty content,
        // which our utility catches and returns null.
        assertNull(bitMatrix, "BitMatrix should be null or handle empty string gracefully as per QrCodeUtil.")
    }
}

// Helper to advance time in TestScope for coroutines
fun TestScope.advanceTimeBy(delayTimeMillis: Long) {
    this.testScheduler.advanceTimeBy(delayTimeMillis)
    this.testScheduler.runCurrent() // Executes any tasks scheduled for the current virtual time
}
