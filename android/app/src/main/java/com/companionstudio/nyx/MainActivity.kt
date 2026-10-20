package com.companionstudio.nyx

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.companionstudio.nyx.service.PetService
import com.companionstudio.nyx.ui.theme.NyxTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PetApp).container
        val open = Open.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_OPEN) } ?: Open.HOME
        setContent {
            NyxTheme {
                AppNavigation(container, open)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Every app open makes sure the pet service runs (D-70); the app is in front, so Android allows it
        val container = (application as PetApp).container
        lifecycleScope.launch {
            if (container.setupSettings.completed.first()) PetService.start(this@MainActivity)
        }
    }

    /** Where the app opens from a notification or the pet's panel. */
    enum class Open {
        /** Home, or wherever the app already is. */
        HOME,

        /** S-03 to fix a lost permission (N-03), in a fresh task over Home. */
        PERMISSIONS,

        /** Focus setup from the pet's panel (§14.5), in a fresh task over Home. */
        FOCUS,
    }

    companion object {
        private const val EXTRA_OPEN = "open"

        fun intent(context: Context, open: Open): Intent {
            val intent = Intent(context, MainActivity::class.java)
            if (open == Open.HOME) return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent.putExtra(EXTRA_OPEN, open.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }
}
