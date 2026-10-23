package dev.neoworks.otter.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.ShieldCheck
import com.adamglin.phosphoricons.fill.ShieldWarning
import dev.neoworks.otter.ui.theme.OtterColors
import dev.neoworks.otter.vpn.VpnStatusStore

/** Shield that shows whether the backend sees a VPN. Mirrors VpnGuard.svelte. */
@Composable
fun VpnGuard(modifier: Modifier = Modifier) {
    val status by VpnStatusStore.status.collectAsStateWithLifecycle()
    if (status.connected) {
        Icon(PhosphorIcons.Fill.ShieldCheck, "VPN connected", tint = OtterColors.success, modifier = modifier.size(20.dp))
        return
    }
    Icon(PhosphorIcons.Fill.ShieldWarning, vpnDisconnectedLabel(status.required), tint = OtterColors.error, modifier = modifier.size(20.dp))
}

private fun vpnDisconnectedLabel(required: Boolean): String {
    if (required) {
        return "VPN disconnected — streaming is blocked"
    }
    return "VPN disconnected"
}
