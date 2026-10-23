<script lang="ts">
	// Audio and subtitle tracks of the current stream, in two columns.
	import CheckIcon from 'phosphor-svelte/lib/CheckIcon';
	import PlayerSheet from './PlayerSheet.svelte';

	export interface Track {
		index: number;
		label: string;
	}

	interface Props {
		audioTracks: Track[];
		subtitleTracks: Track[];
		audioIndex: number;
		/** -1 = subtitles off. */
		subtitleIndex: number;
		onaudio: (index: number) => void;
		onsubtitle: (index: number) => void;
		onclose: () => void;
	}

	let { audioTracks, subtitleTracks, audioIndex, subtitleIndex, onaudio, onsubtitle, onclose }: Props =
		$props();

	let subtitleOptions = $derived([{ index: -1, label: 'Off' }, ...subtitleTracks]);
</script>

<PlayerSheet {onclose}>
	<div class="grid min-h-0 flex-1 grid-cols-2 gap-10 px-10 pt-6 pb-6">
		<section class="flex min-h-0 flex-col gap-3">
			<h2 class="text-lg font-bold">Audio</h2>
			<div class="no-scrollbar flex flex-col overflow-y-auto">
				{#each audioTracks as track (track.index)}
					<button
						class="flex items-center gap-3 py-2.5 text-left {track.index === audioIndex
							? 'font-bold text-white'
							: 'text-white/55'}"
						onclick={() => onaudio(track.index)}
					>
						<span class="w-5">{#if track.index === audioIndex}<CheckIcon size={18} />{/if}</span>
						{track.label}
					</button>
				{:else}
					<p class="py-2.5 text-white/55">Original</p>
				{/each}
			</div>
		</section>
		<section class="flex min-h-0 flex-col gap-3">
			<h2 class="text-lg font-bold">Subtitles</h2>
			<div class="no-scrollbar flex flex-col overflow-y-auto">
				{#each subtitleOptions as track (track.index)}
					<button
						class="flex items-center gap-3 py-2.5 text-left {track.index === subtitleIndex
							? 'font-bold text-white'
							: 'text-white/55'}"
						onclick={() => onsubtitle(track.index)}
					>
						<span class="w-5">{#if track.index === subtitleIndex}<CheckIcon size={18} />{/if}</span>
						{track.label}
					</button>
				{/each}
			</div>
		</section>
	</div>
</PlayerSheet>
