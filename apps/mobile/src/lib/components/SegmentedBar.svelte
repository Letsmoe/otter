<script lang="ts" generics="Value extends string">
	// Glass bar with a sliding pill, styled like the tab bar.
	interface Props {
		options: { value: Value; label: string }[];
		selected: Value;
		onselect: (value: Value) => void;
	}

	let { options, selected, onselect }: Props = $props();

	let selectedIndex = $derived(options.findIndex((option) => option.value === selected));
</script>

<div class="glass-bar relative flex rounded-full p-1">
	<span
		class="glass-pill bg-base-content/10 absolute inset-y-1 left-1 rounded-full"
		style:width="calc((100% - 0.5rem) / {options.length})"
		style:transform="translateX({selectedIndex * 100}%)"
	></span>
	{#each options as option (option.value)}
		<button
			class="relative h-9 flex-1 truncate px-1 text-[13px] transition-colors {option.value ===
			selected
				? 'text-base-content font-semibold'
				: 'text-base-content/60'}"
			onclick={() => onselect(option.value)}
		>
			{option.label}
		</button>
	{/each}
</div>
