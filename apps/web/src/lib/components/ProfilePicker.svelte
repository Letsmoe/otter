<script lang="ts">
	import { onMount } from 'svelte';
	import { otter } from '@neoworks-dev/otter-app/api';
	import { activeProfile, avatarFor, type Profile } from '@neoworks-dev/otter-app/profile';
	import ProfileCreate from './ProfileCreate.svelte';
	import PlusIcon from 'phosphor-svelte/lib/PlusIcon';

	let profiles = $state<Profile[]>([]);
	let loading = $state(true);
	let creating = $state(false);

	// PIN entry state for the profile being unlocked.
	let pinFor = $state<Profile | null>(null);
	let pin = $state('');
	let pinError = $state('');
	let verifying = $state(false);

	async function loadProfiles() {
		loading = true;
		try {
			const data = await otter.query({
				profiles: {
					id: true,
					name: true,
					avatarUrl: true,
					hasPin: true,
					isChild: true,
					languages: true,
					subtitlesEnabled: true,
					subtitleLanguage: true,
					subtitleSize: true,
					voiceBoost: true
				}
			});
			profiles = data.profiles;
		} catch {
			profiles = [];
		} finally {
			loading = false;
		}
	}

	onMount(loadProfiles);

	// Profiles without a PIN open directly.
	function select(p: Profile) {
		if (!p.hasPin) {
			activeProfile.login(p);
			return;
		}
		pinFor = p;
		pin = '';
		pinError = '';
	}

	function onPinInput(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		pin = input.value.replace(/\D/g, '').slice(0, 4);
	}

	function pinErrorMessage(waitSeconds: number): string {
		if (waitSeconds > 0) return `Too many attempts. Wait ${waitSeconds}s.`;
		return 'Incorrect PIN';
	}

	async function verify() {
		if (!pinFor || pin.length !== 4) return;
		verifying = true;
		pinError = '';
		try {
			const data = await otter.mutation({
				verifyPin: { __args: { profileId: pinFor.id, pin }, success: true, waitSeconds: true }
			});
			if (data.verifyPin.success) {
				activeProfile.login(pinFor);
				return;
			}
			pinError = pinErrorMessage(data.verifyPin.waitSeconds);
		} catch (e) {
			pinError = e instanceof Error ? e.message : 'Verification failed';
		} finally {
			verifying = false;
		}
	}
</script>

<div class="bg-base-100 fixed inset-0 z-50 flex flex-col items-center justify-center gap-10 p-6">
	{#if creating}
		<ProfileCreate
			oncreated={(p) => {
				creating = false;
				loadProfiles();
				activeProfile.login(p);
			}}
			oncancel={() => (creating = false)}
		/>
	{:else if pinFor}
		<div class="flex flex-col items-center gap-5">
			<img src={avatarFor(pinFor)} alt={pinFor.name} class="h-24 w-24 rounded-2xl" />
			<h2 class="text-xl font-semibold">{pinFor.name}</h2>
			<p class="text-base-content/50 text-sm">Enter your 4-digit PIN</p>
			<input
				type="password"
				inputmode="numeric"
				class="input input-bordered w-40 text-center text-2xl tracking-[0.5em]"
				value={pin}
				oninput={onPinInput}
				onkeydown={(e) => e.key === 'Enter' && verify()}
			/>
			{#if pinError}<p class="text-error text-sm">{pinError}</p>{/if}
			<div class="flex gap-2">
				<button class="btn btn-ghost" onclick={() => (pinFor = null)}>Back</button>
				<button class="btn btn-primary" onclick={verify} disabled={pin.length !== 4 || verifying}>
					{#if verifying}<span class="loading loading-spinner loading-sm"></span>{/if}
					Unlock
				</button>
			</div>
		</div>
	{:else}
		<h1 class="text-3xl font-bold">Who's watching?</h1>
		{#if loading}
			<span class="loading loading-spinner loading-lg text-primary"></span>
		{:else}
			<div class="flex flex-wrap items-start justify-center gap-6">
				{#each profiles as p (p.id)}
					<button
						class="group flex w-28 flex-col items-center gap-2"
						onclick={() => select(p)}
					>
						<img
							src={avatarFor(p)}
							alt={p.name}
							class="ring-base-300 group-hover:ring-primary h-28 w-28 rounded-2xl ring-2 transition-all"
						/>
						<span class="text-sm font-medium">{p.name}</span>
						{#if p.isChild}<span class="badge badge-sm badge-ghost">Kids</span>{/if}
					</button>
				{/each}

				<button
					class="group flex w-28 flex-col items-center gap-2"
					onclick={() => (creating = true)}
				>
					<div
						class="border-base-300 group-hover:border-primary flex h-28 w-28 items-center justify-center rounded-2xl border-2 border-dashed transition-all"
					>
						<PlusIcon size={36} class="opacity-40" />
					</div>
					<span class="text-base-content/50 text-sm">Add profile</span>
				</button>
			</div>
		{/if}
	{/if}
</div>
