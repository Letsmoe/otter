import { writable } from 'svelte/store';

// Which zone currently owns keyboard focus. The sidebar opens when content-zone
// lists report a left edge, and closes (returning focus to content) on right
// edge / Escape. TV navigation is entirely keyboard-driven.
export type FocusZone = 'sidebar' | 'content';

function createSidebarStore() {
	const { subscribe, set, update } = writable<{ open: boolean; zone: FocusZone }>({
		open: false,
		zone: 'content'
	});

	return {
		subscribe,
		openSidebar() {
			set({ open: true, zone: 'sidebar' });
		},
		closeSidebar() {
			set({ open: false, zone: 'content' });
		},
		toggle() {
			update((s) => ({ open: !s.open, zone: s.open ? 'content' : 'sidebar' }));
		}
	};
}

export const sidebarFocus = createSidebarStore();
