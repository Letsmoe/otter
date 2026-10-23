<script lang="ts">
	import { fly } from 'svelte/transition';
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

<!-- Toast stack, top-right (opposite the sidebar handle). Entries fly in from the
     right and auto-dismiss via the store. -->
<div
	class="pointer-events-none fixed top-4 right-4 z-50 flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2"
>
	{#each $notifications as entry (entry.id)}
		{@const Icon = iconFor(entry.kind)}
		<div
			animate:flip={{ duration: 200 }}
			transition:fly={{ x: 340, duration: 220 }}
			class="border-base-300 bg-base-200 pointer-events-auto flex items-start gap-2 rounded-lg border p-3 shadow-xl backdrop-blur"
		>
			<span class="mt-0.5 shrink-0 {colorFor(entry.kind)}"><Icon size={18} weight="fill" /></span>
			<span class="text-base-content flex-1 text-sm">{entry.message}</span>
			<button
				type="button"
				class="text-base-content/40 hover:text-base-content shrink-0"
				aria-label="Dismiss"
				onclick={() => notifications.dismiss(entry.id)}
			>
				<XIcon size={14} />
			</button>
		</div>
	{/each}
</div>
