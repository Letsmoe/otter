<script lang="ts">
	// Neoworks-style switch. DaisyUI's `toggle` doesn't render in this app, so
	// this is a self-contained Tailwind switch used across settings/plugins.
	interface Props {
		checked: boolean;
		onchange: (value: boolean) => void;
		disabled?: boolean;
		ariaLabel?: string;
	}

	let { checked, onchange, disabled = false, ariaLabel }: Props = $props();
</script>

<button
	type="button"
	role="switch"
	aria-checked={checked}
	aria-label={ariaLabel}
	{disabled}
	onclick={() => onchange(!checked)}
	class="relative inline-flex h-6 w-10 shrink-0 items-center rounded-full transition-colors disabled:opacity-50 {checked
		? 'bg-white'
		: 'bg-base-300'}"
>
	<span
		class="inline-block h-5 w-5 rounded-full transition-transform {checked
			? 'translate-x-[18px] bg-black'
			: 'translate-x-0.5 bg-base-content/40'}"
	></span>
</button>
