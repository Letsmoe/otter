package com.letsmoe.otter.toasts

import com.letsmoe.otter.AppScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ToastKind { INFO, SUCCESS, ERROR }

/** A button on a toast, e.g. "Undo"; the toast closes when it is tapped. */
class ToastAction(val label: String, val onClick: () -> Unit)

data class Toast(val id: Long, val message: String, val kind: ToastKind, val action: ToastAction? = null)

/** App-wide transient messages ("Saved", "Alex joined Watch Together"); mirrors stores/notifications.ts. */
object Toasts {

    private const val AUTO_DISMISS_MILLISECONDS = 6000L

    private val mutableEntries = MutableStateFlow<List<Toast>>(emptyList())
    val entries: StateFlow<List<Toast>> = mutableEntries

    private var counter = 0L

    fun push(message: String, kind: ToastKind = ToastKind.INFO, action: ToastAction? = null) {
        counter += 1
        val toast = Toast(counter, message, kind, action)
        mutableEntries.update { current -> listOf(toast) + current }
        AppScope.launch {
            delay(AUTO_DISMISS_MILLISECONDS)
            dismiss(toast.id)
        }
    }

    fun dismiss(id: Long) {
        mutableEntries.update { current -> current.filter { toast -> toast.id != id } }
    }
}
