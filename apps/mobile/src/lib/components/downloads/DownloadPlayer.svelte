<script lang="ts">
	// Plays a finished download from its local file.
	import type { DownloadItem } from '@neoworks-dev/otter-app/downloads';
	import Player from '$lib/components/player/Player.svelte';

	interface Props {
		item: DownloadItem;
		onclose: () => void;
	}

	let { item, onclose }: Props = $props();

	function title(): string {
		if (!item.episode) return item.titleName;
		const code = `S${item.episode.seasonNumber}:E${item.episode.episodeNumber}`;
		return `${item.titleName} · ${code} ${item.episode.name || ''}`.trim();
	}
</script>

<Player
	src={item.url || ''}
	mimeType={item.mimeType || ''}
	title={title()}
	mediaId={item.mediaId}
	{onclose}
/>
