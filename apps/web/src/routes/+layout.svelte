<script lang="ts">
	import './layout.css';
	import '$lib/tv.css';
	import Sidebar from '$lib/components/Sidebar.svelte';
	import NotificationManager from '$lib/components/NotificationManager.svelte';
	import ProfilePicker from '$lib/components/ProfilePicker.svelte';
	import TmdbKeyDialog from '$lib/components/TmdbKeyDialog.svelte';
	import SyncOnOpen from '$lib/components/SyncOnOpen.svelte';
	import { goto } from '$app/navigation';
	import { activeProfile } from '@letsmoe/otter-app/profile';
	import { downloads } from '$lib/stores/downloads.svelte';

	let { children } = $props();

	$effect(() => {
		let profileId: string | null = null;
		if ($activeProfile) profileId = $activeProfile.id;
		downloads.follow(profileId);
	});

	// Opening the sidebar via the left edge is handled by the FocusList that
	// currently owns keyboard navigation (JS-tracked position); the layout only
	// handles global shortcuts.
	function onWindowKeydown(e: KeyboardEvent) {
		if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
			e.preventDefault();
			goto('/search');
		}
	}
</script>

<svelte:window onkeydown={onWindowKeydown} />

<TmdbKeyDialog />
<SyncOnOpen />

{#if $activeProfile === null}
	<ProfilePicker />
{:else}
	<div class="flex h-screen overflow-hidden bg-base-100">
		<Sidebar />
		<NotificationManager />
		<main class="no-scrollbar min-w-0 flex-1 overflow-x-hidden overflow-y-auto p-6">
			{@render children()}
		</main>
	</div>
{/if}
