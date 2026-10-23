<script lang="ts">
	// Glass card with an icon, a title and a chevron linking to the full page.
	import type { Component, Snippet } from 'svelte';
	import CaretRightIcon from 'phosphor-svelte/lib/CaretRightIcon';

	interface Props {
		title: string;
		href: string;
		icon: Component<{ size?: number; weight?: 'regular' | 'fill' }>;
		children: Snippet;
	}

	let { title, href, icon: Icon, children }: Props = $props();
</script>

<section class="glass-bar flex flex-col gap-3 rounded-3xl p-4">
	<a {href} class="flex items-center gap-4">
		<Icon size={24} weight="fill" />
		<h2 class="flex-1 text-lg font-bold">{title}</h2>
		<CaretRightIcon size={20} class="opacity-80" />
	</a>
	{@render children()}
</section>
