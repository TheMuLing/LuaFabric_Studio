package muling.views.tool.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.tarifchakder.ktoast.ToastState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class NonBlockingToastState(
    val originalToastState: ToastState,
    private val coroutineScope: CoroutineScope
) {
    fun showToast(message: String) {
        coroutineScope.launch {
            originalToastState.currentToastData?.dismiss()
            originalToastState.showToast(message)
        }
    }
}

@Composable
fun rememberNonBlockingToastState(
    toastState: ToastState = remember { ToastState() }
): NonBlockingToastState {
    val scope = rememberCoroutineScope()
    return remember(toastState) {
        NonBlockingToastState(toastState, scope)
    }
}