package com.pantry.app.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pantry.app.data.db.ShoppingItemEntity
import com.pantry.app.data.db.ShoppingListEntity
import com.pantry.app.repo.ShoppingRepository
import com.pantry.app.shopping.Aisles
import com.pantry.app.ui.container
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Names and quantities for a Tesco run, kept in step by index. */
data class TescoQueue(val names: List<String>, val quantities: List<String>) {
    val size: Int get() = names.size
}

data class ShoppingUiState(
    val lists: List<ShoppingListEntity> = emptyList(),
    val activeList: ShoppingListEntity? = null,
    val itemsByAisle: List<Pair<String, List<ShoppingItemEntity>>> = emptyList(),
    val remaining: Int = 0,
    val total: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModel(private val repo: ShoppingRepository) : ViewModel() {

    private val requestedListId = MutableStateFlow<String?>(null)

    private val activeList: StateFlow<ShoppingListEntity?> =
        combine(repo.observeLists(), requestedListId) { lists, requested ->
            lists.firstOrNull { it.id == requested } ?: lists.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<ShoppingUiState> =
        combine(
            repo.observeLists(),
            activeList.flatMapLatest { list ->
                if (list == null) flowOf(emptyList()) else repo.observeItems(list.id)
            },
            activeList
        ) { lists, items, active ->
            ShoppingUiState(
                lists = lists,
                activeList = active,
                itemsByAisle = items
                    .groupBy { it.aisle }
                    .toList()
                    .sortedBy { (aisle, _) -> Aisles.walkingOrder(aisle) }
                    .map { (aisle, rows) -> aisle to rows.sortedWith(compareBy({ it.checked }, { it.position })) },
                remaining = items.count { !it.checked },
                total = items.size
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    private val _tescoQueue = MutableStateFlow<TescoQueue?>(null)
    /** Set when the user asks to fill a Tesco basket; consumed by the screen. */
    val tescoQueue: StateFlow<TescoQueue?> = _tescoQueue.asStateFlow()

    fun showList(listId: String?) { requestedListId.value = listId }

    fun setChecked(id: Long, checked: Boolean) = viewModelScope.launch { repo.setChecked(id, checked) }

    fun deleteItem(id: Long) = viewModelScope.launch { repo.deleteItem(id) }

    fun addManual(text: String) = viewModelScope.launch {
        activeList.value?.let { repo.addManualItem(it.id, text) }
    }

    fun deleteActiveList() = viewModelScope.launch {
        activeList.value?.let { repo.deleteList(it.id) }
        requestedListId.value = null
    }

    /**
     * Feature 9: the unchecked items become the Tesco queue, in the same walking
     * order the list is drawn in.
     *
     * The quantity travels with each item rather than being dropped. Tesco sells
     * packs, not grams, so the shopper is the one converting "600 g" into two
     * tins -- which they can only do if the figure is in front of them.
     */
    fun prepareTescoRun() = viewModelScope.launch {
        val list = activeList.value ?: return@launch
        val rows = repo.uncheckedItems(list.id)
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name.trim().lowercase() }
        _tescoQueue.value = TescoQueue(
            names = rows.map { it.name.trim() },
            quantities = rows.map { it.displayQuantity.trim() }
        )
    }

    fun consumeTescoQueue() { _tescoQueue.value = null }

    /** Ticks off whatever a finished Tesco run reported as added. */
    fun markAdded(names: List<String>) = viewModelScope.launch {
        val list = activeList.value ?: return@launch
        repo.checkOff(list.id, names)
    }

    /** Plain-text export, for pasting into a message or another app. */
    fun asPlainText(state: ShoppingUiState): String = buildString {
        appendLine(state.activeList?.name ?: "Shopping list")
        state.itemsByAisle.forEach { (aisle, items) ->
            appendLine()
            appendLine(aisle)
            items.forEach { item ->
                val tick = if (item.checked) "x" else " "
                val qty = if (item.displayQuantity.isBlank()) "" else " - ${item.displayQuantity}"
                appendLine("[$tick] ${item.name}$qty")
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ShoppingViewModel(container.shopping) }
        }
    }
}
