package ie.pantry.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ie.pantry.R

/** The F1 launch screen: the app name and nothing else. Real screens arrive in later features. */
@Composable
fun PlaceholderScreen() {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(R.string.app_name))
        }
    }
}
