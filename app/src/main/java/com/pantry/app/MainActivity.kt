package com.pantry.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.pantry.app.ui.PantryNav
import com.pantry.app.ui.theme.PantryTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val sharedUrl = remember { sharedUrlFrom(intent) }
            PantryTheme {
                PantryNav(sharedUrl = sharedUrl)
            }
        }
    }

    /** Feature 2: a link shared from the browser lands straight in the importer. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun sharedUrlFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.ifBlank { null }
    }
}
