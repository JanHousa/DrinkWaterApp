package com.example.myapp.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var showGoalDialog by remember { mutableStateOf(false) }
    var dailyGoal by remember { mutableStateOf(2000f) }
    var username by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sledování příjmu tekutin",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Vaše jméno") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (username.isNotBlank()) {
                    val prefs = context.getSharedPreferences("fluid_intake", Context.MODE_PRIVATE)
                    prefs.edit().putString("username", username).apply()
                    showGoalDialog = true
                }
            },
            enabled = username.isNotBlank()
        ) {
            Text("Pokračovat")
        }
    }
    
    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Nastavení denního cíle") },
            text = {
                Column {
                    Text("Zadejte svůj denní cíl příjmu tekutin (ml):")
                    OutlinedTextField(
                        value = dailyGoal.toString(),
                        onValueChange = { dailyGoal = it.toFloatOrNull() ?: 2000f },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val prefs = context.getSharedPreferences("fluid_intake", Context.MODE_PRIVATE)
                        prefs.edit().putFloat("daily_goal", dailyGoal).apply()
                        showGoalDialog = false
                        onLoginSuccess()
                    }
                ) {
                    Text("Uložit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("Zrušit")
                }
            }
        )
    }
} 