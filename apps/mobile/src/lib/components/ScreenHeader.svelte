<script lang="ts">
	// Top bar of a full-screen page: back arrow, title, optional text action.
	import ArrowLeftIcon from 'phosphor-svelte/lib/ArrowLeftIcon';

	interface Props {
		title: string;
		onback: () => void;
		actionLabel?: string;
		actionDisabled?: boolean;
		onaction?: () => void;
	}

	let { title, onback, actionLabel, actionDisabled = false, onaction }: Props = $props();
</script>

<header class="flex h-14 shrink-0 items-center gap-4 px-2">
	<button class="flex h-11 w-11 items-center justify-center" aria-label="Back" onclick={onback}>
		<ArrowLeftIcon size={24} />
	</button>
	<h1 class="flex-1 text-xl font-semibold">{title}</h1>
	{#if actionLabel && onaction}
		<button
			class="text-success px-3 text-base font-semibold disabled:opacity-40"
			disabled={actionDisabled}
			onclick={onaction}
		>
			{actionLabel}
		</button>
	{/if}
</header>
