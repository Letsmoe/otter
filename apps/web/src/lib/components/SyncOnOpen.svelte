<script lang="ts">
	// Asks the backend to sync with Neoworks when the app opens and whenever the
	// tab comes back into view, so changes from other devices show up.
	import { onMount } from 'svelte';
	import { otter } from '@letsmoe/otter-app/api';

	function requestSync() {
		// A failed request just waits for the backend's own next sync.
		otter.mutation({ syncNeoworksSoon: true }).catch(() => {});
	}

	function syncWhenVisible() {
		if (document.visibilityState === 'visible') requestSync();
	}

	onMount(() => {
		requestSync();
		document.addEventListener('visibilitychange', syncWhenVisible);
		return () => document.removeEventListener('visibilitychange', syncWhenVisible);
	});
</script>
