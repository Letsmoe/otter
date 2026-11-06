package dev.neoworks.otter.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neoworks.otter.ui.components.PillButton
import dev.neoworks.otter.ui.components.PlainTextField
import dev.neoworks.otter.ui.components.TMDB_API_KEY_PAGE
import dev.neoworks.otter.ui.components.frostedGlass
import dev.neoworks.otter.ui.components.openWebPage
import dev.neoworks.otter.ui.theme.OtterColors

private const val TMDB_SIGNUP_PAGE = "https://www.themoviedb.org/signup"

/** Asks for a TMDB API key on first start, over everything else. */
@Composable
fun TmdbSetupOverlay(setup: TmdbSetupViewModel = viewModel()) {
    if (!setup.visible) {
        return
    }
    // The scrim swallows taps so the app underneath stays untouched until setup is done.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .frostedGlass(RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SetupIntroduction()
            SetupSteps()
            KeyField(setup)
            SetupButtons(setup)
        }
    }
}

@Composable
private fun SetupIntroduction() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Connect to TMDB", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Otter gets titles, posters and recommendations from The Movie Database. A key of your own is free and takes about two minutes.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun SetupSteps() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SetupStep(1, "Create a free TMDB account.", "Sign up") { openWebPage(context, TMDB_SIGNUP_PAGE) }
        SetupStep(2, "Under Settings → API, request a key. Pick “Developer” and describe it as personal use.", "Open API settings") {
            openWebPage(context, TMDB_API_KEY_PAGE)
        }
        SetupStep(3, "Copy the “API Key” (not the Read Access Token) and paste it below.", null, null)
    }
}

@Composable
private fun SetupStep(number: Int, text: String, linkLabel: String?, onLink: (() -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp).background(Color.White.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            Text(number.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, fontSize = 14.sp)
            if (linkLabel != null && onLink != null) {
                StepLink(linkLabel, onLink)
            }
        }
    }
}

@Composable
private fun StepLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        color = OtterColors.accent,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun KeyField(setup: TmdbSetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PlainTextField(
            value = setup.apiKey,
            onValueChange = { typed -> setup.apiKey = typed },
            placeholder = "Paste your API key",
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
        val errorMessage = setup.errorMessage
        if (errorMessage != null) {
            Text(errorMessage, fontSize = 12.sp, color = OtterColors.error)
        }
    }
}

@Composable
private fun SetupButtons(setup: TmdbSetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(saveLabel(setup.saving), Modifier.fillMaxWidth(), enabled = !setup.saving && setup.apiKey.isNotBlank(), filled = true, onClick = setup::saveKey)
        if (!setup.builtInKeyAvailable) {
            return
        }
        PillButton("Use the built-in key for now", Modifier.fillMaxWidth(), enabled = !setup.saving, filled = false, onClick = setup::useBuiltInKey)
        Text(
            "You can add your own key later in App Settings.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

private fun saveLabel(saving: Boolean): String {
    if (saving) {
        return "Checking…"
    }
    return "Save key"
}
