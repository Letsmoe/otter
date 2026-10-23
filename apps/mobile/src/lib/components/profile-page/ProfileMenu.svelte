<script lang="ts">
	// The ☰ menu of the profile page: a glass dropdown with the app's other pages.
	import ListIcon from 'phosphor-svelte/lib/ListIcon';
	import GearSixIcon from 'phosphor-svelte/lib/GearSixIcon';
	import PuzzlePieceIcon from 'phosphor-svelte/lib/PuzzlePieceIcon';
	import ClockCounterClockwiseIcon from 'phosphor-svelte/lib/ClockCounterClockwiseIcon';
	import BellIcon from 'phosphor-svelte/lib/BellIcon';
	import UsersIcon from 'phosphor-svelte/lib/UsersIcon';
	import UsersThreeIcon from 'phosphor-svelte/lib/UsersThreeIcon';
	import { together } from '$lib/stores/together.svelte';

	interface Props {
		onswitchprofile: () => void;
	}

	let { onswitchprofile }: Props = $props();

	const LINKS = [
		{ label: 'Notifications', href: '/notifications', icon: BellIcon },
		{ label: 'Watch History', href: '/history', icon: ClockCounterClockwiseIcon },
		{ label: 'Plugins', href: '/plugins', icon: PuzzlePieceIcon },
		{ label: 'Settings', href: '/settings', icon: GearSixIcon }
	];

	let open = $state(false);

	function openTogether() {
		open = false;
		together.openSheet();
	}

	function switchProfile() {
		open = false;
		onswitchprofile();
	}
</script>

<button
	class="flex h-10 w-10 items-center justify-center rounded-full"
	aria-label="Menu"
	aria-expanded={open}
	onclick={() => (open = !open)}
>
	<ListIcon size={24} />
</button>

{#if open}
	<button
		data-backdrop
		class="fixed inset-0 z-40 cursor-default"
		aria-label="Close menu"
		onclick={() => (open = false)}
	></button>
	<nav
		class="profile-menu glass-bar fixed top-[calc(var(--safe-top)+56px)] right-3 z-50 flex w-60 flex-col rounded-2xl p-1"
	>
		<button
			class="flex h-12 items-center gap-3 rounded-xl px-3 text-left text-sm font-semibold"
			onclick={openTogether}
		>
			<UsersThreeIcon size={20} class="opacity-80" />
			Watch Together
		</button>
		{#each LINKS as link (link.href)}
			<a
				href={link.href}
				class="flex h-12 items-center gap-3 rounded-xl px-3 text-sm font-semibold"
				onclick={() => (open = false)}
			>
				<link.icon size={20} class="opacity-80" />
				{link.label}
			</a>
		{/each}
		<button
			class="flex h-12 items-center gap-3 rounded-xl px-3 text-left text-sm font-semibold"
			onclick={switchProfile}
		>
			<UsersIcon size={20} class="opacity-80" />
			Switch Profile
		</button>
	</nav>
{/if}

<style>
	.profile-menu {
		transform-origin: top right;
		animation: menu-in 220ms cubic-bezier(0.34, 1.3, 0.64, 1);
	}

	@keyframes menu-in {
		from {
			opacity: 0;
			scale: 0.92;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.profile-menu {
			animation: none;
		}
	}
</style>
