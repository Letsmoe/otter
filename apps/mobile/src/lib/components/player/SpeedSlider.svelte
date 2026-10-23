<script lang="ts">
	// Stepped speed slider: tap or drag to one of the stops.
	interface Props {
		speeds: number[];
		value: number;
		onchange: (speed: number) => void;
	}

	let { speeds, value, onchange }: Props = $props();

	let track = $state<HTMLDivElement>();
	let dragging = $state(false);

	let index = $derived(Math.max(0, speeds.indexOf(value)));
	let percent = $derived((index / (speeds.length - 1)) * 100);

	function stopAt(clientX: number): number {
		if (!track) return value;
		const rect = track.getBoundingClientRect();
		const fraction = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
		return speeds[Math.round(fraction * (speeds.length - 1))];
	}

	function onPointerDown(event: PointerEvent) {
		track?.setPointerCapture(event.pointerId);
		dragging = true;
		onchange(stopAt(event.clientX));
	}

	function onPointerMove(event: PointerEvent) {
		if (!dragging) return;
		const next = stopAt(event.clientX);
		if (next !== value) onchange(next);
	}

	function label(speed: number): string {
		return `${speed}x`;
	}
</script>

<div class="flex flex-col gap-4 px-2">
	<div
		bind:this={track}
		class="relative flex h-10 touch-none items-center"
		role="slider"
		tabindex="0"
		aria-label="Playback speed"
		aria-valuenow={value}
		onpointerdown={onPointerDown}
		onpointermove={onPointerMove}
		onpointerup={() => (dragging = false)}
		onpointercancel={() => (dragging = false)}
	>
		<div class="relative h-1 w-full rounded-full bg-white/15">
			<div class="absolute inset-y-0 left-0 rounded-full bg-white/90" style:width="{percent}%"></div>
		</div>
		<div
			class="thumb absolute h-7 w-10 -translate-x-1/2 rounded-full bg-white shadow-lg"
			style:left="{percent}%"
		></div>
	</div>
	<div class="flex justify-between text-xs">
		{#each speeds as speed (speed)}
			<button
				class="flex w-12 flex-col items-center {speed === value ? 'font-bold text-white' : 'text-white/50'}"
				onclick={() => onchange(speed)}
			>
				{label(speed)}
				{#if speed === 1}<span class="text-[10px] font-normal text-white/80">Normal</span>{/if}
			</button>
		{/each}
	</div>
</div>

<style>
	.thumb {
		transition: left 220ms cubic-bezier(0.22, 1, 0.36, 1);
	}
</style>
