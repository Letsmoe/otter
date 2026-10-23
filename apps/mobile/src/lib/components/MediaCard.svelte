<script lang="ts">
	// A normalized poster card. Pages build CardItem from whatever query shape
	// they have (movie, series, …) so this component stays source-agnostic.
	import type { CardItem } from '@neoworks-dev/otter-app/discovery';
	interface Props {
		item: CardItem;
		onclick: () => void;
	}

	let { item, onclick }: Props = $props();
</script>

<button
	type="button"
	{onclick}
	class="group flex flex-col gap-2 text-left focus:outline-none"
>
	<div
		class="bg-base-300 ring-base-300 group-hover:ring-primary relative aspect-[2/3] w-full overflow-hidden rounded-xl ring-1 transition-all group-hover:-translate-y-1 group-hover:shadow-xl"
	>
		{#if item.posterUrl}
			<img
				src={item.posterUrl}
				alt={item.title}
				loading="lazy"
				class="h-full w-full object-cover"
			/>
		{:else}
			<div class="flex h-full w-full items-center justify-center">
				<span class="text-5xl opacity-10">🎬</span>
			</div>
		{/if}
	</div>
	<div class="px-0.5">
		<p class="line-clamp-1 text-sm font-medium">{item.title}</p>
		{#if item.subtitle}
			<p class="text-base-content/40 text-xs">{item.subtitle}</p>
		{/if}
	</div>
</button>
