<script lang="ts">
	import { untrack } from 'svelte';
	import './layout.css';
	import '$lib/tv.css';
	import BottomNav from '$lib/components/BottomNav.svelte';
	import NotificationManager from '$lib/components/NotificationManager.svelte';
	import ProfileGate from '$lib/components/profiles/ProfileGate.svelte';
	import ProfileFlight from '$lib/components/profiles/ProfileFlight.svelte';
	import SplashIntro from '$lib/components/SplashIntro.svelte';
	import { isNativeApp } from '$lib/server';
	import { goto, onNavigate } from '$app/navigation';
	import { page } from '$app/state';
	import TabPager from '$lib/components/TabPager.svelte';
	import { swipeTabs } from '$lib/tabs';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import { downloads } from '$lib/stores/downloads.svelte';
	import { together } from '$lib/stores/together.svelte';
	import TogetherSheet from '$lib/components/together/TogetherSheet.svelte';
	import { App } from '@capacitor/app';
	import { INVITE_LINK_PREFIX, roomCodeFromText } from '@neoworks-dev/otter-app/together';

	let { children } = $props();

	// The logo intro is the native app's launch splash: once per cold start,
	// not on WebView reloads (e.g. after switching servers) or in a browser.
	const INTRO_SHOWN_KEY = 'otter:intro-shown';
	let introPlaying = $state(shouldPlayIntro());

	function shouldPlayIntro(): boolean {
		if (!isNativeApp()) return false;
		return sessionStorage.getItem(INTRO_SHOWN_KEY) === null;
	}

	function finishIntro() {
		sessionStorage.setItem(INTRO_SHOWN_KEY, '1');
		introPlaying = false;
	}

	$effect(() => {
		const profile = $activeProfile;
		if (!profile) {
			downloads.follow(null);
			return;
		}
		downloads.follow(profile.id);
	});

	$effect(() => {
		together.start();
	});

	// Invite links (otter://together/CODE) open the Watch Together page.
	$effect(() => {
		if (!isNativeApp()) return;
		const listener = App.addListener('appUrlOpen', (event) => openInvite(event.url));
		return () => {
			listener.then((handle) => handle.remove());
		};
	});

	function openInvite(url: string) {
		if (!url.startsWith(INVITE_LINK_PREFIX)) return;
		const code = roomCodeFromText(url);
		if (code) together.openSheet(code);
	}

	// Picking a profile mounts the app at once, so Home loads while the gate
	// fades and the app pulls into focus (revealing).
	const REVEAL_MS = 1600;
	let revealing = $state(false);
	let hadProfile = untrack(() => $activeProfile !== null);

	$effect(() => {
		const signedIn = $activeProfile !== null;
		if (signedIn && !hadProfile) {
			revealing = true;
			setTimeout(() => (revealing = false), REVEAL_MS);
		}
		hadProfile = signedIn;
	});

	function isSwipeTabPath(pathname: string | undefined): boolean {
		return swipeTabs.some((tab) => tab.href === pathname);
	}

	// Slides pages like a native stack: forward pushes in from the right, back
	// (browser or back button) reveals the previous page from the left. Tab to
	// tab moves are left to the pager's own slide.
	onNavigate((navigation) => {
		if (!document.startViewTransition) return;
		const fromTab = isSwipeTabPath(navigation.from?.url.pathname);
		const toTab = isSwipeTabPath(navigation.to?.url.pathname);
		if (fromTab && toTab) return;
		let direction = 'forward';
		if (navigation.type === 'popstate') direction = 'back';
		document.documentElement.dataset.navDirection = direction;
		return new Promise((resolve) => {
			document.startViewTransition(async () => {
				resolve();
				await navigation.complete;
			});
		});
	});

	let onSwipeTab = $derived(swipeTabs.some((tab) => tab.href === page.url.pathname));

	// The swipeable tabs live in the pager, whose panes scroll themselves.
	function mainClass(): string {
		if (onSwipeTab) return 'overflow-hidden';
		return 'no-scrollbar pb-bottom-nav overflow-x-hidden overflow-y-auto overscroll-y-contain px-3 pt-[var(--safe-top)]';
	}

	// Opening the sidebar via the left edge is handled by the FocusList that
	// currently owns keyboard navigation (JS-tracked position); the layout only
	// handles global shortcuts.
	function onWindowKeydown(e: KeyboardEvent) {
		if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
			e.preventDefault();
			goto('/search');
		}
	}
</script>

<svelte:window onkeydown={onWindowKeydown} />

{#if $activeProfile === null}
	<ProfileGate />
{:else}
	<div class="bg-base-100 flex h-dvh flex-col overflow-hidden">
		<main data-swipe-page class="min-h-0 min-w-0 flex-1 {mainClass()}" class:app-reveal={revealing}>
			{#if onSwipeTab}
				<TabPager />
			{:else}
				{@render children()}
			{/if}
		</main>
		<BottomNav />
	</div>
	{#if together.sheetOpen}
		<TogetherSheet />
	{/if}
{/if}

<ProfileFlight />
<NotificationManager />

{#if introPlaying}
	<SplashIntro ondone={finishIntro} />
{/if}
