package com.example.tic_tac_toe_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.UUID
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.tic_tac_toe_app.ui.theme.Tic_tac_toe_AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var gameClient by mutableStateOf<GameClient?>(null)
    // private var isMultiplayerGame by mutableStateOf(false) // Will be managed inside Composable
    // private var localPlayerSymbol by mutableStateOf("X") // Will be managed inside Composable
    // private var gameId by mutableStateOf<String?>(null) // Will be managed inside Composable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Tic_tac_toe_AppTheme {
                TicTacToeGame(
                    gameClient = gameClient,
                    onGameClientChange = { newClient -> gameClient = newClient }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gameClient?.close()
    }
}


@Composable
fun TicTacToeGame(
    gameClient: GameClient?,
    onGameClientChange: (GameClient?) -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.MainGame) }
    var gameIdForQr by remember { mutableStateOf<String?>(null) }

    var board by remember { mutableStateOf(List(3) { MutableList(3) { "" } }) }
    var currentPlayer by remember { mutableStateOf("X") }
    var winner by remember { mutableStateOf<String?>(null) }
    var isDraw by remember { mutableStateOf(false) } // Added for draw state
    var notification by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Multiplayer specific states
    var isMultiplayerGame by remember { mutableStateOf(false) }
    var localPlayerSymbol by remember { mutableStateOf("X") } // Default for host
    var activeGameId by remember { mutableStateOf<String?>(null) }
    var connectionStatus by remember { mutableStateOf("Not Connected") } // New state for connection


    when (currentScreen) {
        Screen.MainGame -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp), // Added modifier
                horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround // Adjusted for better spacing
    ) {
        // Game Title Text (Implicitly TicTacToeGame)

        if (isMultiplayerGame) {
            activeGameId?.let { Text("Game ID: $it") }
            Text("You are Player: $localPlayerSymbol")
            Text("Connection: $connectionStatus")
        }

        val statusText = winner?.let { // Handles winner and draw for status
            if (it == "TIE") "It's a Draw!" else "Winner: $it"
        } ?: if (isDraw) "It's a Draw!" else "Current Player: $currentPlayer"
        BasicText(text = statusText)

        notification?.let {
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(text = "Info: $it", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Board(board, onCellClicked = { row, col ->
            if (isMultiplayerGame) {
                if (currentPlayer == localPlayerSymbol && board[row][col].isEmpty() && winner == null && !isDraw) {
                    coroutineScope.launch {
                        gameClient?.sendMove(Move(row, col, localPlayerSymbol))
                    }
                } else if (currentPlayer != localPlayerSymbol) {
                    notification = "Not your turn."
                } else if (board[row][col].isNotEmpty()) {
                    notification = "Cell already taken."
                } else if (winner != null || isDraw) {
                    notification = "Game is already over."
                }
            } else { // Single player logic
                if (board[row][col].isEmpty() && winner == null && !isDraw) {
                    board = board.mapIndexed { r, rowItems ->
                        rowItems.mapIndexed { c, item ->
                            if (r == row && c == col) currentPlayer else item
                        }.toMutableList()
                    }
                    val newWinner = checkWinner(board, isMultiplayerGame)
                    if (newWinner == "TIE") {
                        isDraw = true
                        winner = null // Ensure winner is null if it's a draw
                    } else {
                        winner = newWinner
                    }
                    if (winner == null && !isDraw) { // Only switch player if game not over
                         currentPlayer = if (currentPlayer == "X") "O" else "X"
                    }
                }
            }
        })

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons section
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!isMultiplayerGame) {
                Button(
                    onClick = {
                        // Reset local single player game
                        board = List(3) { MutableList(3) { "" } }
                        currentPlayer = "X"
                        winner = null
                        isDraw = false
                        notification = null
                    },
                    // Enable reset if game has started (a move made) or game is over
                    enabled = board.any { row -> row.any { it.isNotEmpty() } } || winner != null || isDraw
                ) {
                    Text("Reset Game")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { // Create Game & Show QR
                    val newGameId = UUID.randomUUID().toString()
                    activeGameId = newGameId
                    gameIdForQr = newGameId
                    isMultiplayerGame = true
                    localPlayerSymbol = "X" // Host is X
                    connectionStatus = "Creating game..." // Initial status
                    val newClient = GameClient()
                    onGameClientChange(newClient) // Update MainActivity's client

                    coroutineScope.launch {
                        connectionStatus = "Connecting as Player X..."
                        newClient.connect(
                            host = "10.0.2.2", // Android emulator localhost
                            port = 8080,
                            path = "/game/$newGameId",
                            onGameStateUpdate = { gameState ->
                                board = gameState.board
                                currentPlayer = gameState.currentPlayer
                                winner = gameState.winner
                                isDraw = gameState.isDraw // Update isDraw from server
                                if (winner != null || isDraw) {
                                    connectionStatus = if (winner == localPlayerSymbol) "You won!"
                                    else if (winner != null && winner != "TIE") "Player $winner won."
                                    else "It's a Draw!"
                                } else {
                                    connectionStatus = if (gameState.currentPlayer == localPlayerSymbol) {
                                        "Your turn (Player $localPlayerSymbol)."
                                    } else {
                                        "Opponent's turn (Player ${gameState.currentPlayer})."
                                    }
                                }
                                // Heuristic for opponent joined, can be refined with specific server messages
                                if (gameState.board.flatten().count { it.isEmpty() } < 8 &&
                                    (connectionStatus.contains("Waiting") || connectionStatus.contains("Connecting"))) {
                                     if (gameState.currentPlayer != localPlayerSymbol) connectionStatus = "Opponent found! Opponent's turn."
                                     else connectionStatus = "Opponent found! Your turn."
                                }
                                notification = null // Clear previous notification
                            },
                            onNotification = { message ->
                                notification = message
                                // Update connectionStatus based on specific notifications
                                if (message.contains("Player O joined", ignoreCase = true)) {
                                    connectionStatus = "Player O joined! Current player: $currentPlayer"
                                } else if (message.contains("Waiting for player O", ignoreCase = true)) {
                                    connectionStatus = "Waiting for Player O..."
                                } else if (message.contains("Connection error")) {
                                    connectionStatus = "Connection failed: $message"
                                }
                            }
                        )
                    }
                    currentScreen = Screen.QrGenerator // Navigate to QR generator
                }) {
                    Text("Create Game & Show QR")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    currentScreen = Screen.QrScanner // Navigate to QR scanner
                }) {
                    Text("Join Game via QR")
                }
            } else { // Multiplayer game is active or was active
                Button(onClick = { // Leave Game button
                    gameClient?.close()
                    onGameClientChange(null)
                    isMultiplayerGame = false
                    activeGameId = null
                    gameIdForQr = null // Reset gameIdForQr as well
                    board = List(3) { MutableList(3) { "" } } // Reset board
                    currentPlayer = "X" // Reset player
                    winner = null
                    isDraw = false
                    notification = null
                    connectionStatus = "Not Connected"
                    currentScreen = Screen.MainGame // Ensure back to main screen
                }) {
                    Text("Leave Game")
                }
                // Reset button is implicitly hidden in multiplayer as per original logic
                // and current requirements (no separate MP reset button)
            }
        }
    }
        }
        Screen.QrGenerator -> {
            gameIdForQr?.let { gameId ->
                QrCodeGeneratorScreen(gameId = gameId, onDismiss = {
                    currentScreen = Screen.MainGame
                    // If user dismisses QR screen before opponent joins, update status
                    if (connectionStatus.contains("Creating game...")) { // Check if game was just being created
                         connectionStatus = "Waiting for Opponent (Player O)..."
                    }
                })
            } ?: run { // Should ideally not be reached if gameIdForQr is always set before navigating
                BasicText("Error: No Game ID for QR code. Please go back.")
                Button(onClick = { currentScreen = Screen.MainGame }) { Text("Back") }
            }
        }
        Screen.QrScanner -> {
            val context = LocalContext.current
            var hasCameraPermissionState by remember { // Renamed for clarity
                mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            }
            val cameraPermissionLauncher = rememberLauncherForActivityResult( // Renamed for clarity
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { granted ->
                    hasCameraPermissionState = granted
                    if (!granted) {
                        notification = "Camera permission denied. Cannot scan QR."
                        currentScreen = Screen.MainGame // Or stay and show message to grant permission
                    }
                }
            )

            // This LaunchedEffect will trigger when the composable enters the composition
            LaunchedEffect(key1 = Unit) { // key1 = Unit ensures it runs once on entry
                if (!hasCameraPermissionState) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            if (hasCameraPermissionState) {
                QrCodeScannerScreen(
                    onQrCodeScanned = { scannedGameId ->
                        activeGameId = scannedGameId
                        isMultiplayerGame = true
                        localPlayerSymbol = "O" // Joiner is O
                        connectionStatus = "Joining game ID: $scannedGameId..." // Initial status
                        val newClient = GameClient()
                        onGameClientChange(newClient)

                        coroutineScope.launch {
                            connectionStatus = "Connecting as Player O..."
                            newClient.connect(
                                host = "10.0.2.2", // Android emulator localhost
                                port = 8080,
                                path = "/game/$scannedGameId",
                                onGameStateUpdate = { gameState ->
                                    board = gameState.board
                                    currentPlayer = gameState.currentPlayer
                                    winner = gameState.winner
                                    isDraw = gameState.isDraw // Update isDraw from server
                                     if (winner != null || isDraw) {
                                         connectionStatus = if (winner == localPlayerSymbol) "You won!"
                                         else if (winner != null && winner != "TIE") "Player $winner won."
                                         else "It's a Draw!"
                                     } else {
                                        connectionStatus = if (gameState.currentPlayer == localPlayerSymbol) {
                                            "Your turn (Player $localPlayerSymbol)."
                                        } else {
                                            "Opponent's turn (Player ${gameState.currentPlayer})."
                                        }
                                    }
                                     // Heuristic for game started
                                     if (gameState.board.flatten().count { it.isEmpty() } < 8 &&
                                         (connectionStatus.contains("Waiting") || connectionStatus.contains("Connecting"))) {
                                         if (gameState.currentPlayer != localPlayerSymbol) connectionStatus = "Game started! Opponent's turn."
                                         else connectionStatus = "Game started! Your turn."
                                    }
                                    notification = null
                                },
                                onNotification = { message ->
                                    notification = message
                                    if (message.contains("Connection error")) {
                                        connectionStatus = "Connection failed: $message"
                                    } else if (message.contains("Successfully joined", ignoreCase = true) ||
                                               message.contains("Player X is already connected", ignoreCase = true) ) { // Server might send this if host is already there
                                         connectionStatus = "Connected! Waiting for game state..."
                                    }
                                }
                            )
                        }
                        currentScreen = Screen.MainGame // Navigate back to game
                    },
                    onDismiss = {
                        currentScreen = Screen.MainGame
                        notification = "QR scanning cancelled."
                    }
                )
            } else {
                // UI to show while permission is being requested or if denied
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    BasicText("Camera permission is required to scan QR codes for joining a game.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        // Try to launch permission request again, or guide user to settings
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }) {
                        Text("Grant Camera Permission")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { currentScreen = Screen.MainGame }) {
                        Text("Cancel and Go Back")
                    }
                }
            }
        }
    }
}

enum class Screen {
    MainGame,
    QrGenerator,
    QrScanner
}

@Composable
fun QrCodeGeneratorScreen(gameId: String, onDismiss: () -> Unit) {
    val size = 512 // Desired size of the QR code image
    val qrCodeBitmap = remember(gameId) { // Regenerate if gameId changes
        try {
            val writer = QRCodeWriter()
            // Create a map for hints, though often not strictly necessary for simple QR codes
            val hints = mutableMapOf<com.google.zxing.EncodeHintType, Any>()
            // hints[com.google.zxing.EncodeHintType.MARGIN] = 1 // Example: Set margin

            val bitMatrix = writer.encode(gameId, BarcodeFormat.QR_CODE, size, size, hints)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null // Return null if error occurs
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BasicText("Scan QR to join game ID: $gameId") // Show game ID for confirmation
        Spacer(modifier = Modifier.height(16.dp))
        qrCodeBitmap?.asImageBitmap()?.let {
            Image(
                bitmap = it,
                contentDescription = "Game ID QR Code for $gameId",
                modifier = Modifier.fillMaxSize(0.7f).border(1.dp, Color.Gray) // Added border
            )
        } ?: run {
            BasicText("Generating QR code...") // Show while bitmap is null (e.g. during generation)
            CircularProgressIndicator() // Show a loading spinner
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDismiss) {
            Text("Back to Game Screen") // Changed from "Dismiss" for clarity
        }
    }
}

@Composable
fun QrCodeScannerScreen(onQrCodeScanned: (String) -> Unit, onDismiss: () -> Unit) {
    // Camera permission is now handled in TicTacToeGame before calling this composable
    // This composable now assumes permission has been granted.

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract(), // From zxing-android-embedded
        onResult = { result ->
            if (result.contents != null) {
                onQrCodeScanned(result.contents)
            } else {
                onDismiss() // Or show a message that scanning failed/was cancelled
            }
        }
    )

    // Automatically launch scanner when this composable is shown
    LaunchedEffect(Unit) {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Scan a QR code to join a game")
        options.setCameraId(0)
        options.setBeepEnabled(true)
        options.setBarcodeImageEnabled(false) // Don't need to show the image in the result
        scanLauncher.launch(options)
    }

    // Display some content while scanner is active or if it fails to launch
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BasicText("QR Code Scanner is active...")
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDismiss) {
            Text("Cancel Scanning")
        }
    }
}


fun checkWinner(board: List<List<String>>, isMultiplayer: Boolean): String? {
    // Check rows and columns
    for (i in 0..2) {
        if (board[i][0].isNotEmpty() && board[i][0] == board[i][1] && board[i][0] == board[i][2]) {
            return board[i][0]
        }
        if (board[0][i].isNotEmpty() && board[0][i] == board[1][i] && board[0][i] == board[2][i]) {
            return board[0][i]
        }
    }
    // Check diagonals
    if (board[0][0].isNotEmpty() && board[0][0] == board[1][1] && board[0][0] == board[2][2]) {
        return board[0][0]
    }
    if (board[0][2].isNotEmpty() && board[0][2] == board[1][1] && board[0][2] == board[2][0]) {
        return board[0][2]
    }

    // Check for draw in single-player mode
    if (!isMultiplayer && board.all { row -> row.all { it.isNotEmpty() } }) {
        return "TIE" // Representing a draw
    }

    return null
}

@Composable
fun Board(board: List<List<String>>, onCellClicked: (Int, Int) -> Unit) {
    Column {
        for (row in board.indices) {
            Row {
                for (col in board[row].indices) {
                    Cell(
                        value = board[row][col],
                        onClick = { onCellClicked(row, col) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun Cell(value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .padding(4.dp).border(color = Color.Black, width = 1.dp)
            .clickable(onClick = onClick)
    ) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium)
    }
}



