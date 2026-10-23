// VPN state from the backend, polled while the app is open.
import { readable } from 'svelte/store';
import { otter } from '@neoworks-dev/otter-app/api';

const POLL_INTERVAL_MS = 30_000;

export interface VpnStatus {
	connected: boolean;
	required: boolean;
	/** False until the first answer, and while the backend is unreachable. */
	known: boolean;
}

const UNKNOWN: VpnStatus = { connected: false, required: false, known: false };

async function fetchVpnStatus(): Promise<VpnStatus> {
	try {
		const data = await otter.query({ status: { vpnConnected: true, requireVpn: true } });
		return { connected: data.status.vpnConnected, required: data.status.requireVpn, known: true };
	} catch {
		return UNKNOWN;
	}
}

export const vpnStatus = readable<VpnStatus>(UNKNOWN, (set) => {
	const refresh = () => fetchVpnStatus().then(set);
	refresh();
	const timer = setInterval(refresh, POLL_INTERVAL_MS);
	return () => clearInterval(timer);
});
