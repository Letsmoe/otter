package dev.neoworks.otter.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

// Share of the scroll distance the portrait lags behind the page.
private const val PARALLAX_FACTOR = 0.5f

// The portrait takes most of the screen but leaves the start of the page in view.
private const val HERO_SCREEN_FRACTION = 0.68f

private val heroFade = Brush.verticalGradient(
    0f to Color.Transparent,
    0.55f to Color.Black.copy(alpha = 0.55f),
    1f to Color.Black,
)

/**
 * The person's portrait across the full width from the top of the screen, drifting slower than
 * the page, with their name and key facts on its fading lower edge.
 */
@Composable
fun PersonHero(person: PersonDetail, scroll: () -> Float) {
    val heroHeight = LocalConfiguration.current.screenHeightDp.dp * HERO_SCREEN_FRACTION
    Box(Modifier.fillMaxWidth().height(heroHeight).clipToBounds()) {
        if (person.photoUrl != null) {
            AsyncImage(
                person.photoUrl,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = scroll() * PARALLAX_FACTOR }
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.6f).background(heroFade))
        HeroText(person, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun HeroText(person: PersonDetail, modifier: Modifier) {
    Column(modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(person.name, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
        val facts = listOfNotNull(person.knownForDepartment, person.lifespan, person.placeOfBirth)
        if (facts.isNotEmpty()) {
            Text(facts.joinToString("  ·  "), fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f))
        }
    }
}
