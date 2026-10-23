<script lang="ts">
	// Netflix's profile hand-off: the picked avatar shrinks in place, arcs up to
	// the middle right of the screen, then drops into the bottom bar's Profile tab.
	import { tick } from 'svelte';
	import { profileFlight, type Flight } from '$lib/stores/profileFlight.svelte';

	const FLIGHT_MS = 1150;
	// Size of the avatar at the top of its arc, and where that apex sits.
	const APEX_SIZE = 64;
	const APEX_X_FRACTION = 0.72;
	const APEX_Y_FRACTION = 0.38;
	const SHRINK_FRACTION = 0.6;

	let traveller = $state<HTMLImageElement>();

	$effect(() => {
		const flight = profileFlight.flight;
		if (!flight) return;
		fly(flight);
	});

	async function fly(flight: Flight) {
		await tick();
		// Wait a frame so the app (and its bottom bar) has rendered behind the picker.
		await new Promise((resolve) => requestAnimationFrame(resolve));
		const target = document.querySelector('[data-profile-tab-icon]');
		if (!traveller || !target || matchMedia('(prefers-reduced-motion: reduce)').matches) {
			profileFlight.land();
			return;
		}
		const animation = traveller.animate(keyframes(flight.from, target.getBoundingClientRect()), {
			duration: FLIGHT_MS,
			fill: 'forwards'
		});
		await animation.finished.catch(() => {});
		profileFlight.land();
	}

	// Positions are translations of a box laid out at the start rect.
	function frame(
		from: DOMRect,
		centerX: number,
		centerY: number,
		size: number,
		offset: number,
		easing: string
	): Keyframe {
		const scale = size / from.width;
		const shiftX = centerX - (from.left + from.width / 2);
		const shiftY = centerY - (from.top + from.height / 2);
		return { transform: `translate(${shiftX}px, ${shiftY}px) scale(${scale})`, offset, easing };
	}

	function keyframes(from: DOMRect, to: DOMRect): Keyframe[] {
		const startX = from.left + from.width / 2;
		const startY = from.top + from.height / 2;
		const apexX = window.innerWidth * APEX_X_FRACTION;
		const apexY = window.innerHeight * APEX_Y_FRACTION;
		const endX = to.left + to.width / 2;
		const endY = to.top + to.height / 2;
		return [
			frame(from, startX, startY, from.width, 0, 'cubic-bezier(0.4, 0, 0.2, 1)'),
			frame(
				from,
				startX,
				startY,
				from.width * SHRINK_FRACTION,
				0.22,
				'cubic-bezier(0.3, 0, 0.2, 1)'
			),
			frame(from, apexX, apexY, APEX_SIZE, 0.58, 'cubic-bezier(0.5, 0, 0.75, 0.2)'),
			frame(from, endX, endY, to.width, 1, 'linear')
		];
	}
</script>

{#if profileFlight.flight}
	{@const flight = profileFlight.flight}
	<img
		bind:this={traveller}
		src={flight.imageUrl}
		alt=""
		class="pointer-events-none fixed z-[70] rounded-2xl shadow-2xl"
		style:left="{flight.from.left}px"
		style:top="{flight.from.top}px"
		style:width="{flight.from.width}px"
		style:height="{flight.from.height}px"
	/>
{/if}
