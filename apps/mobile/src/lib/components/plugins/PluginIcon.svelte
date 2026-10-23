<script lang="ts">
	// A plugin's icon: an image URL, an emoji, or a plug as fallback.
	import PlugsIcon from 'phosphor-svelte/lib/PlugsIcon';

	interface Props {
		icon: string | null;
		size?: 'small' | 'large';
	}

	let { icon, size = 'small' }: Props = $props();

	let failed = $state(false);

	function isUrl(value: string): boolean {
		return value.startsWith('http://') || value.startsWith('https://') || value.startsWith('/');
	}

	function boxClass(): string {
		if (size === 'large') return 'h-14 w-14 rounded-2xl text-2xl';
		return 'h-10 w-10 rounded-xl text-lg';
	}
</script>

<span class="flex shrink-0 items-center justify-center overflow-hidden bg-white/10 {boxClass()}">
	{#if icon && isUrl(icon) && !failed}
		<img
			src={icon}
			alt=""
			class="h-full w-full object-contain p-1"
			onerror={() => (failed = true)}
		/>
	{:else if icon && !isUrl(icon)}
		{icon}
	{:else}
		<PlugsIcon size={20} class="opacity-60" />
	{/if}
</span>
