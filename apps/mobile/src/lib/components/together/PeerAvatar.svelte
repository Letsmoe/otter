<script lang="ts">
	// A Watch Together participant's profile icon; dims with a spinner on top
	// while their player is loading.
	import { fade } from 'svelte/transition';
	import { avatarImage } from '@neoworks-dev/otter-app/avatars';
	import type { TogetherPeer } from '@neoworks-dev/otter-app/together';

	interface Props {
		peer: TogetherPeer;
		size?: number;
		buffering?: boolean;
	}

	let { peer, size = 32, buffering = false }: Props = $props();
</script>

<span
	class="relative inline-flex shrink-0 overflow-hidden rounded-[28%]"
	style:width="{size}px"
	style:height="{size}px"
	title={peer.name}
>
	<img
		src={avatarImage(peer.avatar, peer.name)}
		alt={peer.name}
		class="h-full w-full object-cover"
	/>
	{#if buffering}
		<span
			class="absolute inset-0 flex items-center justify-center bg-black/55"
			transition:fade={{ duration: 180 }}
		>
			<span
				class="spinner rounded-full"
				style:width="{size * 0.42}px"
				style:height="{size * 0.42}px"
			></span>
		</span>
	{/if}
</span>

<style>
	.spinner {
		border: 2px solid rgb(255 255 255 / 0.25);
		border-top-color: white;
		animation: spin 800ms linear infinite;
	}

	@keyframes spin {
		to {
			rotate: 360deg;
		}
	}
</style>
