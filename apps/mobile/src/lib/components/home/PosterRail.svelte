<script lang="ts">
	// Titled row of posters that scrolls sideways. Contains its own overscroll so
	// reaching the end doesn't swipe the home pager to the next category.
	import { detailPath, type RailItem } from '@neoworks-dev/otter-app/discovery';

	const POSTER_WIDTHS = {
		regular: 'w-28',
		large: 'w-40'
	};

	interface Props {
		title: string;
		items: RailItem[];
		size?: keyof typeof POSTER_WIDTHS;
	}

	let { title, items, size = 'regular' }: Props = $props();
</script>

{#if items.length > 0}
	<section class="flex flex-col gap-2">
		<h2 class="text-base font-semibold">{title}</h2>
		<div
			class="no-scrollbar -mx-3 flex snap-x gap-2 overflow-x-auto overscroll-x-contain scroll-px-3 px-3"
		>
			{#each items as item (item.id)}
				<a
					href={detailPath(item)}
					class="relative aspect-[2/3] {POSTER_WIDTHS[size]} shrink-0 snap-start overflow-hidden rounded-md bg-white/5"
				>
					{#if item.posterUrl}
						<img src={item.posterUrl} alt={item.title} loading="lazy" class="h-full w-full object-cover" />
					{:else}
						<span class="flex h-full items-center justify-center p-2 text-center text-xs">
							{item.title}
						</span>
					{/if}
					{#if item.badge}
						<span
							class="absolute top-1.5 left-1.5 rounded bg-black/75 px-1.5 py-0.5 text-[11px] font-semibold"
						>
							{item.badge}
						</span>
					{/if}
					{#if item.progress !== undefined}
						<span class="absolute inset-x-0 bottom-0 h-1 bg-white/25">
							<span class="bg-error block h-full" style:width="{item.progress * 100}%"></span>
						</span>
					{/if}
				</a>
			{/each}
		</div>
	</section>
{/if}
