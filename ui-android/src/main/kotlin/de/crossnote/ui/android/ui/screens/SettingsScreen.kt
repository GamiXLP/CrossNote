package de.crossnote.ui.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    isSyncing: Boolean,
    onSync: (String, Int) -> Unit
) {
    var host by remember { mutableStateOf("10.0.2.2") } // Default for Android Emulator to host
    var port by remember { mutableStateOf("8085") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("App Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Synchronization", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Server Host") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing
                )
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Server Port") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing
                )
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = { onSync(host, port.toIntOrNull() ?: 8085) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sync Now")
                    }
                }
            }
        }
        
        Spacer(Modifier.weight(1f))
        Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall)
        Text("Developer: CrossNote Team", style = MaterialTheme.typography.bodySmall)
    }
}
