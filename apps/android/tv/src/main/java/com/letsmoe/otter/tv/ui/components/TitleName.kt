package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.letsmoe.otter.tv.theme.TvType

private val LOGO_MAX_WIDTH = 300.dp
private val LOGO_MAX_HEIGHT = 80.dp

/** A title's logo artwork, like streaming apps show over their hero; the name in large text when it has none. */
@Composable
fun TitleName(titleId: String, title: String, modifier: Modifier = Modifier) {
    val logoUrl by rememberTitleLogo(titleId)
    val logo = logoUrl
    if (logo != null) {
        AsyncImage(
            model = logo,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            modifier = modifier.widthIn(max = LOGO_MAX_WIDTH).heightIn(max = LOGO_MAX_HEIGHT),
        )
        return
    }
    Text(
        title,
        fontSize = TvType.heroTitle,
        fontWeight = FontWeight.Bold,
        lineHeight = 34.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
