<script lang="ts">
	// Draggable progress bar: buffered range, played range and a small glass
	// thumb that swells (and the track thickens) while dragging. Reports the target time on release.
	interface Props {
		currentTime: number;
		duration: number;
		bufferedEnd: number;
		onseek: (seconds: number) => void;
		/** The time under the finger while dragging, null when released. */
		onpreview?: (seconds: number | null) => void;
	}

	let { currentTime, duration, bufferedEnd, onseek, onpreview }: Props = $props();

	let track = $state<HTMLDivElement>();
	let dragTime = $state<number | null>(null);

	let dragging = $derived(dragTime !== null);
	let shownTime = $derived(currentDisplayTime(dragTime, currentTime));
	let playedPercent = $derived(percentOf(shownTime));
	let bufferedPercent = $derived(percentOf(bufferedEnd));

	function currentDisplayTime(dragging: number | null, playing: number): number {
		if (dragging === null) return playing;
		return dragging;
	}

	function percentOf(seconds: number): number {
		if (!duration || !isFinite(duration)) return 0;
		return Math.min(100, Math.max(0, (seconds / duration) * 100));
	}

	function timeAt(clientX: number): number {
		if (!track || !duration) return 0;
		const rect = track.getBoundingClientRect();
		const fraction = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
		return fraction * duration;
	}

	function onPointerDown(event: PointerEvent) {
		track?.setPointerCapture(event.pointerId);
		dragTime = timeAt(event.clientX);
		onpreview?.(dragTime);
	}

	function onPointerMove(event: PointerEvent) {
		if (dragTime === null) return;
		dragTime = timeAt(event.clientX);
		onpreview?.(dragTime);
	}

	function onPointerUp() {
		if (dragTime === null) return;
		onseek(dragTime);
		dragTime = null;
		onpreview?.(null);
	}
</script>

<div
	bind:this={track}
	class="relative flex h-8 cursor-pointer touch-none items-center"
	role="slider"
	tabindex="0"
	aria-label="Seek"
	aria-valuemin={0}
	aria-valuemax={Math.round(duration)}
	aria-valuenow={Math.round(shownTime)}
	onpointerdown={onPointerDown}
	onpointermove={onPointerMove}
	onpointerup={onPointerUp}
	onpointercancel={onPointerUp}
>
	<div
		class="scrub-track relative w-full overflow-hidden rounded-full bg-white/20"
		class:scrub-track-dragging={dragging}
	>
		<div class="absolute inset-y-0 left-0 bg-white/30" style:width="{bufferedPercent}%"></div>
		<div class="absolute inset-y-0 left-0 bg-white" style:width="{playedPercent}%"></div>
	</div>
	<div
		class="scrub-thumb glass-bar absolute -translate-x-1/2 rounded-full"
		class:scrub-thumb-dragging={dragging}
		style:left="{playedPercent}%"
	></div>
</div>

<style>
	.scrub-track {
		height: 3px;
		transition: height 180ms ease-out;
	}

	.scrub-track-dragging {
		height: 6px;
	}

	/* The buttons' glass, tinted light so it stands out on the video; swells
	   under the finger. */
	.scrub-thumb {
		width: 14px;
		height: 14px;
		background-color: color-mix(in oklab, var(--color-base-content) 45%, transparent);
		transition:
			width 240ms cubic-bezier(0.34, 1.56, 0.64, 1),
			height 240ms cubic-bezier(0.34, 1.56, 0.64, 1);
	}

	.scrub-thumb-dragging {
		width: 34px;
		height: 34px;
	}
</style>
