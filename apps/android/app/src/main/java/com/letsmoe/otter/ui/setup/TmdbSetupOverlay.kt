package com.letsmoe.otter.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letsmoe.otter.R
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.ui.components.PlainTextField
import com.letsmoe.otter.ui.components.TMDB_API_KEY_PAGE
import com.letsmoe.otter.ui.components.TmdbAttribution
import com.letsmoe.otter.ui.components.frostedGlass
import com.letsmoe.otter.ui.components.openWebPage
import com.letsmoe.otter.ui.theme.OtterColors

private const val TMDB_SIGNUP_PAGE = "https://www.themoviedb.org/signup"

/** First-run TMDB setup over everything else: built-in key or own key, then the key itself. */
@Composable
fun TmdbSetupOverlay(setup: TmdbSetupViewModel = viewModel()) {
    if (!setup.visible) {
        return
    }
    BackHandler(enabled = setup.step == SetupStep.OWN_KEY && setup.builtInKeyAvailable, onBack = setup::backToChoice)
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Image(painterResource(R.drawable.otter_wordmark), contentDescription = "Otter", modifier = Modifier.size(width = 102.dp, height = 24.dp))
            SetupStepContent(setup)
            TmdbAttribution()
        }
    }
}

@Composable
private fun SetupStepContent(setup: TmdbSetupViewModel) {
    if (setup.step == SetupStep.CHOOSE_KEY) {
        KeyChoice(setup)
        return
    }
    OwnKeyForm(setup)
}

@Composable
private fun KeyChoice(setup: TmdbSetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SetupHeading("Connect to TMDB", "Titles, posters and recommendations come from The Movie Database. Which key should Otter use?")
        ChoiceCard("Use the built-in key", "Start right away. You can switch to your own key in App Settings.", enabled = !setup.saving, onClick = setup::useBuiltInKey)
        ChoiceCard("Use my own key", "Free, takes about two minutes.", enabled = !setup.saving, onClick = setup::chooseOwnKey)
        ErrorText(setup.errorMessage)
    }
}

@Composable
private fun ChoiceCard(title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(description, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun OwnKeyForm(setup: TmdbSetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SetupHeading("Your own TMDB key", "A free account is all it takes.")
        KeySteps()
        KeyField(setup)
        PillButton(saveLabel(setup.saving), Modifier.fillMaxWidth(), enabled = !setup.saving && setup.apiKey.isNotBlank(), filled = true, onClick = setup::saveKey)
        if (setup.builtInKeyAvailable) {
            PillButton("Back", Modifier.fillMaxWidth(), enabled = !setup.saving, filled = false, onClick = setup::backToChoice)
        }
    }
}

@Composable
private fun SetupHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(description, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun KeySteps() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KeyStep(1, "Create a free TMDB account.", "Sign up") { openWebPage(context, TMDB_SIGNUP_PAGE) }
        KeyStep(2, "Under Settings → API, request a key. Pick “Developer” and describe it as personal use.", "Open API settings") {
            openWebPage(context, TMDB_API_KEY_PAGE)
        }
        KeyStep(3, "Copy the “API Key” (not the Read Access Token) and paste it below.", null, null)
    }
}

@Composable
private fun KeyStep(number: Int, text: String, linkLabel: String?, onLink: (() -> Unit)?) {
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
        ErrorText(setup.errorMessage)
    }
}

@Composable
private fun ErrorText(message: String?) {
    if (message == null) {
        return
    }
    Text(message, fontSize = 12.sp, color = OtterColors.error)
}

private fun saveLabel(saving: Boolean): String {
    if (saving) {
        return "Checking…"
    }
    return "Save key"
}
