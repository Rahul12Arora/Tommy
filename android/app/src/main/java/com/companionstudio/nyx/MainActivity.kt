package com.companionstudio.nyx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.companionstudio.nyx.ui.theme.NyxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PetApp).container
        setContent {
            NyxTheme {
                AppNavigation(container)
            }
        }
    }
}
