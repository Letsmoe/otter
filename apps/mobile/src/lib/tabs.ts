import type { Component } from 'svelte';
import HouseIcon from 'phosphor-svelte/lib/HouseIcon';
import BookmarkSimpleIcon from 'phosphor-svelte/lib/BookmarkSimpleIcon';
import DownloadSimpleIcon from 'phosphor-svelte/lib/DownloadSimpleIcon';
import UserCircleIcon from 'phosphor-svelte/lib/UserCircleIcon';

export interface Tab {
	label: string;
	href: string;
	icon: Component<{ size?: number; weight?: 'regular' | 'fill' }>;
	/** Further path prefixes that belong to this tab. */
	matches: string[];
}

export const tabs: Tab[] = [
	{ label: 'Home', href: '/movies', icon: HouseIcon, matches: [] },
	{ label: 'My List', href: '/watchlist', icon: BookmarkSimpleIcon, matches: [] },
	{ label: 'Downloads', href: '/downloads', icon: DownloadSimpleIcon, matches: ['/downloads'] },
	{
		label: 'Profile',
		href: '/profile',
		icon: UserCircleIcon,
		matches: ['/history', '/settings', '/plugins', '/notifications']
	}
];

function pathMatches(pathname: string, prefix: string): boolean {
	return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

/** Whether the tab points at the current page. Detail pages belong to no tab. */
export function isTabActive(tab: Tab, pathname: string): boolean {
	if (pathname === tab.href) return true;
	return tab.matches.some((prefix) => pathMatches(pathname, prefix));
}

/** Index of the active tab, or -1 on pages without one. */
export function activeTabIndex(pathname: string): number {
	return tabs.findIndex((tab) => isTabActive(tab, pathname));
}

/** Tabs that sideways swipes move between, in tab bar order. */
export const swipeTabs: Tab[] = tabs.filter((tab) => tab.href !== '/profile');
