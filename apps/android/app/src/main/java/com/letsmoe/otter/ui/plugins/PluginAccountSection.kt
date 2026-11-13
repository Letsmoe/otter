package com.letsmoe.otter.ui.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.graphql.fragment.PluginFields
import com.letsmoe.otter.ui.components.PlainTextField
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.theme.OtterColors

/** The login of a plugin whose site needs an account: a form, or who is logged in. */
@Composable
fun PluginAccountSection(plugin: PluginFields, account: PluginFields.Account, viewModel: PluginsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Account", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (account.username != null && account.loggedIn) {
            LoggedIn(plugin, account.username, viewModel)
        } else {
            LoginForm(plugin, account, viewModel)
        }
    }
}

@Composable
private fun LoggedIn(plugin: PluginFields, username: String, viewModel: PluginsViewModel) {
    Row(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Logged in as $username", fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f))
        Text("Log out", fontSize = 14.sp, color = OtterColors.error, modifier = Modifier.pressable { viewModel.logout(plugin) })
    }
}

@Composable
private fun LoginForm(plugin: PluginFields, account: PluginFields.Account, viewModel: PluginsViewModel) {
    var username by remember(plugin.name) { mutableStateOf(account.username.orEmpty()) }
    var password by remember(plugin.name) { mutableStateOf("") }
    if (!account.error.isNullOrEmpty()) {
        Text("Login stopped working: ${account.error}", fontSize = 12.sp, color = OtterColors.error)
    }
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)) {
        CredentialField("Username", username, isPassword = false) { typed -> username = typed }
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        CredentialField("Password", password, isPassword = true) { typed -> password = typed }
    }
    Text(
        "Otter keeps your password encrypted so it can log in again when the site signs you out.",
        fontSize = 12.sp,
        color = Color.White.copy(alpha = 0.4f),
    )
    SignupLink(account.signupUrl)
    LoginButton(viewModel.loggingIn) { viewModel.login(plugin, username.trim(), password) }
}

@Composable
private fun CredentialField(label: String, value: String, isPassword: Boolean, onValueChange: (String) -> Unit) {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 14.sp)
        PlainTextField(value = value, onValueChange = onValueChange, placeholder = "", isPassword = isPassword, textColor = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun SignupLink(signupUrl: String?) {
    if (signupUrl.isNullOrEmpty()) {
        return
    }
    val uriHandler = LocalUriHandler.current
    Text(
        "No account yet? Sign up",
        fontSize = 12.sp,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.pressable { uriHandler.openUri(signupUrl) },
    )
}

@Composable
private fun LoginButton(loggingIn: Boolean, onClick: () -> Unit) {
    var buttonAlpha = 1f
    if (loggingIn) {
        buttonAlpha = 0.5f
    }
    Row(
        Modifier.pressable(enabled = !loggingIn, onClick = onClick).fillMaxWidth().height(48.dp).alpha(buttonAlpha).background(Color.White, CircleShape),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loggingIn) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Black)
        }
        Text("Log in", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
