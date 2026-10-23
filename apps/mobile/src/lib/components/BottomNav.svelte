<!--
	Phone tab bar: the main destinations within thumb reach.
-->
<script lang="ts">
	import { page } from '$app/state';
	import { activeTabIndex, isTabActive, tabs, type Tab } from '$lib/tabs';
	import { vpnStatus } from '$lib/stores/vpn';
	import { activeProfile, avatarFor } from '@neoworks-dev/otter-app/profile';
	import { profileFlight } from '$lib/stores/profileFlight.svelte';

	const PROFILE_HREF = '/profile';

	// Dot colour on the Profile tab: green on VPN, red when a required VPN is
	// down, amber when it's down but not required.
	function vpnDotClass(): string {
		if ($vpnStatus.connected) return 'bg-success';
		if ($vpnStatus.required) return 'bg-error';
		return 'bg-warning';
	}

	function vpnLabel(): string {
		if ($vpnStatus.connected) return 'VPN connected';
		return 'VPN not connected';
	}

	function isActive(tab: Tab): boolean {
		return isTabActive(tab, page.url.pathname);
	}

	function iconWeight(tab: Tab): 'fill' | 'regular' {
		if (isActive(tab)) return 'fill';
		return 'regular';
	}

	// -1 on pages without a tab (detail pages): the pill fades out.
	let activeIndex = $derived(activeTabIndex(page.url.pathname));
</script>

<nav
	class="bottom-nav glass-bar fixed inset-x-3 z-40 flex rounded-[1.75rem] p-1.5"
	aria-label="Main"
>
	<span
		class="glass-pill bg-base-content/10 absolute inset-y-1.5 left-1.5 rounded-[1.375rem]"
		class:opacity-0={activeIndex === -1}
		style:width="calc((100% - 0.75rem) / {tabs.length})"
		style:transform="translateX({Math.max(activeIndex, 0) * 100}%)"
	></span>
	{#each tabs as tab (tab.href)}
		<a
			href={tab.href}
			class="text-base-content/60 relative flex h-13 flex-1 flex-col items-center justify-center gap-0.5 text-[10px] transition-colors"
			class:bottom-tab-active={isActive(tab)}
		>
			<span class="bottom-tab-icon relative flex">
				{#if tab.href === PROFILE_HREF && $activeProfile}
					<!-- The picked avatar; hidden while the picker's avatar flies in. -->
					<img
						src={avatarFor($activeProfile)}
						alt=""
						data-profile-tab-icon
						class="profile-tab-avatar my-0.5 h-[18px] w-[18px] rounded-[5px]"
						class:opacity-0={profileFlight.inFlight}
						class:profile-tab-avatar-landed={!profileFlight.inFlight}
					/>
				{:else}
					<tab.icon size={22} weight={iconWeight(tab)} />
				{/if}
				{#if tab.href === PROFILE_HREF && $vpnStatus.known}
					<span
						class="ring-base-200 absolute -top-0.5 -right-1 h-2.5 w-2.5 rounded-full ring-2 {vpnDotClass()}"
						title={vpnLabel()}
						aria-label={vpnLabel()}
					></span>
				{/if}
			</span>
			<span class="max-w-full truncate px-1">{tab.label}</span>
		</a>
	{/each}
</nav>

<style>
	/* Floats above the safe area; the feed scrolls under it and shows through blurred. */
	.bottom-nav {
		bottom: calc(0.75rem + var(--safe-bottom));
	}

	.bottom-tab-active {
		color: var(--color-base-content);
		font-weight: 600;
	}

	/* Pops once when a tab becomes active. */
	.bottom-tab-active .bottom-tab-icon {
		animation: tab-pop 380ms cubic-bezier(0.34, 1.56, 0.64, 1);
	}

	@keyframes tab-pop {
		0% {
			scale: 0.8;
		}
		100% {
			scale: 1;
		}
	}

	/* Active outline instead of a filled icon. */
	.bottom-tab-active .profile-tab-avatar {
		outline: 1.5px solid var(--color-base-content);
		outline-offset: 1px;
	}

	/* A bounce as the flying avatar lands. */
	.profile-tab-avatar-landed {
		animation: avatar-land 420ms cubic-bezier(0.34, 1.56, 0.64, 1);
	}

	@keyframes avatar-land {
		0% {
			scale: 0.7;
		}
		100% {
			scale: 1;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.bottom-tab-active .bottom-tab-icon,
		.profile-tab-avatar-landed {
			animation: none;
		}
	}
</style>
