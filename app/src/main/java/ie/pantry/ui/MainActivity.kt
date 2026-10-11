package ie.pantry.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ie.pantry.PantryApplication
import ie.pantry.ui.recipeimport.ImportActions
import ie.pantry.ui.recipeimport.ImportScreen
import ie.pantry.ui.recipeimport.ImportViewModel
import ie.pantry.ui.recipeimport.ShareIntents
import ie.pantry.ui.theme.PantryTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ImportViewModel by viewModels {
        viewModelFactory {
            initializer { ImportViewModel((application as PantryApplication).container.recipeImporter) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation) restores its state from the ViewModel; replaying the intent would
        // overwrite it and re-announce an old share.
        if (savedInstanceState == null) readShare(intent)
        setContent {
            PantryTheme {
                val state by viewModel.state.collectAsState()
                val actions = remember { ImportActions.of(viewModel) }
                ImportScreen(state, actions)
            }
        }
    }

    /** A share from another app while this activity is already running arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShare(intent)
    }

    /**
     * Passes an `ACTION_SEND` text to the ViewModel. A launcher start or any other action calls nothing, so an
     * ordinary launch shows no notice, and a relaunch from Recents never replays an old share.
     */
    private fun readShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        viewModel.onShareReceived(ShareIntents.sharedText(intent))
    }
}
