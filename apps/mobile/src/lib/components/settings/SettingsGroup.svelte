<script lang="ts">
	// A titled glass card of settings rows, like iOS / Netflix app settings.
	import type { Snippet } from 'svelte';

	interface Props {
		title: string;
		footer?: string;
		children: Snippet;
	}

	let { title, footer, children }: Props = $props();
</script>

<section class="flex flex-col gap-2">
	<h2 class="px-1 text-xs font-semibold tracking-wider text-white/50 uppercase">{title}</h2>
	<div class="glass-bar flex flex-col divide-y divide-white/10 rounded-3xl px-4">
		{@render children()}
	</div>
	{#if footer}
		<p class="px-1 text-xs text-white/40">{footer}</p>
	{/if}
</section>
