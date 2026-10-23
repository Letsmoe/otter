<script module lang="ts">
	export interface PlayerEpisode {
		id: string;
		seasonNumber: number;
		episodeNumber: number;
		name: string | null;
		overview: string | null;
		stillUrl: string | null;
		// Watch fraction (0..1); undefined when unwatched.
		progress?: number;
	}
</script>

<script lang="ts">
	// Netflix's episodes panel: season switch on top, the season's episodes as a
	// horizontal row of cards, the playing one marked.
	import { onMount } from 'svelte';
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';
	import PlayerSheet from './PlayerSheet.svelte';

	interface Props {
		episodes: PlayerEpisode[];
		currentEpisodeId?: string;
		onepisode: (episodeId: string) => void;
		onclose: () => void;
	}

	let { episodes, currentEpisodeId, onepisode, onclose }: Props = $props();

	let seasonNumbers = $derived([...new Set(episodes.map((episode) => episode.seasonNumber))]);
	let shownSeason = $state(currentSeason());
	let seasonEpisodes = $derived(episodes.filter((episode) => episode.seasonNumber === shownSeason));
	let row = $state<HTMLDivElement>();

	function currentSeason(): number {
		const current = episodes.find((episode) => episode.id === currentEpisodeId);
		if (current) return current.seasonNumber;
		if (episodes.length > 0) return episodes[0].seasonNumber;
		return 1;
	}

	function seasonLabel(seasonNumber: number): string {
		if (seasonNumber === 0) return 'Specials';
		return `Season ${seasonNumber}`;
	}

	function episodeName(episode: PlayerEpisode): string {
		if (episode.name) return episode.name;
		return `Episode ${episode.episodeNumber}`;
	}

	onMount(() => {
		row?.querySelector('[data-current]')?.scrollIntoView({ inline: 'center', block: 'nearest' });
	});
</script>

<PlayerSheet {onclose}>
	<div class="flex min-h-0 flex-1 flex-col gap-4 pt-6 pb-5">
		<div class="no-scrollbar flex gap-2 overflow-x-auto px-6 pr-20">
			{#each seasonNumbers as seasonNumber (seasonNumber)}
				<button
					class="h-9 shrink-0 rounded-full px-4 text-sm font-semibold transition-colors {seasonNumber ===
					shownSeason
						? 'bg-white text-black'
						: 'bg-white/10 text-white/70'}"
					onclick={() => (shownSeason = seasonNumber)}
				>
					{seasonLabel(seasonNumber)}
				</button>
			{/each}
		</div>

		<div bind:this={row} class="no-scrollbar flex min-h-0 flex-1 gap-3 overflow-x-auto px-6">
			{#each seasonEpisodes as episode (episode.id)}
				<button
					class="flex w-56 shrink-0 flex-col gap-2 text-left"
					data-current={episode.id === currentEpisodeId || undefined}
					onclick={() => onepisode(episode.id)}
				>
					<div
						class="relative aspect-video w-full overflow-hidden rounded-xl bg-white/5 {episode.id ===
						currentEpisodeId
							? 'ring-2 ring-white'
							: ''}"
					>
						{#if episode.stillUrl}
							<img
								src={episode.stillUrl}
								alt=""
								loading="lazy"
								class="h-full w-full object-cover"
							/>
						{/if}
						<span
							class="glass-bar absolute inset-0 m-auto flex h-10 w-10 items-center justify-center rounded-full"
						>
							<PlayIcon size={18} weight="fill" />
						</span>
						{#if episode.progress !== undefined}
							<div class="absolute inset-x-0 bottom-0 h-1 bg-white/20">
								<div class="bg-primary h-full" style:width="{episode.progress * 100}%"></div>
							</div>
						{/if}
					</div>
					<p class="line-clamp-1 text-sm font-semibold">
						{episode.episodeNumber}. {episodeName(episode)}
					</p>
					{#if episode.overview}
						<p class="line-clamp-3 text-xs leading-snug text-white/60">{episode.overview}</p>
					{/if}
				</button>
			{/each}
		</div>
	</div>
</PlayerSheet>
