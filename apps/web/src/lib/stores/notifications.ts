import { writable } from 'svelte/store';

// App-wide transient notifications surfaced by the top-right NotificationManager
// (e.g. "Download started"). Entries auto-dismiss after a timeout.
export type NotificationKind = 'info' | 'success' | 'error';

/** A button on a notification, e.g. "Undo". The notification closes when it is used. */
export interface NotificationAction {
	label: string;
	run: () => void;
}

export interface Notification {
	id: string;
	message: string;
	kind: NotificationKind;
	action?: NotificationAction;
}

const AUTO_DISMISS_MS = 6000;

function createNotifications() {
	const { subscribe, update } = writable<Notification[]>([]);
	let counter = 0;

	function dismiss(id: string) {
		update((list) => list.filter((entry) => entry.id !== id));
	}

	function push(
		message: string,
		kind: NotificationKind = 'info',
		action?: NotificationAction
	): string {
		counter += 1;
		const id = `${Date.now()}-${counter}`;
		update((list) => [{ id, message, kind, action }, ...list]);
		setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
		return id;
	}

	return { subscribe, push, dismiss };
}

export const notifications = createNotifications();
