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
        val openPermissions = intent.getStringExtra(EXTRA_OPEN) == OPEN_PERMISSIONS
        setContent {
            NyxTheme {
                AppNavigation(container, openPermissions)
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

    companion object {
        private const val EXTRA_OPEN = "open"
        private const val OPEN_PERMISSIONS = "permissions"

        /** [openPermissions]: start on S-03 to fix a lost permission (N-03), in a fresh task over Home. */
        fun intent(context: Context, openPermissions: Boolean): Intent {
            val intent = Intent(context, MainActivity::class.java)
            if (!openPermissions) return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent.putExtra(EXTRA_OPEN, OPEN_PERMISSIONS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }
}
