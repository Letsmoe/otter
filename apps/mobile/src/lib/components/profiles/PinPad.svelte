<script lang="ts">
	// Full-screen 4-digit PIN entry with an on-screen keypad. Submits on the
	// fourth digit; the parent answers with an error message or null.
	import BackspaceIcon from 'phosphor-svelte/lib/BackspaceIcon';
	import ScreenHeader from '../ScreenHeader.svelte';

	const PIN_LENGTH = 4;
	const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', 'delete'];

	interface Props {
		title: string;
		message: string;
		avatarUrl?: string;
		onback: () => void;
		oncomplete: (pin: string) => Promise<string | null>;
	}

	let { title, message, avatarUrl, onback, oncomplete }: Props = $props();

	let digits = $state('');
	let error = $state('');
	let busy = $state(false);

	async function submit() {
		busy = true;
		const result = await oncomplete(digits);
		busy = false;
		digits = '';
		if (result) error = result;
	}

	function press(key: string) {
		if (busy) return;
		if (key === 'delete') {
			digits = digits.slice(0, -1);
			return;
		}
		if (digits.length >= PIN_LENGTH) return;
		error = '';
		digits += key;
		if (digits.length === PIN_LENGTH) submit();
	}

	function onKeydown(event: KeyboardEvent) {
		if (/^\d$/.test(event.key)) press(event.key);
		if (event.key === 'Backspace') press('delete');
	}
</script>

<svelte:window onkeydown={onKeydown} />

<div class="flex h-full flex-col">
	<ScreenHeader {title} {onback} />

	<div class="flex flex-1 flex-col items-center gap-6 px-6 pt-8">
		{#if avatarUrl}
			<img src={avatarUrl} alt="" class="h-20 w-20 rounded-2xl" />
		{/if}
		<p class="text-center text-lg">{message}</p>

		<div class="flex gap-3" class:animate-shake={error}>
			{#each Array(PIN_LENGTH) as _, index (index)}
				<div
					class="flex h-14 w-12 items-center justify-center rounded-2xl border-2 bg-white/5 text-2xl {index <
					digits.length
						? 'border-white'
						: 'border-white/25'}"
				>
					{#if index < digits.length}•{/if}
				</div>
			{/each}
		</div>

		<p class="text-error h-5 text-sm">{error}</p>
	</div>

	<div class="grid grid-cols-3 gap-2 px-6 pb-[calc(var(--safe-bottom)+24px)]">
		{#each KEYS as key (key)}
			{#if key === ''}
				<span></span>
			{:else if key === 'delete'}
				<button
					class="flex h-16 items-center justify-center rounded-full active:bg-white/10"
					aria-label="Delete"
					onclick={() => press(key)}
				>
					<BackspaceIcon size={26} />
				</button>
			{:else}
				<button
					class="h-16 rounded-full bg-white/6 text-2xl font-medium active:bg-white/15"
					onclick={() => press(key)}
				>
					{key}
				</button>
			{/if}
		{/each}
	</div>
</div>

<style>
	@keyframes shake {
		0%,
		100% {
			transform: translateX(0);
		}
		25% {
			transform: translateX(-8px);
		}
		75% {
			transform: translateX(8px);
		}
	}
	.animate-shake {
		animation: shake 0.25s ease-in-out 2;
	}
</style>
