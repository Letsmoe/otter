<script lang="ts">
	// Header shortcut to Watch Together, placed left of search; a green dot
	// shows while a room is open.
	import UsersThreeIcon from 'phosphor-svelte/lib/UsersThreeIcon';
	import { together } from '$lib/stores/together.svelte';

	interface Props {
		/** Round glass button, for headers floating over images. */
		glass?: boolean;
		size?: number;
	}

	let { glass = false, size = 22 }: Props = $props();
</script>

<button
	class="pointer-events-auto relative flex h-10 w-10 items-center justify-center rounded-full"
	class:glass-bar={glass}
	aria-label="Watch Together"
	onclick={() => together.openSheet()}
>
	<UsersThreeIcon {size} />
	{#if together.open}
		<span class="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-green-400 ring-2 ring-black"
		></span>
	{/if}
</button>
