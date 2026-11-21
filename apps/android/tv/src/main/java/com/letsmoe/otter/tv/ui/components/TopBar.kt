package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.letsmoe.otter.tv.R
import com.letsmoe.otter.tv.theme.TvSpacing

/** The wordmark with the app's [tabs] right after it on the left, the VPN state and [trailing] on the right. */
@Composable
fun TopBar(modifier: Modifier = Modifier, tabs: @Composable () -> Unit = {}, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TvSpacing.screenHorizontal, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.otter_wordmark),
            contentDescription = "Otter",
            modifier = Modifier.height(16.dp),
        )
        Row(Modifier.weight(1f).padding(start = 8.dp)) {
            tabs()
        }
        VpnIndicator()
        trailing()
    }
}
