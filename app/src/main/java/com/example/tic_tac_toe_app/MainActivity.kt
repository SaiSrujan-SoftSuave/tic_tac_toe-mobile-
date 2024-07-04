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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.tic_tac_toe_app.ui.theme.Tic_tac_toe_AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Tic_tac_toe_AppTheme {
            TicTacToeGame()
            }
        }
    }
}


@Composable
fun TicTacToeGame() {
    var board by remember { mutableStateOf(List(3) { MutableList(3) { "" } }) }
    var currentPlayer by remember { mutableStateOf("X") }
    var winner by remember { mutableStateOf<String?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Board(board, onCellClicked = { row, col ->
            if (board[row][col].isEmpty() && winner == null) {
                board = board.mapIndexed { r, rowItems ->
                    rowItems.mapIndexed { c, item ->
                        if (r == row && c == col) currentPlayer else item
                    }.toMutableList()
                }
                currentPlayer = if (currentPlayer == "X") "O" else "X"
                winner = checkWinner(board)
            }
        })

        Spacer(modifier = Modifier.height(16.dp))

        BasicText(text = winner?.let { "Winner: $it" } ?: "Current Player: $currentPlayer")

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            board = List(3) { MutableList(3) { "" } }
            currentPlayer = "X"
            winner = null
        }, enabled = (winner != null) || board.all { row -> row.all { it.isNotEmpty() } }) {
            Text("Reset")
        }
    }
}

fun checkWinner(board: List<List<String>>): String? {
    // Check rows and columns
    for (i in 0..2) {
        // Check row
        if (board[i][0].isNotEmpty() && board[i][0] == board[i][1] && board[i][0] == board[i][2]) {
            return board[i][0]
        }
        // Check column
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



