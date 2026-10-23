<script lang="ts">
	// Every notification of the profile; opening the page marks them read.
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import {
		fetchNotifications,
		markNotificationsRead,
		type NotificationItem
	} from '@neoworks-dev/otter-app/notifications';
	import ScreenHeader from '$lib/components/ScreenHeader.svelte';
	import NotificationRow from '$lib/components/profile-page/NotificationRow.svelte';

	let notifications = $state<NotificationItem[]>([]);
	let loaded = $state(false);

	$effect(() => {
		const profile = $activeProfile;
		if (!profile) return;
		fetchNotifications(profile.id, 100).then((items) => {
			notifications = items;
			loaded = true;
			if (items.some((item) => !item.read)) markNotificationsRead(profile.id).catch(() => {});
		});
	});
</script>

<div class="-mx-3">
	<ScreenHeader title="Notifications" onback={() => history.back()} />
</div>

{#if notifications.length > 0}
	<div class="glass-bar mb-8 flex flex-col gap-2 rounded-3xl p-3">
		{#each notifications as notification (notification.id)}
			<NotificationRow {notification} />
		{/each}
	</div>
{:else if loaded}
	<p class="pt-16 text-center text-sm text-white/50">No notifications yet.</p>
{:else}
	<span class="loading loading-spinner mx-auto mt-16 block"></span>
{/if}
