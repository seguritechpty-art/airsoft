package com.airsoft.tracker.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    isLoading: Boolean,
    error: String?,
    onCreateSquad: (String) -> Unit,
    onJoinSquad: (String, String) -> Unit,
) {
    var nick by remember { mutableStateOf("") }
    var squadCode by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("create") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🎯", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "AIRSOFT TRACKER",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Coordinación táctica en tiempo real",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = nick,
            onValueChange = { nick = it.take(20) },
            label = { Text("Nick / Nombre de operador") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = KeyboardCapitalization.Words
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        // Segmented control simplificado
        Row {
            FilterChip(
                selected = mode == "create",
                onClick = { mode = "create" },
                label = { Text("Crear partida") },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = mode == "join",
                onClick = { mode = "join" },
                label = { Text("Unirse") },
            )
        }

        Spacer(Modifier.height(16.dp))

        if (mode == "join") {
            OutlinedTextField(
                value = squadCode,
                onValueChange = { squadCode = it.uppercase().take(6) },
                label = { Text("Código de partida (6 caracteres)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (nick.isBlank()) return@Button
                if (mode == "create") onCreateSquad(nick)
                else if (squadCode.length == 6) onJoinSquad(nick, squadCode)
            },
            enabled = !isLoading && nick.isNotBlank() && (mode == "create" || squadCode.length == 6),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
            } else {
                Text(if (mode == "create") "CREAR PARTIDA" else "UNIRSE A PARTIDA", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Uso táctico: solo para equipos de Airsoft/MilSim autorizados",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}