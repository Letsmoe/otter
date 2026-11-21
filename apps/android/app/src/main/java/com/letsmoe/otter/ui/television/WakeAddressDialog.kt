package com.letsmoe.otter.ui.television

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.cast.DlnaRenderer
import com.letsmoe.otter.cast.WakeableTelevisions
import com.letsmoe.otter.cast.formatMacAddress
import com.letsmoe.otter.cast.parseMacAddress
import com.letsmoe.otter.ui.components.PlainTextField

/**
 * Asks for the MAC address of [renderer], which Otter needs to switch the TV on from standby.
 * [onDone] runs after the address was saved or removed.
 */
@Composable
fun WakeAddressDialog(renderer: DlnaRenderer, currentAddress: String?, onDismiss: () -> Unit, onDone: () -> Unit) {
    var typed by remember { mutableStateOf(currentAddress.orEmpty()) }
    val address = parseMacAddress(typed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch on ${renderer.name}") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Otter can switch this TV on from standby with its MAC address, found in the TV's network settings " +
                        "(the one of the Wi-Fi, or of the cable if it uses one). The TV must allow switching on over the network.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
                PlainTextField(typed, { changed -> typed = changed }, "AA:BB:CC:DD:EE:FF", Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = address != null,
                onClick = {
                    if (address != null) {
                        WakeableTelevisions.save(renderer, formatMacAddress(address))
                        onDone()
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            if (currentAddress != null) {
                TextButton(onClick = {
                    WakeableTelevisions.forget(renderer.id)
                    onDone()
                }) { Text("Remove") }
            }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
