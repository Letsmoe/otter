package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

/**
 * The app's top bar: the wordmark on the left when [showWordmark], the [tabs] centred, the VPN
 * state and [trailing] on the right.
 */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    showWordmark: Boolean = true,
    tabs: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TvSpacing.screenHorizontal, vertical = 16.dp),
    ) {
        if (showWordmark) {
            Image(
                painter = painterResource(R.drawable.otter_wordmark),
                contentDescription = "Otter",
                modifier = Modifier.align(Alignment.CenterStart).height(16.dp),
            )
        }
        Box(Modifier.align(Alignment.Center)) {
            tabs()
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VpnIndicator()
            trailing()
        }
    }
}
