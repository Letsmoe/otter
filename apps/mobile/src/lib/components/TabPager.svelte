<script lang="ts">
	// Home, My List and Downloads side by side in one sliding track. All panes
	// stay mounted, so a swipe shows the neighbour's content while dragging and
	// switching never shows an empty page.
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { swipeTabs } from '$lib/tabs';
	import HomePage from '../../routes/movies/+page.svelte';
	import ListPage from '../../routes/watchlist/+page.svelte';
	import DownloadsPage from '../../routes/downloads/+page.svelte';

	// Movement before a touch counts as a swipe or a scroll.
	const SLOP_PIXELS = 16;
	// Horizontal movement must dominate vertical by this factor to be a swipe.
	const DIRECTION_RATIO = 1.6;
	// Release speed (px/ms) that switches without covering COMMIT_FRACTION.
	const FLING_VELOCITY = 0.6;
	// A fling only counts after the pane moved at least this far (px).
	const FLING_MIN_DISTANCE = 80;
	// Share of the width a swipe must cover to switch without a fling.
	const COMMIT_FRACTION = 0.4;
	// Dragging past the first or last pane pulls at most this far (px).
	const EDGE_MAX_PULL = 40;
	// Finger distance at which the edge pull reaches half its maximum.
	const EDGE_HALF_PULL_DISTANCE = 120;
	// Touches starting here keep their own horizontal gestures.
	const SWIPE_IGNORE = 'video, input, textarea, [contenteditable], [role="dialog"], [data-swipe-ignore]';

	const PANES = [
		{ href: '/movies', component: HomePage, scrollsItself: true },
		{ href: '/watchlist', component: ListPage, scrollsItself: false },
		{ href: '/downloads', component: DownloadsPage, scrollsItself: false }
	].filter((pane) => swipeTabs.some((tab) => tab.href === pane.href));

	interface Touch {
		target: Element;
		startX: number;
		startY: number;
		// Null until the finger leaves the slop radius.
		horizontal: boolean | null;
		lastX: number;
		lastTime: number;
		velocity: number;
	}

	let pager = $state<HTMLDivElement>();
	let dragOffset = $state(0);
	let dragging = $state(false);
	// Set on release so the track slides before the route has changed.
	let targetIndex = $state<number | null>(null);
	let touch: Touch | null = null;

	let routeIndex = $derived(
		Math.max(
			0,
			PANES.findIndex((pane) => pane.href === page.url.pathname)
		)
	);
	let shownIndex = $derived(currentIndex(targetIndex, routeIndex));

	function currentIndex(target: number | null, route: number): number {
		if (target === null) return route;
		return target;
	}

	$effect(() => {
		if (targetIndex === routeIndex) targetIndex = null;
	});

	/** Whether an ancestor of the target can still scroll sideways in this direction. */
	function scrollsSideways(target: Element, deltaX: number): boolean {
		for (let element: Element | null = target; element; element = element.parentElement) {
			if (element === pager) return false;
			if (element.scrollWidth <= element.clientWidth) continue;
			const overflow = getComputedStyle(element).overflowX;
			if (overflow !== 'auto' && overflow !== 'scroll') continue;
			if (deltaX > 0 && element.scrollLeft > 0) return true;
			if (deltaX < 0 && element.scrollLeft + element.clientWidth < element.scrollWidth - 1) return true;
		}
		return false;
	}

	function isSwipe(active: Touch, deltaX: number, deltaY: number): boolean {
		if (Math.abs(deltaX) <= Math.abs(deltaY) * DIRECTION_RATIO) return false;
		if (active.target.closest(SWIPE_IGNORE)) return false;
		return !scrollsSideways(active.target, deltaX);
	}

	// Past the first or last pane the track follows the finger less and less,
	// approaching EDGE_MAX_PULL but never passing it.
	function resisted(deltaX: number): number {
		const pastStart = shownIndex === 0 && deltaX > 0;
		const pastEnd = shownIndex === PANES.length - 1 && deltaX < 0;
		if (!pastStart && !pastEnd) return deltaX;
		const distance = Math.abs(deltaX);
		const pull = EDGE_MAX_PULL * (distance / (distance + EDGE_HALF_PULL_DISTANCE));
		return Math.sign(deltaX) * pull;
	}

	function onTouchStart(event: TouchEvent) {
		touch = null;
		if (event.touches.length !== 1 || !(event.target instanceof Element)) return;
		const point = event.touches[0];
		touch = {
			target: event.target,
			startX: point.clientX,
			startY: point.clientY,
			horizontal: null,
			lastX: point.clientX,
			lastTime: event.timeStamp,
			velocity: 0
		};
	}

	function onTouchMove(event: TouchEvent) {
		const active = touch;
		if (!active) return;
		const point = event.touches[0];
		const deltaX = point.clientX - active.startX;
		const deltaY = point.clientY - active.startY;
		if (active.horizontal === null) {
			if (Math.abs(deltaX) < SLOP_PIXELS && Math.abs(deltaY) < SLOP_PIXELS) return;
			active.horizontal = isSwipe(active, deltaX, deltaY);
		}
		if (!active.horizontal) return;

		if (event.cancelable) event.preventDefault();
		const elapsed = event.timeStamp - active.lastTime;
		if (elapsed > 0) active.velocity = (point.clientX - active.lastX) / elapsed;
		active.lastX = point.clientX;
		active.lastTime = event.timeStamp;
		dragging = true;
		dragOffset = resisted(deltaX);
	}

	/** Which pane a released swipe settles on. */
	function settledIndex(active: Touch, width: number): number {
		const flung =
			Math.abs(active.velocity) > FLING_VELOCITY && Math.abs(dragOffset) > FLING_MIN_DISTANCE;
		const far = Math.abs(dragOffset) > width * COMMIT_FRACTION;
		if (!flung && !far) return shownIndex;
		// Finger moving left (negative offset) reveals the next pane.
		let direction = -Math.sign(dragOffset);
		if (flung) direction = -Math.sign(active.velocity);
		return Math.min(PANES.length - 1, Math.max(0, shownIndex + direction));
	}

	function onTouchEnd() {
		const active = touch;
		touch = null;
		if (!active?.horizontal || !pager) return;
		const next = settledIndex(active, pager.clientWidth);
		dragging = false;
		dragOffset = 0;
		if (next === shownIndex) return;
		targetIndex = next;
		goto(PANES[next].href);
	}

	// Registered by hand: touchmove must not be passive so a sideways drag can
	// cancel the pane's vertical scroll.
	$effect(() => {
		const element = pager;
		if (!element) return;
		element.addEventListener('touchstart', onTouchStart, { passive: true });
		element.addEventListener('touchmove', onTouchMove, { passive: false });
		element.addEventListener('touchend', onTouchEnd);
		element.addEventListener('touchcancel', onTouchEnd);
		return () => {
			element.removeEventListener('touchstart', onTouchStart);
			element.removeEventListener('touchmove', onTouchMove);
			element.removeEventListener('touchend', onTouchEnd);
			element.removeEventListener('touchcancel', onTouchEnd);
		};
	});
</script>

<div bind:this={pager} class="h-full overflow-hidden">
	<div
		class="pager-track flex h-full"
		class:pager-track-dragging={dragging}
		style:transform="translateX(calc({-shownIndex * 100}% + {dragOffset}px))"
	>
		{#each PANES as pane (pane.href)}
			<section
				class="h-full w-full shrink-0 {pane.scrollsItself
					? ''
					: 'no-scrollbar pb-bottom-nav overflow-y-auto overscroll-y-contain px-3 pt-[var(--safe-top)]'}"
				aria-hidden={pane.href !== PANES[shownIndex].href}
			>
				<pane.component />
			</section>
		{/each}
	</div>
</div>

<style>
	/* Settles with a fast start and a long, soft ease-out. */
	.pager-track {
		transition: transform 460ms cubic-bezier(0.22, 1, 0.36, 1);
		will-change: transform;
	}

	.pager-track-dragging {
		transition: none;
	}

	@media (prefers-reduced-motion: reduce) {
		.pager-track {
			transition: none;
		}
	}
</style>
