<script lang="ts">
	// Round glass back, Watch Together and search buttons floating over a title page's banner.
	import ArrowLeftIcon from 'phosphor-svelte/lib/ArrowLeftIcon';
	import MagnifyingGlassIcon from 'phosphor-svelte/lib/MagnifyingGlassIcon';
	import TogetherButton from '$lib/components/together/TogetherButton.svelte';
</script>

<div
	class="pointer-events-none fixed inset-x-0 top-0 z-30 flex justify-between px-3 pt-[calc(var(--safe-top)+8px)]"
>
	<button
		class="glass-bar pointer-events-auto flex h-10 w-10 items-center justify-center rounded-full"
		aria-label="Back"
		onclick={() => history.back()}
	>
		<ArrowLeftIcon size={20} />
	</button>
	<div class="flex gap-2">
		<TogetherButton glass size={20} />
		<a
			href="/search"
			class="glass-bar pointer-events-auto flex h-10 w-10 items-center justify-center rounded-full"
			aria-label="Search"
		>
			<MagnifyingGlassIcon size={20} />
		</a>
	</div>
</div>
