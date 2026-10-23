<script lang="ts">
	// "Choose Your Profile" over a trending title's poster: pick, unlock, add and
	// manage profiles before the app opens. Picking one sends its avatar flying
	// into the bottom bar (ProfileFlight).
	import { onMount } from 'svelte';
	import { fade } from 'svelte/transition';
	import { fetchSpotlight, type Spotlight } from '@neoworks-dev/otter-app/discovery';
	import { profileFlight } from '$lib/stores/profileFlight.svelte';
	import CheckIcon from 'phosphor-svelte/lib/CheckIcon';
	import { otter } from '@neoworks-dev/otter-app/api';
	import { activeProfile, avatarFor, type Profile } from '@neoworks-dev/otter-app/profile';
	import PencilSimpleIcon from 'phosphor-svelte/lib/PencilSimpleIcon';
	import PlusIcon from 'phosphor-svelte/lib/PlusIcon';
	import LockSimpleIcon from 'phosphor-svelte/lib/LockSimpleIcon';
	import PinPad from './PinPad.svelte';
	import ProfileEditor from './ProfileEditor.svelte';

	type Screen =
		| { kind: 'pick' }
		| { kind: 'unlock'; profile: Profile; then: 'open' | 'edit' }
		| { kind: 'edit'; profile: Profile }
		| { kind: 'add' };

	let profiles = $state<Profile[]>([]);
	let loading = $state(true);
	let loadFailed = $state(false);
	let managing = $state(false);
	let screen = $state<Screen>({ kind: 'pick' });
	let spotlight = $state<Spotlight | null>(null);
	let posterLoaded = $state(false);

	// Unlocked profiles take off from where the PIN screen showed the avatar.
	const UNLOCK_AVATAR_SIZE = 96;
	const UNLOCK_AVATAR_TOP_FRACTION = 0.3;

	async function loadProfiles() {
		loading = true;
		loadFailed = false;
		try {
			const data = await otter.query({
				profiles: { id: true, name: true, avatarUrl: true, hasPin: true, isChild: true, languages: true }
			});
			profiles = data.profiles;
		} catch {
			loadFailed = true;
		} finally {
			loading = false;
		}
	}

	onMount(() => {
		loadProfiles();
		fetchSpotlight().then((loaded) => (spotlight = loaded));
	});

	function launch(profile: Profile, from: DOMRect) {
		profileFlight.start(avatarFor(profile), from);
		activeProfile.login(profile);
	}

	function unlockAvatarRect(): DOMRect {
		const left = (window.innerWidth - UNLOCK_AVATAR_SIZE) / 2;
		const top = window.innerHeight * UNLOCK_AVATAR_TOP_FRACTION;
		return new DOMRect(left, top, UNLOCK_AVATAR_SIZE, UNLOCK_AVATAR_SIZE);
	}

	function avatarRect(event: MouseEvent): DOMRect {
		const tile = event.currentTarget;
		if (!(tile instanceof HTMLElement)) return unlockAvatarRect();
		const image = tile.querySelector('img');
		if (!image) return unlockAvatarRect();
		return image.getBoundingClientRect();
	}

	function backToPicker() {
		screen = { kind: 'pick' };
		loadProfiles();
	}

	function openProfile(profile: Profile, event: MouseEvent) {
		if (managing) {
			editProfile(profile);
			return;
		}
		if (profile.hasPin) {
			screen = { kind: 'unlock', profile, then: 'open' };
			return;
		}
		launch(profile, avatarRect(event));
	}

	// Locked profiles need their PIN before they can be changed.
	function editProfile(profile: Profile) {
		if (profile.hasPin) {
			screen = { kind: 'unlock', profile, then: 'edit' };
			return;
		}
		screen = { kind: 'edit', profile };
	}

	function waitMessage(waitSeconds: number): string {
		if (waitSeconds > 0) return `Too many attempts. Try again in ${waitSeconds}s.`;
		return 'Incorrect PIN. Try again.';
	}

	async function verifyPin(profile: Profile, then: 'open' | 'edit', pin: string) {
		const data = await otter.mutation({
			verifyPin: { __args: { profileId: profile.id, pin }, success: true, waitSeconds: true }
		});
		if (!data.verifyPin.success) return waitMessage(data.verifyPin.waitSeconds);
		if (then === 'edit') screen = { kind: 'edit', profile };
		else launch(profile, unlockAvatarRect());
		return null;
	}

	// The Add tile spins its plus and pops before the editor slides up.
	const ADD_PRESS_MS = 260;
	let addPressed = $state(false);

	function openAdd() {
		if (addPressed) return;
		addPressed = true;
		setTimeout(() => {
			addPressed = false;
			screen = { kind: 'add' };
		}, ADD_PRESS_MS);
	}

	function editLabel(): string {
		if (managing) return 'Done';
		return 'Edit';
	}

	function sheetTitle(): string {
		if (managing) return 'Manage Profiles';
		return 'Choose Your Profile';
	}
</script>

<div class="fixed inset-0 z-50 overflow-hidden bg-black" out:fade|global={{ duration: 800 }}>
	<!-- The spotlight stays behind every screen; editors blur it. -->
	<div class="gate-backdrop absolute inset-0" class:gate-backdrop-blurred={screen.kind !== 'pick'}>
		{#if spotlight}
			<img
				src={spotlight.posterUrl}
				alt=""
				class="spotlight-poster absolute inset-x-0 top-0 h-[78%] w-full object-cover"
				class:opacity-0={!posterLoaded}
				onload={() => (posterLoaded = true)}
			/>
		{/if}
		<div
			class="absolute inset-0 bg-gradient-to-b from-black/50 via-transparent via-30% to-black"
		></div>
	</div>

	{#if screen.kind === 'pick'}
		<div class="absolute inset-0" in:fade={{ duration: 260 }}>
			<span
				class="absolute inset-x-0 top-[calc(var(--safe-top)+14px)] text-center text-lg font-black tracking-[0.18em]"
			>
				OTTER
			</span>

			<div class="absolute inset-x-0 bottom-0 flex max-h-full flex-col items-center">
				{#if spotlight && posterLoaded}
					<div
						class="flex flex-col items-center gap-3 px-8 pb-6 text-center"
						in:fade={{ duration: 500 }}
					>
						{#if spotlight.logoUrl}
							<img
								src={spotlight.logoUrl}
								alt={spotlight.title}
								class="max-h-24 max-w-[70%] object-contain drop-shadow-lg"
							/>
						{:else}
							<p class="text-3xl font-black tracking-tight drop-shadow-lg">{spotlight.title}</p>
						{/if}
						{#if spotlight.tagline}
							<p class="line-clamp-2 text-sm font-semibold drop-shadow">{spotlight.tagline}</p>
						{/if}
					</div>
				{/if}

				<section
					class="gate-sheet glass-bar flex min-h-0 w-full flex-col items-center gap-5 px-8 pt-6 pb-[calc(var(--safe-bottom)+28px)]"
				>
					<h1 class="text-base text-white/70">{sheetTitle()}</h1>

					{#if loading}
						<span class="loading loading-spinner my-8"></span>
					{:else if loadFailed}
						<div class="flex flex-col items-center gap-3 py-4 text-center">
							<p class="opacity-70">Can't reach the Otter server.</p>
							<button
								class="glass-bar rounded-full px-5 py-2 text-sm font-semibold"
								onclick={loadProfiles}
							>
								Try again
							</button>
						</div>
					{:else}
						<div
							class="no-scrollbar grid max-h-[46vh] grid-cols-2 gap-x-10 gap-y-5 overflow-y-auto"
						>
							{#each profiles as profile (profile.id)}
								<button
									class="flex flex-col items-center gap-2"
									onclick={(event) => openProfile(profile, event)}
								>
									<span class="relative block h-20 w-20 overflow-hidden rounded-2xl">
										<img src={avatarFor(profile)} alt="" class="h-full w-full" />
										{#if managing}
											<span class="absolute inset-0 flex items-center justify-center bg-black/55">
												<PencilSimpleIcon size={28} />
											</span>
										{/if}
									</span>
									<span class="flex items-center gap-1 text-sm font-semibold">
										{profile.name}
										{#if profile.hasPin}<LockSimpleIcon size={13} class="opacity-60" />{/if}
									</span>
								</button>
							{/each}

							{#if !managing}
								<button
									class="add-tile flex flex-col items-center gap-2"
									class:add-tile-pressed={addPressed}
									onclick={openAdd}
								>
									<span class="glass-bar flex h-20 w-20 items-center justify-center rounded-2xl">
										<PlusIcon size={34} weight="light" />
									</span>
									<span class="text-sm text-white/80">Add</span>
								</button>
							{/if}
							{#if profiles.length > 0}
								<button
									class="flex flex-col items-center gap-2"
									onclick={() => (managing = !managing)}
								>
									<span class="glass-bar flex h-20 w-20 items-center justify-center rounded-2xl">
										{#if managing}<CheckIcon size={30} />{:else}<PencilSimpleIcon
												size={30}
												weight="light"
											/>{/if}
									</span>
									<span class="text-sm text-white/80">{editLabel()}</span>
								</button>
							{/if}
						</div>
					{/if}
				</section>
			</div>
		</div>
	{:else}
		<div
			class="editor-panel glass-bar absolute inset-x-0 top-[calc(var(--safe-top)+12px)] bottom-0 overflow-hidden rounded-t-[2rem]"
		>
			{#if screen.kind === 'unlock'}
				{@const unlocking = screen}
				<PinPad
					title="Profile Lock"
					message="Enter your PIN to access this profile."
					avatarUrl={avatarFor(unlocking.profile)}
					onback={() => (screen = { kind: 'pick' })}
					oncomplete={(pin) => verifyPin(unlocking.profile, unlocking.then, pin)}
				/>
			{:else if screen.kind === 'edit'}
				<ProfileEditor profile={screen.profile} onback={backToPicker} onsaved={backToPicker} />
			{:else if screen.kind === 'add'}
				<ProfileEditor onback={backToPicker} onsaved={backToPicker} />
			{/if}
		</div>
	{/if}
</div>

<style>
	/* The sheet's top edge bulges upwards like Netflix's. */
	.gate-sheet {
		border-radius: 50% 50% 0 0 / 2.75rem 2.75rem 0 0;
	}

	.gate-backdrop {
		transition:
			filter 500ms ease-out,
			scale 500ms ease-out;
	}

	.gate-backdrop-blurred {
		filter: blur(18px) brightness(0.55);
		scale: 1.06;
	}

	.editor-panel {
		animation: panel-up 440ms cubic-bezier(0.22, 1, 0.36, 1);
	}

	@keyframes panel-up {
		from {
			translate: 0 100%;
		}
	}

	/* Plus spins a quarter turn while the tile pops. */
	.add-tile :global(svg) {
		transition: rotate 260ms cubic-bezier(0.34, 1.56, 0.64, 1);
	}

	.add-tile-pressed :global(svg) {
		rotate: 90deg;
	}

	.add-tile-pressed > span:first-child {
		animation: add-pop 260ms cubic-bezier(0.34, 1.56, 0.64, 1);
	}

	@keyframes add-pop {
		50% {
			scale: 1.12;
		}
	}

	.spotlight-poster {
		transition: opacity 700ms ease-out;
		animation: spotlight-drift 18s ease-in-out infinite alternate;
	}

	/* A slow push-in keeps the poster alive. */
	@keyframes spotlight-drift {
		from {
			scale: 1;
		}
		to {
			scale: 1.08;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.editor-panel,
		.add-tile-pressed > span:first-child,
		.spotlight-poster {
			animation: none;
		}
	}
</style>
