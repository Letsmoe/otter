<script lang="ts">
	import { onMount } from 'svelte';
	import ShieldCheckIcon from 'phosphor-svelte/lib/ShieldCheckIcon';
	import ShieldWarningIcon from 'phosphor-svelte/lib/ShieldWarningIcon';
	import { otter } from '@neoworks-dev/otter-app/api';

	const POLL_INTERVAL_MS = 10000;

	let connected = $state(false);
	let required = $state(false);

	async function refresh() {
		try {
			const data = await otter.query({ status: { vpnConnected: true, requireVpn: true } });
			connected = data.status.vpnConnected;
			required = data.status.requireVpn;
		} catch {
			connected = false;
		}
	}

	onMount(() => {
		refresh();
		const timer = setInterval(refresh, POLL_INTERVAL_MS);
		return () => clearInterval(timer);
	});

	let label = $derived.by(() => {
		if (connected) return 'VPN connected';
		if (required) return 'VPN disconnected — streaming is blocked';
		return 'VPN disconnected';
	});
</script>

<div class="tooltip tooltip-right" data-tip={label}>
	{#if connected}
		<ShieldCheckIcon size={20} weight="fill" class="text-success" />
	{:else}
		<ShieldWarningIcon size={20} weight="fill" class="text-error" />
	{/if}
</div>
