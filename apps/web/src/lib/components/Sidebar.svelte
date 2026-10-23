<script lang="ts">
	import { page } from '$app/stores';
	import MagnifyingGlassIcon from 'phosphor-svelte/lib/MagnifyingGlassIcon';
	import CompassIcon from 'phosphor-svelte/lib/CompassIcon';
	import FilmSlateIcon from 'phosphor-svelte/lib/FilmSlateIcon';
	import BookmarkSimpleIcon from 'phosphor-svelte/lib/BookmarkSimpleIcon';
	import ClockCounterClockwiseIcon from 'phosphor-svelte/lib/ClockCounterClockwiseIcon';
	import DownloadSimpleIcon from 'phosphor-svelte/lib/DownloadSimpleIcon';
	import PlugsIcon from 'phosphor-svelte/lib/PlugsIcon';
	import GearSixIcon from 'phosphor-svelte/lib/GearSixIcon';
	import FilmReelIcon from 'phosphor-svelte/lib/FilmReelIcon';
	import SignOutIcon from 'phosphor-svelte/lib/SignOutIcon';
	import type { Component } from 'svelte';
	import VpnGuard from './VpnGuard.svelte';
	import { activeProfile, avatarFor } from '@neoworks-dev/otter-app/profile';

	type NavEntry = {
		key: string;
		label: string;
		icon: Component;
		href: string;
		// Match the route exactly rather than by prefix (e.g. /search).
		exact?: boolean;
	};

	const entries: NavEntry[] = [
		{ key: 'search', label: 'Search', icon: MagnifyingGlassIcon, href: '/search', exact: true },
		{ key: 'explore', label: 'Explore', icon: CompassIcon, href: '/movies' },
		{ key: 'library', label: 'Library', icon: FilmSlateIcon, href: '/library' },
		{ key: 'watchlist', label: 'Watchlist', icon: BookmarkSimpleIcon, href: '/watchlist' },
		{ key: 'history', label: 'History', icon: ClockCounterClockwiseIcon, href: '/history' },
		{ key: 'downloads', label: 'Downloads', icon: DownloadSimpleIcon, href: '/downloads' },
		{ key: 'plugins', label: 'Plugins', icon: PlugsIcon, href: '/plugins' },
		{ key: 'settings', label: 'Settings', icon: GearSixIcon, href: '/settings' }
	];

	function isActive(entry: NavEntry): boolean {
		if (entry.exact) return $page.url.pathname === entry.href;
		return $page.url.pathname.startsWith(entry.href);
	}
</script>

<!-- Persistent desktop sidebar: full height, never scrolls with the content. -->
<aside
	class="border-base-300 bg-base-200 flex h-screen w-60 shrink-0 flex-col border-r"
	role="navigation"
	aria-label="Main menu"
>
	<!-- Header -->
	<div class="border-base-300 flex h-14 shrink-0 items-center gap-2 border-b px-4">
		<span class="text-primary shrink-0"><FilmReelIcon size={22} weight="fill" /></span>
		<span class="text-base-content text-base font-semibold tracking-tight">Otter</span>
	</div>

	<nav class="flex flex-1 flex-col gap-0.5 overflow-y-auto p-2">
		{#each entries as entry (entry.key)}
			{@const active = isActive(entry)}
			<a
				href={entry.href}
				class="flex h-9 items-center gap-2 rounded-md px-2 text-[13px] font-medium transition-colors duration-150 {active
					? 'bg-base-300 text-base-content'
					: 'text-base-content/60 hover:bg-base-300/50 hover:text-base-content'}"
			>
				<span class="flex h-5 w-5 shrink-0 items-center justify-center">
					<entry.icon size={17} weight={active ? 'fill' : 'regular'} />
				</span>
				<span class="flex-1 text-left">{entry.label}</span>
			</a>
		{/each}
	</nav>

	<!-- Current user -->
	<div class="border-base-300 flex shrink-0 flex-col gap-1 border-t p-2">
		{#if $activeProfile}
			<button
				type="button"
				onclick={() => activeProfile.logout()}
				class="text-base-content/70 hover:bg-base-300 group flex h-11 items-center gap-2 rounded-md px-2 transition-colors"
				title="Switch profile"
			>
				<img
					src={avatarFor($activeProfile)}
					alt={$activeProfile.name}
					class="h-7 w-7 shrink-0 rounded-lg"
				/>
				<span class="flex-1 truncate text-left text-[13px] font-medium">{$activeProfile.name}</span>
				<SignOutIcon size={16} class="opacity-0 transition-opacity group-hover:opacity-60" />
			</button>
		{/if}
	</div>

	<!-- Footer -->
	<div class="border-base-300 flex shrink-0 items-center justify-between border-t p-3">
		<VpnGuard />
		<span class="text-base-content/20 text-[9px] font-medium">v0.1.0</span>
	</div>
</aside>
