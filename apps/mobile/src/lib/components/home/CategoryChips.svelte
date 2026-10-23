<script lang="ts" module>
	export type Category = 'movies' | 'tv' | 'sports' | 'news';

	export const CATEGORIES: { value: Category; label: string }[] = [
		{ value: 'movies', label: 'Movies' },
		{ value: 'tv', label: 'TV Shows' },
		{ value: 'sports', label: 'Sports' },
		{ value: 'news', label: 'News' }
	];
</script>

<script lang="ts">
	import SegmentedBar from '../SegmentedBar.svelte';

	interface Props {
		selected: Category;
		onselect: (category: Category) => void;
	}

	let { selected, onselect }: Props = $props();
</script>

<SegmentedBar options={CATEGORIES} {selected} {onselect} />
