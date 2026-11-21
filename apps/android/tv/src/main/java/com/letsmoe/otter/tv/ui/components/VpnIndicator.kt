package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.vpn.VpnStatus
import com.letsmoe.otter.vpn.VpnStatusStore

private data class VpnLook(val label: String, val color: Color)

/** A pill in the top right: whether Otter's traffic goes through a VPN. Hidden until the backend answers. */
@Composable
fun VpnIndicator(modifier: Modifier = Modifier) {
    val status by VpnStatusStore.status.collectAsStateWithLifecycle()
    val look = vpnLook(status) ?: return
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(look.color, CircleShape))
        Text(look.label, fontSize = TvType.caption, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
    }
}

private fun vpnLook(status: VpnStatus): VpnLook? {
    if (!status.known) {
        return null
    }
    if (status.connected) {
        return VpnLook("VPN on", TvColors.success)
    }
    if (status.bypassed) {
        return VpnLook("VPN skips Otter", TvColors.warning)
    }
    if (status.required) {
        return VpnLook("No VPN", TvColors.error)
    }
    return VpnLook("No VPN", TvColors.neutralContent)
}
