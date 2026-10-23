<script lang="ts">
	// One notification: still or backdrop, a red dot while unread, heading, body and age.
	import { notificationAge, type NotificationItem } from '@neoworks-dev/otter-app/notifications';

	interface Props {
		notification: NotificationItem;
	}

	let { notification }: Props = $props();
</script>

<a href={notification.href} class="relative flex items-center gap-3 py-1.5 pl-3">
	{#if !notification.read}
		<span class="bg-error absolute top-1/2 left-0 h-1.5 w-1.5 -translate-y-1/2 rounded-full"></span>
	{/if}
	<span class="aspect-video w-32 shrink-0 overflow-hidden rounded-xl bg-white/5">
		{#if notification.imageUrl}
			<img src={notification.imageUrl} alt="" loading="lazy" class="h-full w-full object-cover" />
		{:else}
			<span class="flex h-full items-center justify-center p-2 text-center text-xs font-semibold">
				{notification.titleName}
			</span>
		{/if}
	</span>
	<span class="flex min-w-0 flex-1 flex-col">
		<span class="line-clamp-1 text-sm font-semibold">{notification.heading}</span>
		<span class="line-clamp-1 text-sm text-white/80">
			{notification.body || notification.titleName}
		</span>
		<span class="text-xs text-white/45">{notificationAge(notification.createdAt)}</span>
	</span>
</a>
