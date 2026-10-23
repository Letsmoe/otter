<script lang="ts">
	// Netflix's "My Netflix": avatar and name (tap to switch), Notifications and
	// Downloads cards, then My List, Watch History and liked titles.
	import { activeProfile, avatarFor } from '@neoworks-dev/otter-app/profile';
	import {
		fetchHistory,
		fetchLikedTitles,
		fetchWatchlist,
		type RailItem
	} from '@neoworks-dev/otter-app/discovery';
	import { fetchNotifications, type NotificationItem } from '@neoworks-dev/otter-app/notifications';
	import MagnifyingGlassIcon from 'phosphor-svelte/lib/MagnifyingGlassIcon';
	import TogetherButton from '$lib/components/together/TogetherButton.svelte';
	import CaretDownIcon from 'phosphor-svelte/lib/CaretDownIcon';
	import BellIcon from 'phosphor-svelte/lib/BellIcon';
	import DownloadSimpleIcon from 'phosphor-svelte/lib/DownloadSimpleIcon';
	import PosterRail from '$lib/components/home/PosterRail.svelte';
	import SectionCard from '$lib/components/profile-page/SectionCard.svelte';
	import NotificationRow from '$lib/components/profile-page/NotificationRow.svelte';
	import ProfileMenu from '$lib/components/profile-page/ProfileMenu.svelte';
	import { groupDownloads } from '@neoworks-dev/otter-app/downloads';
	import { downloads } from '$lib/stores/downloads.svelte';

	const NOTIFICATION_PREVIEW_COUNT = 3;
	const DOWNLOAD_PREVIEW_COUNT = 4;

	let notifications = $state<NotificationItem[]>([]);
	let notificationsLoaded = $state(false);
	let watchlist = $state<RailItem[]>([]);
	let history = $state<RailItem[]>([]);
	let liked = $state<RailItem[]>([]);

	$effect(() => {
		const profile = $activeProfile;
		if (!profile) return;
		fetchNotifications(profile.id, NOTIFICATION_PREVIEW_COUNT).then((loaded) => {
			notifications = loaded;
			notificationsLoaded = true;
		});
		fetchWatchlist(profile.id).then((loaded) => (watchlist = loaded));
		fetchHistory(profile.id, 20).then((loaded) => (history = loaded));
		fetchLikedTitles(profile.id).then((loaded) => (liked = loaded));
	});

	let downloadGroups = $derived(groupDownloads(downloads.items).slice(0, DOWNLOAD_PREVIEW_COUNT));

	function downloadCaption(count: number, isSeries: boolean): string {
		if (!isSeries) return 'Movie';
		if (count === 1) return '1 Episode';
		return `${count} Episodes`;
	}

	// Alone, the tile gets a fixed height; next to posters it matches them.
	function emptyTileHeight(): string {
		if (downloadGroups.length > 0) return '';
		return 'h-40';
	}

	function switchProfile() {
		activeProfile.logout();
	}
</script>

<header class="flex h-14 items-center justify-between">
	<h1 class="text-2xl font-bold">My Otter</h1>
	<div class="flex items-center">
		<TogetherButton size={24} />
		<a
			href="/search"
			class="flex h-10 w-10 items-center justify-center rounded-full"
			aria-label="Search"
		>
			<MagnifyingGlassIcon size={24} />
		</a>
		<ProfileMenu onswitchprofile={switchProfile} />
	</div>
</header>

{#if $activeProfile}
	<button class="mx-auto flex flex-col items-center gap-2 pt-2 pb-6" onclick={switchProfile}>
		<img src={avatarFor($activeProfile)} alt="" class="h-24 w-24 rounded-2xl" />
		<span class="flex items-center gap-1.5 text-xl font-bold">
			{$activeProfile.name}
			<CaretDownIcon size={16} weight="bold" />
		</span>
	</button>
{/if}

<div class="flex flex-col gap-4 pb-6">
	<SectionCard title="Notifications" href="/notifications" icon={BellIcon}>
		{#if notifications.length > 0}
			<div class="flex flex-col gap-1">
				{#each notifications as notification (notification.id)}
					<NotificationRow {notification} />
				{/each}
			</div>
		{:else if notificationsLoaded}
			<p class="text-sm text-white/50">
				News about titles on My List — new episodes, releases and found sources — shows up here.
			</p>
		{/if}
	</SectionCard>

	<SectionCard title="Downloads" href="/downloads" icon={DownloadSimpleIcon}>
		<div class="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4">
			{#each downloadGroups as group (group.titleId)}
				<a
					href="/downloads"
					class="flex w-28 shrink-0 flex-col overflow-hidden rounded-2xl bg-black/40"
				>
					<span class="aspect-[2/3] w-full bg-white/5">
						{#if group.posterUrl}
							<img src={group.posterUrl} alt={group.titleName} class="h-full w-full object-cover" />
						{/if}
					</span>
					<span class="py-2 text-center text-xs">
						{downloadCaption(group.items.length, group.titleId.startsWith('series:'))}
					</span>
				</a>
			{/each}
			<a
				href="/search"
				class="flex min-w-40 flex-1 flex-col items-center justify-center gap-3 rounded-2xl bg-black/40 p-4 text-center text-sm {emptyTileHeight()}"
			>
				<span
					class="flex h-12 w-12 items-center justify-center rounded-full border border-white/60"
				>
					<DownloadSimpleIcon size={22} />
				</span>
				See what you can download
			</a>
		</div>
	</SectionCard>
</div>

<div class="flex flex-col gap-6 pb-4">
	<PosterRail title="My List" items={watchlist} />
	<PosterRail title="Watch History" items={history} />
	<PosterRail title="TV Shows & Movies You've Liked" items={liked} />
</div>
