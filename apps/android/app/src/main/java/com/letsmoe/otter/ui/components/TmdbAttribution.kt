package com.letsmoe.otter.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.R

const val TMDB_NOTICE = "This application uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB."

/** The logo and notice TMDB's terms require; kept small so it never outweighs Otter's own branding. */
@Composable
fun TmdbAttribution(modifier: Modifier = Modifier, horizontalAlignment: Alignment.Horizontal = Alignment.Start) {
    var textAlign = TextAlign.Start
    if (horizontalAlignment == Alignment.CenterHorizontally) {
        textAlign = TextAlign.Center
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = horizontalAlignment) {
        Image(painterResource(R.drawable.tmdb_logo), contentDescription = "TMDB", modifier = Modifier.size(width = 77.dp, height = 10.dp))
        Text(TMDB_NOTICE, fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f), textAlign = textAlign)
    }
}
