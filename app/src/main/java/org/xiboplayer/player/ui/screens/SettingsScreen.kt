package org.xiboplayer.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.xiboplayer.player.model.CmsSettings

/**
 * Settings screen for CMS configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: CmsSettings?,
    onSave: (CmsSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var cmsUrl by remember { mutableStateOf(currentSettings?.address ?: "") }
    var cmsKey by remember { mutableStateOf(currentSettings?.key ?: "") }
    var displayId by remember { mutableStateOf(currentSettings?.displayId ?: "") }
    var displayName by remember { mutableStateOf(currentSettings?.displayName ?: "Android Xibo Player") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Xibo Player Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1a1a2e),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "CMS Connection",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            OutlinedTextField(
                value = cmsUrl,
                onValueChange = { cmsUrl = it },
                label = { Text("CMS URL") },
                placeholder = { Text("https://cms.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF4CAF50),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White
                )
            )

            OutlinedTextField(
                value = cmsKey,
                onValueChange = { cmsKey = it },
                label = { Text("CMS Server Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF4CAF50),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White
                )
            )

            OutlinedTextField(
                value = displayId,
                onValueChange = { displayId = it },
                label = { Text("Display ID / Hardware Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF4CAF50),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White
                )
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF4CAF50),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val settings = CmsSettings(
                        address = cmsUrl.trimEnd('/'),
                        key = cmsKey,
                        displayId = displayId,
                        displayName = displayName
                    )
                    onSave(settings)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                enabled = cmsUrl.isNotBlank() && cmsKey.isNotBlank() && displayId.isNotBlank()
            ) {
                Text("Save & Connect", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2a2a3e)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "How to get these values:",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "1. Log into your Xibo CMS as admin",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "2. Go to Display → Displays → Add Display",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "3. Copy the Server Key and Hardware Key",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "4. Enter your CMS URL (e.g. https://cms.example.com)",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
