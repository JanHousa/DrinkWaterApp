package com.example.myapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.myapp.ui.FluidIntakeScreen
import com.example.myapp.ui.LoginScreen
import com.example.myapp.ui.theme.MyAppTheme

class MainActivity : ComponentActivity() {
    private var isLoggedIn by mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Načtení stavu přihlášení při startu
        val prefs = getSharedPreferences("fluid_intake", Context.MODE_PRIVATE)
        isLoggedIn = prefs.getBoolean("is_logged_in", false)
        
        setContent {
            MyAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isLoggedIn) {
                        FluidIntakeScreen(
                            onLogout = {
                                val prefs = getSharedPreferences("fluid_intake", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("is_logged_in", false).apply()
                                isLoggedIn = false
                            }
                        )
                    } else {
                        LoginScreen(
                            onLoginSuccess = {
                                val prefs = getSharedPreferences("fluid_intake", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("is_logged_in", true).apply()
                                isLoggedIn = true
                            }
                        )
                    }
                }
            }
        }
    }
}