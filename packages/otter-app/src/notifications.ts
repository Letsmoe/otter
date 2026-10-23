// Per-profile notifications from the backend's watchlist watcher.
import { otter } from './api';
import { backdropUrl } from './tmdb';
import { detailPath, episodePath, mediaKindOf } from './discovery';
import type { NotificationKind } from '@neoworks-dev/otter-sdk/client';

export interface NotificationItem {
	id: string;
	kind: NotificationKind;
	heading: string;
	body: string | null;
	createdAt: string;
	read: boolean;
	titleName: string;
	imageUrl: string | null;
	href: string;
}

const NOTIFICATION_SELECT = {
	id: true,
	kind: true,
	heading: true,
	body: true,
	createdAt: true,
	read: true,
	title: { id: true, mediaType: true, title: true, backdropPath: true },
	episode: { id: true, seasonNumber: true, episodeNumber: true, name: true, stillPath: true }
} as const;

interface FetchedNotification {
	id: string;
	kind: NotificationKind;
	heading: string;
	body: string | null;
	createdAt: string;
	read: boolean;
	title: {
		id: string;
		mediaType: 'MOVIE' | 'SERIES' | 'EPISODE';
		title: string;
		backdropPath: string | null;
	};
	episode: {
		id: string;
		seasonNumber: number;
		episodeNumber: number;
		name: string | null;
		stillPath: string | null;
	} | null;
}

// Episodes link to their series with the episode selected and show its still.
function toNotificationItem(notification: FetchedNotification): NotificationItem {
	const title = notification.title;
	let href = detailPath({ id: title.id, mediaType: mediaKindOf(title.mediaType) });
	let imageUrl = backdropUrl(title.backdropPath, 'w780');
	if (notification.episode) {
		href = episodePath(title.id, notification.episode);
		imageUrl = backdropUrl(notification.episode.stillPath, 'w780') || imageUrl;
	}
	return {
		id: notification.id,
		kind: notification.kind,
		heading: notification.heading,
		body: notification.body,
		createdAt: notification.createdAt,
		read: notification.read,
		titleName: title.title,
		imageUrl,
		href
	};
}

export async function fetchNotifications(
	profileId: string,
	limit = 30
): Promise<NotificationItem[]> {
	try {
		const data = await otter.query({
			notifications: { __args: { profileId, limit }, ...NOTIFICATION_SELECT }
		});
		return data.notifications.map(toNotificationItem);
	} catch {
		return [];
	}
}

export async function fetchUnreadNotificationCount(profileId: string): Promise<number> {
	try {
		const data = await otter.query({ unreadNotificationCount: { __args: { profileId } } });
		return data.unreadNotificationCount;
	} catch {
		return 0;
	}
}

export async function markNotificationsRead(profileId: string): Promise<void> {
	await otter.mutation({ markNotificationsRead: { __args: { profileId } } });
}

/** "Today", "Yesterday", "3 days ago" or a date, for a notification's age. */
export function notificationAge(createdAt: string, now = new Date()): string {
	const created = new Date(createdAt);
	const dayMs = 24 * 60 * 60 * 1000;
	const days = Math.floor((startOfDay(now) - startOfDay(created)) / dayMs);
	if (days <= 0) return 'Today';
	if (days === 1) return 'Yesterday';
	if (days < 7) return `${days} days ago`;
	return created.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

function startOfDay(date: Date): number {
	return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}
