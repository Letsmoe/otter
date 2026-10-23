<script lang="ts">
	// Over the video: reactions float up on the right, recent chat messages
	// fade in at the bottom left. Visible even while the controls are hidden.
	import { untrack } from 'svelte';
	import { fade, fly } from 'svelte/transition';
	import { together, type ChatLine } from '$lib/stores/together.svelte';
	import PeerAvatar from './PeerAvatar.svelte';

	const BUBBLE_MS = 6000;
	const BUBBLE_COUNT = 3;

	let recent = $state<ChatLine[]>([]);
	// Messages from before the player opened stay in the chat panel.
	let lastSeenId = untrack(lastChatId);

	function lastChatId(): number {
		if (together.chat.length === 0) return 0;
		return together.chat[together.chat.length - 1].id;
	}

	// Messages arriving while the player is open show briefly.
	$effect(() => {
		const fresh = together.chat.filter((line) => line.id > lastSeenId && !line.mine);
		lastSeenId = lastChatId();
		if (fresh.length === 0) return;
		recent = [...untrack(() => recent), ...fresh].slice(-BUBBLE_COUNT);
		for (const line of fresh) setTimeout(() => dismiss(line.id), BUBBLE_MS);
	});

	function dismiss(lineId: number) {
		recent = recent.filter((line) => line.id !== lineId);
	}
</script>

<div class="pointer-events-none absolute inset-0 z-[5] overflow-hidden">
	{#each together.reactions as reaction (reaction.id)}
		<span class="reaction absolute bottom-24 text-4xl" style:right="{8 + reaction.offset / 4}%">
			{reaction.emoji}
		</span>
	{/each}

	<div class="absolute bottom-28 left-5 flex max-w-[40%] flex-col items-start gap-2">
		{#each recent as line (line.id)}
			<div
				class="glass-bar flex items-center gap-2 rounded-2xl py-1.5 pr-3 pl-1.5"
				in:fly={{ y: 12, duration: 220 }}
				out:fade={{ duration: 300 }}
			>
				<PeerAvatar peer={line.from} size={24} />
				<span class="text-sm"><b class="font-semibold">{line.from.name}</b> {line.text}</span>
			</div>
		{/each}
	</div>
</div>

<style>
	.reaction {
		animation: float-up 2600ms ease-out forwards;
		filter: drop-shadow(0 2px 6px rgb(0 0 0 / 0.4));
	}

	@keyframes float-up {
		0% {
			opacity: 0;
			translate: 0 0;
			scale: 0.6;
		}
		12% {
			opacity: 1;
			scale: 1.1;
		}
		100% {
			opacity: 0;
			translate: 0 -55vh;
			scale: 1;
		}
	}
</style>
