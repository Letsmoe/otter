<script lang="ts">
	// "Trailers & More": the trailer's thumbnail, playing inline on tap.
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';

	interface Props {
		trailerKey: string | null;
		title: string;
	}

	let { trailerKey, title }: Props = $props();

	let trailerPlaying = $state(false);
</script>

{#if trailerKey}
	<div class="aspect-video overflow-hidden rounded-md bg-black">
		{#if trailerPlaying}
			<iframe
				src="https://www.youtube-nocookie.com/embed/{trailerKey}?autoplay=1&playsinline=1"
				title="{title} trailer"
				class="h-full w-full"
				allow="autoplay; encrypted-media; fullscreen"
				allowfullscreen
			></iframe>
		{:else}
			<button
				class="relative h-full w-full"
				aria-label="Play trailer"
				onclick={() => (trailerPlaying = true)}
			>
				<img
					src="https://img.youtube.com/vi/{trailerKey}/hqdefault.jpg"
					alt=""
					class="h-full w-full object-cover"
				/>
				<span
					class="glass-bar absolute inset-0 m-auto flex h-14 w-14 items-center justify-center rounded-full"
				>
					<PlayIcon size={24} weight="fill" />
				</span>
			</button>
		{/if}
	</div>
	<p class="pt-2 text-sm font-semibold">Trailer: {title}</p>
{:else}
	<p class="py-6 text-center text-sm text-white/50">No trailer available.</p>
{/if}
