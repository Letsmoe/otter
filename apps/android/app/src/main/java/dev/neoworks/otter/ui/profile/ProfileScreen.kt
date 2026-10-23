package dev.neoworks.otter.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Bell
import com.adamglin.phosphoricons.fill.DownloadSimple as DownloadSimpleFill
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.DownloadSimple
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import dev.neoworks.otter.downloads.DownloadGroup
import dev.neoworks.otter.downloads.DownloadsRepository
import dev.neoworks.otter.downloads.groupDownloads
import dev.neoworks.otter.graphql.fragment.ProfileFields
import dev.neoworks.otter.notifications.NotificationItem
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.profiles.avatarImage
import dev.neoworks.otter.ui.components.PosterRail
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.navigation.bottomBarClearance
import dev.neoworks.otter.ui.together.TogetherButton

private const val DOWNLOAD_PREVIEW_COUNT = 4

/** Where the profile page leads. */
class ProfileNavigation(
    val onOpenItem: (RailItem) -> Unit,
    val onOpenNotification: (NotificationItem) -> Unit,
    val onSearch: () -> Unit,
    val onNotifications: () -> Unit,
    val onDownloads: () -> Unit,
    val onHistory: () -> Unit,
    val onPlugins: () -> Unit,
    val onSettings: () -> Unit,
)

/**
 * Netflix's "My Netflix": avatar and name (tap to switch), Notifications and Downloads cards, then
 * My List, Watch History and liked titles. Mirrors routes/profile/+page.svelte.
 */
@Composable
fun ProfileScreen(navigation: ProfileNavigation, viewModel: ProfileViewModel = viewModel()) {
    val activeProfile by ProfileSession.activeProfile.collectAsStateWithLifecycle()
    val page by viewModel.data.collectAsStateWithLifecycle()
    val downloadItems by DownloadsRepository.items.collectAsStateWithLifecycle()
    val profile = activeProfile ?: return
    LaunchedEffect(profile.id) { viewModel.load(profile.id) }
    val menuActions = ProfileMenuActions(
        onNotifications = navigation.onNotifications,
        onHistory = navigation.onHistory,
        onPlugins = navigation.onPlugins,
        onSettings = navigation.onSettings,
        onSwitchProfile = ProfileSession::logout,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomBarClearance()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ProfileHeader(navigation.onSearch, menuActions) }
        item { ProfileIdentity(profile) }
        item { NotificationsCard(page, navigation) }
        item { DownloadsCard(groupDownloads(downloadItems).take(DOWNLOAD_PREVIEW_COUNT), navigation) }
        item { RailsSection(page, navigation.onOpenItem) }
    }
}

@Composable
private fun ProfileHeader(onSearch: () -> Unit, menuActions: ProfileMenuActions) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("My Otter", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TogetherButton(iconSize = 24.dp)
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onSearch), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Regular.MagnifyingGlass, contentDescription = "Search", modifier = Modifier.size(24.dp))
        }
        ProfileMenu(menuActions)
    }
}

@Composable
private fun ProfileIdentity(profile: ProfileFields) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = ProfileSession::logout).padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(avatarImage(profile.avatarUrl, profile.name), contentDescription = null, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(profile.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Icon(PhosphorIcons.Regular.CaretDown, contentDescription = "Switch profile", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun NotificationsCard(page: ProfilePageData, navigation: ProfileNavigation) {
    SectionCard("Notifications", PhosphorIcons.Fill.Bell, navigation.onNotifications, Modifier.padding(horizontal = 12.dp)) {
        if (page.notifications.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (notification in page.notifications) {
                    NotificationRow(notification, onOpen = { navigation.onOpenNotification(notification) })
                }
            }
        } else if (page.notificationsLoaded) {
            Text(
                "News about titles on My List — new episodes, releases and found sources — shows up here.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun DownloadsCard(groups: List<DownloadGroup>, navigation: ProfileNavigation) {
    SectionCard("Downloads", PhosphorIcons.Fill.DownloadSimpleFill, navigation.onDownloads, Modifier.padding(horizontal = 12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups, key = { group -> group.titleId }) { group -> DownloadPoster(group, navigation.onDownloads) }
            item { FindDownloadsTile(compact = groups.isNotEmpty(), onClick = navigation.onSearch) }
        }
    }
}

@Composable
private fun DownloadPoster(group: DownloadGroup, onClick: () -> Unit) {
    Column(Modifier.width(112.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Color.White.copy(alpha = 0.05f))) {
            if (group.posterUrl != null) {
                AsyncImage(group.posterUrl, contentDescription = group.titleName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(downloadCaption(group), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
}

private fun downloadCaption(group: DownloadGroup): String {
    if (!group.isSeries) {
        return "Movie"
    }
    if (group.items.size == 1) {
        return "1 Episode"
    }
    return "${group.items.size} Episodes"
}

// Alone, the tile gets a fixed height; next to posters it matches them.
@Composable
private fun FindDownloadsTile(compact: Boolean, onClick: () -> Unit) {
    var tile = Modifier.widthIn(min = 160.dp).heightIn(min = 160.dp)
    if (compact) {
        tile = Modifier.widthIn(min = 160.dp).height(190.dp)
    }
    tile = tile.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onClick)
    Column(
        tile.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(48.dp).border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Regular.DownloadSimple, contentDescription = null, modifier = Modifier.size(22.dp))
        }
        Text("See what you can download", fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RailsSection(page: ProfilePageData, onOpenItem: (RailItem) -> Unit) {
    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PosterRail("My List", page.watchlist, onOpenItem)
        PosterRail("Watch History", page.history, onOpenItem)
        PosterRail("TV Shows & Movies You've Liked", page.liked, onOpenItem)
    }
}
