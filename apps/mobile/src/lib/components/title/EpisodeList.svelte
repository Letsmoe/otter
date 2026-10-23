<script lang="ts">
	// Netflix's episode rows: still with a play badge and progress, "1. Name",
	// runtime and a download button, the description underneath.
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';
	import DownloadButton from '$lib/components/downloads/DownloadButton.svelte';
	import { backdropUrl, formatRuntime } from '@neoworks-dev/otter-app/tmdb';
	import type { SeasonEpisode } from '@neoworks-dev/otter-app/seriesDetail';

	interface Props {
		episodes: SeasonEpisode[];
		onplay: (episode: SeasonEpisode) => void;
	}

	let { episodes, onplay }: Props = $props();

	function episodeName(episode: SeasonEpisode): string {
		if (episode.name) return episode.name;
		return `Episode ${episode.episodeNumber}`;
	}
</script>

<ul class="flex flex-col gap-5">
	{#each episodes as episode (episode.id)}
		<li class="flex flex-col gap-2">
			<div class="flex items-center gap-3">
				<button
					class="relative aspect-video w-32 shrink-0 overflow-hidden rounded-md bg-white/5"
					aria-label="Play {episodeName(episode)}"
					onclick={() => onplay(episode)}
				>
					{#if backdropUrl(episode.stillPath, 'w780')}
						<img
							src={backdropUrl(episode.stillPath, 'w780')}
							alt=""
							loading="lazy"
							class="h-full w-full object-cover"
						/>
					{/if}
					<span
						class="glass-bar absolute inset-0 m-auto flex h-9 w-9 items-center justify-center rounded-full"
					>
						<PlayIcon size={16} weight="fill" />
					</span>
					{#if episode.progress !== undefined}
						<span class="absolute inset-x-0 bottom-0 h-1 bg-white/20">
							<span class="bg-primary block h-full" style:width="{episode.progress * 100}%"></span>
						</span>
					{/if}
				</button>
				<button class="flex min-w-0 flex-1 flex-col text-left" onclick={() => onplay(episode)}>
					<span class="line-clamp-2 text-sm font-semibold">
						{episode.episodeNumber}. {episodeName(episode)}
					</span>
					{#if formatRuntime(episode.runtime)}
						<span class="text-xs text-white/50">{formatRuntime(episode.runtime)}</span>
					{/if}
				</button>
				<DownloadButton
					mediaId={episode.id}
					title="S{episode.seasonNumber}:E{episode.episodeNumber} {episodeName(episode)}"
					variant="icon"
				/>
			</div>
			{#if episode.overview}
				<p class="line-clamp-3 text-xs leading-relaxed text-white/60">{episode.overview}</p>
			{/if}
		</li>
	{/each}
</ul>
