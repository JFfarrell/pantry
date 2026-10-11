package ie.pantry.ui.recipeimport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import ie.pantry.R

/** What the import screen can ask for. The share chooser is started from the screen itself, not through here. */
class ImportActions(
    val onFieldChange: (String) -> Unit,
    val onImport: () -> Unit,
    val onCancel: () -> Unit,
    val onRetry: () -> Unit,
    val onTryAnother: () -> Unit,
    val onImportAnother: () -> Unit,
    val onBack: () -> Unit,
) {
    companion object {
        fun of(viewModel: ImportViewModel): ImportActions = ImportActions(
            onFieldChange = viewModel::onFieldChange,
            onImport = viewModel::onImport,
            onCancel = viewModel::onCancel,
            onRetry = viewModel::onRetry,
            onTryAnother = viewModel::onTryAnother,
            onImportAnother = viewModel::onImportAnother,
            onBack = { viewModel.onBack() },
        )
    }
}

/** Semantics tags the screen tests read; they carry no content. */
internal object ImportTags {
    const val FIELD = "importField"
    const val FIELD_ERROR = "importFieldError"
    const val NOTICE = "importNotice"
    const val STATUS = "importStatus"
    const val ERROR = "importError"
    const val DRAFT_NOTICE = "importDraftNotice"
    const val DECLINE = "importDecline"
    const val EXPLAINER = "importExplainer"
}

private val MinTouchTarget = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(state: ImportUiState, actions: ImportActions) {
    val screen = state.screen
    BackHandler(enabled = screen is Screen.InProgress || screen is Screen.Failed || screen is Screen.Declined) {
        actions.onBack()
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NoticeText(state.notice)
            when (screen) {
                is Screen.Input -> {
                    UrlField(state, actions, readOnly = false, fieldError = screen.fieldError)
                    ImportButton(actions)
                }
                is Screen.InProgress -> {
                    UrlField(state, actions, readOnly = true, fieldError = false)
                    LiveText(ImportMessages.of(screen.phase), ImportTags.STATUS)
                    ActionButton(R.string.import_action_cancel, actions.onCancel)
                }
                is Screen.Failed -> {
                    UrlField(state, actions, readOnly = false, fieldError = false)
                    LiveText(ImportMessages.of(screen.message), ImportTags.ERROR)
                    if (screen.retryable) ActionButton(R.string.import_action_retry, actions.onRetry)
                    ImportButton(actions)
                }
                is Screen.Declined -> {
                    val context = LocalContext.current
                    LiveText(ImportMessages.of(Failure.NOT_RECOGNISED), ImportTags.DECLINE)
                    LiveText(R.string.import_decline_share_explainer, ImportTags.EXPLAINER)
                    ActionButton(R.string.import_action_retry, actions.onRetry)
                    ActionButton(R.string.import_action_try_another, actions.onTryAnother)
                    ActionButton(R.string.import_action_share) {
                        context.startActivity(ShareIntents.chooserFor(screen.url.value, context))
                    }
                }
                is Screen.Drafted -> {
                    LiveText(ImportMessages.draftNotice(screen.draft.completeness), ImportTags.DRAFT_NOTICE)
                    ActionButton(R.string.import_action_import_another, actions.onImportAnother)
                }
            }
        }
    }
}

/**
 * The URL field: labelled, a single line, a URI keyboard with Go, no autocorrect, the hint as supporting text and
 * the invalid-link message in its place. Focus is requested when [ImportUiState.focusSeq] changes after the first
 * composition, never on launch or after a share.
 */
@Composable
private fun UrlField(state: ImportUiState, actions: ImportActions, readOnly: Boolean, fieldError: Boolean) {
    val focusRequester = remember { FocusRequester() }
    var firstFocusSeq by remember { mutableStateOf(state.focusSeq) }
    LaunchedEffect(state.focusSeq) {
        if (state.focusSeq != firstFocusSeq) {
            firstFocusSeq = state.focusSeq
            focusRequester.requestFocus()
        }
    }
    OutlinedTextField(
        value = state.field,
        onValueChange = actions.onFieldChange,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag(ImportTags.FIELD),
        readOnly = readOnly,
        label = { Text(stringResource(R.string.import_label_link)) },
        singleLine = true,
        isError = fieldError,
        supportingText = {
            if (fieldError) {
                Text(
                    text = stringResource(R.string.import_error_invalid_url),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(ImportTags.FIELD_ERROR),
                )
            } else {
                Text(stringResource(R.string.import_hint))
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { actions.onImport() }),
    )
}

@Composable
private fun ImportButton(actions: ImportActions) = ActionButton(R.string.import_action_import, actions.onImport)

@Composable
private fun ActionButton(label: Int, onClick: () -> Unit) {
    val secondary = label != R.string.import_action_import && label != R.string.import_action_retry
    val modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget)
    if (secondary) {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(stringResource(label)) }
    } else {
        Button(onClick = onClick, modifier = modifier) { Text(stringResource(label)) }
    }
}

/** A message that assistive technology announces politely when it appears or changes. */
@Composable
private fun LiveText(message: Int, tag: String) {
    Text(
        text = stringResource(message),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag),
    )
}

/**
 * The notice slot. Each [Notice] carries a sequence number; when it changes, the text is first set to empty for one
 * frame and then to the constant, so a repeat of the same notice is a content change that is announced again.
 */
@Composable
private fun NoticeText(notice: Notice?) {
    if (notice == null) return
    val constant = stringResource(ImportMessages.of(notice))
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(notice.seq, constant) {
        shown = ""
        withFrameNanos { }
        shown = constant
    }
    Text(
        text = shown,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag(ImportTags.NOTICE),
    )
}
