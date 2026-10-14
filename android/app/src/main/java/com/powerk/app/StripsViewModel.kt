package com.powerk.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Holds the strip snapshot that is polled every 5s, plus the outlet command/icon mutations that
 * depend on it. Obtained once via `viewModel()` in [PowerkNavigation], which (since it is not
 * itself hosted inside a NavHost destination) resolves to the Activity's ViewModelStoreOwner, so
 * there is a single shared instance for the whole app rather than one per tab.
 *
 * This state used to live as `remember {}` vars directly inside `PowerkNavigation()`'s own
 * composable body, with the polling loop and command/icon closures declared there too. That meant
 * every screen that reads `snapshot`/`loading`/`error` had to do so via a plain closure recreated
 * on every recomposition of the *root* composable, and the root itself owned mutable state that
 * (depending on where it's read) can force broader recomposition than just the active tab's
 * content. Moving it into a ViewModel means `PowerkNavigation()` only ever holds a stable
 * reference to this object - it does not read `snapshot`/`loading`/`error` itself - so a poll
 * tick can only invalidate the specific `composable(route) { ... }` block that actually reads
 * those properties (i.e. whichever tab is currently on screen), not the Scaffold/bottom-nav/
 * NavHost structure itself.
 */
class StripsViewModel : ViewModel() {
    var snapshot by mutableStateOf<Snapshot?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    val pending = mutableStateMapOf<String, Boolean>()
    val snackbar = SnackbarHostState()

    init {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(5000)
            }
        }
    }

    suspend fun refresh() {
        if (!Server.configured) return
        loading = true
        try {
            snapshot = Api.snapshot(Server.base, Server.token)
            error = null
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            snapshot = null
        }
        loading = false
    }

    fun command(mac: String, outlet: Int, on: Boolean) {
        val keys = if (outlet == 0) (1..4).map { "$mac:$it" } else listOf("$mac:$outlet")
        if (keys.any { pending.containsKey(it) }) return
        keys.forEach { pending[it] = on }
        viewModelScope.launch {
            try {
                Api.setOutlet(Server.base, Server.token, mac, outlet, on)
                refresh()
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "command failed")
            } finally {
                keys.forEach { pending.remove(it) }
            }
        }
    }

    fun updateIcon(mac: String, outlet: Int?, icon: String) {
        viewModelScope.launch {
            try {
                if (outlet == null) Api.updateStripIcon(Server.base, Server.token, mac, icon)
                else Api.updateOutletIcon(Server.base, Server.token, mac, outlet, icon)
                refresh()
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "icon update failed")
            }
        }
    }
}
