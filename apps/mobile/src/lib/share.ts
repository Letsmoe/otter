// Opens the system share menu: Capacitor's native sheet in the app (the
// Android WebView has no navigator.share), the Web Share API in browsers,
// and copying to the clipboard as a last resort.
import { Share } from '@capacitor/share';
import { isNativeApp } from '$lib/server';
import { notifications } from '$lib/stores/notifications';

export interface ShareContent {
	title: string;
	text?: string;
	url?: string;
}

export async function shareContent(content: ShareContent): Promise<void> {
	if (isNativeApp()) {
		await Share.share({ ...content, dialogTitle: content.title }).catch(() => {});
		return;
	}
	if (navigator.share) {
		await navigator.share(content).catch(() => {});
		return;
	}
	await navigator.clipboard?.writeText(clipboardText(content));
	notifications.push('Copied to clipboard', 'success');
}

function clipboardText(content: ShareContent): string {
	return [content.text, content.url].filter(Boolean).join(' ');
}
