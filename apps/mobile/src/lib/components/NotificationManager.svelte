<script lang="ts">
	import { fly } from 'svelte/transition';
	import { backOut } from 'svelte/easing';
	import { flip } from 'svelte/animate';
	import XIcon from 'phosphor-svelte/lib/XIcon';
	import CheckCircleIcon from 'phosphor-svelte/lib/CheckCircleIcon';
	import WarningCircleIcon from 'phosphor-svelte/lib/WarningCircleIcon';
	import InfoIcon from 'phosphor-svelte/lib/InfoIcon';
	import { notifications } from '$lib/stores/notifications';

	function iconFor(kind: string) {
		if (kind === 'success') return CheckCircleIcon;
		if (kind === 'error') return WarningCircleIcon;
		return InfoIcon;
	}

	function colorFor(kind: string): string {
		if (kind === 'success') return 'text-success';
		if (kind === 'error') return 'text-error';
		return 'text-info';
	}
</script>

<!-- Glass toasts dropping in under the status bar, newest last; they dismiss
     themselves via the store. -->
<div
	class="pointer-events-none fixed inset-x-0 top-[calc(var(--safe-top)+10px)] z-[75] flex flex-col items-center gap-2 px-4"
>
	{#each $notifications as entry (entry.id)}
		{@const Icon = iconFor(entry.kind)}
		<div
			animate:flip={{ duration: 220 }}
			in:fly={{ y: -48, duration: 380, easing: backOut }}
			out:fly={{ y: -32, duration: 200 }}
			class="glass-bar pointer-events-auto flex max-w-sm items-center gap-2.5 rounded-full py-2.5 pr-3 pl-4 shadow-xl"
		>
			<span class="shrink-0 {colorFor(entry.kind)}"><Icon size={18} weight="fill" /></span>
			<span class="line-clamp-2 flex-1 text-sm font-medium">{entry.message}</span>
			<button
				type="button"
				class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-white/50"
				aria-label="Dismiss"
				onclick={() => notifications.dismiss(entry.id)}
			>
				<XIcon size={13} weight="bold" />
			</button>
		</div>
	{/each}
</div>
