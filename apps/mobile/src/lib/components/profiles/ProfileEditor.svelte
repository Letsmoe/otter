<script lang="ts">
	// Add or edit a profile: avatar, name, kids flag and an optional PIN lock.
	// Avatar picking and PIN setup open as their own full-screen steps.
	import { untrack } from 'svelte';
	import { otter } from '@neoworks-dev/otter-app/api';
	import { PRESET_AVATARS, avatarImage } from '@neoworks-dev/otter-app/avatars';
	import type { Profile } from '@neoworks-dev/otter-app/profile';
	import PencilSimpleIcon from 'phosphor-svelte/lib/PencilSimpleIcon';
	import LockSimpleIcon from 'phosphor-svelte/lib/LockSimpleIcon';
	import SmileyIcon from 'phosphor-svelte/lib/SmileyIcon';
	import TrashIcon from 'phosphor-svelte/lib/TrashIcon';
	import CaretRightIcon from 'phosphor-svelte/lib/CaretRightIcon';
	import ScreenHeader from '../ScreenHeader.svelte';
	import Toggle from '../Toggle.svelte';
	import AvatarChooser from './AvatarChooser.svelte';
	import PinPad from './PinPad.svelte';

	const PROFILE_SELECT = {
		id: true,
		name: true,
		avatarUrl: true,
		hasPin: true,
		isChild: true,
		languages: true
	} as const;

	interface Props {
		/** The profile to edit; omitted when adding a new one. */
		profile?: Profile;
		onback: () => void;
		onsaved: () => void;
	}

	let { profile, onback, onsaved }: Props = $props();

	// The form edits a copy; the gate mounts a new editor per profile.
	const initialProfile = untrack(() => profile);
	const isNew = initialProfile === undefined;

	let name = $state(initialProfile?.name || '');
	let avatarId = $state(initialAvatar());
	let isChild = $state(initialProfile?.isChild === true);
	let locked = $state(initialProfile?.hasPin === true);
	// A freshly entered PIN; null keeps the stored one.
	let newPin = $state<string | null>(null);

	let step = $state<'form' | 'avatar' | 'pin-create' | 'pin-confirm'>('form');
	let pendingPin = '';
	let saving = $state(false);
	let error = $state('');
	let confirmingDelete = $state(false);

	function screenTitle(): string {
		if (isNew) return 'Add Profile';
		return 'Edit Profile';
	}

	function saveLabel(): string {
		if (isNew) return 'Create Profile';
		return 'Save';
	}

	function initialAvatar(): string {
		if (initialProfile?.avatarUrl) return initialProfile.avatarUrl;
		return PRESET_AVATARS[Math.floor(Math.random() * PRESET_AVATARS.length)];
	}

	function onLockChange(checked: boolean) {
		if (!checked) {
			locked = false;
			newPin = null;
			return;
		}
		step = 'pin-create';
	}

	async function onPinCreated(pin: string): Promise<string | null> {
		pendingPin = pin;
		step = 'pin-confirm';
		return null;
	}

	async function onPinConfirmed(pin: string): Promise<string | null> {
		if (pin !== pendingPin) {
			step = 'pin-create';
			return 'PINs did not match. Try again.';
		}
		newPin = pin;
		locked = true;
		step = 'form';
		return null;
	}

	// Empty string removes the PIN; undefined leaves it untouched.
	function pinChange(): string | undefined {
		if (newPin !== null) return newPin;
		if (!locked && profile?.hasPin) return '';
		return undefined;
	}

	async function createProfile() {
		await otter.mutation({
			createProfile: {
				__args: { name: name.trim(), avatarUrl: avatarId, pin: newPin, isChild },
				...PROFILE_SELECT
			}
		});
	}

	async function updateProfile(existing: Profile) {
		await otter.mutation({
			updateProfile: {
				__args: {
					id: existing.id,
					name: name.trim(),
					avatarUrl: avatarId,
					isChild,
					pin: pinChange()
				},
				...PROFILE_SELECT
			}
		});
	}

	async function save() {
		if (!name.trim()) {
			error = 'Enter a name.';
			return;
		}
		saving = true;
		error = '';
		try {
			if (profile) await updateProfile(profile);
			else await createProfile();
			onsaved();
		} catch (failure) {
			error = saveErrorMessage(failure);
		} finally {
			saving = false;
		}
	}

	function saveErrorMessage(failure: unknown): string {
		if (failure instanceof Error) return failure.message;
		return 'Could not save the profile.';
	}

	async function deleteProfile() {
		if (!profile) return;
		await otter.mutation({ deleteProfile: { __args: { id: profile.id } } });
		onsaved();
	}
</script>

{#if step === 'avatar'}
	<AvatarChooser
		selected={avatarId}
		onback={() => (step = 'form')}
		onselect={(chosen) => {
			avatarId = chosen;
			step = 'form';
		}}
	/>
{:else if step === 'pin-create'}
	<PinPad
		title="Profile Lock"
		message="Create a 4-digit PIN for this profile."
		onback={() => (step = 'form')}
		oncomplete={onPinCreated}
	/>
{:else if step === 'pin-confirm'}
	<PinPad
		title="Profile Lock"
		message="Enter the PIN again to confirm."
		onback={() => (step = 'pin-create')}
		oncomplete={onPinConfirmed}
	/>
{:else}
	<div class="flex h-full flex-col">
		<ScreenHeader title={screenTitle()} {onback} />

		<div
			class="flex flex-1 flex-col gap-5 overflow-y-auto px-5 pt-2 pb-[calc(var(--safe-bottom)+20px)]"
		>
			<button
				class="avatar-button relative mx-auto h-28 w-28"
				aria-label="Change icon"
				onclick={() => (step = 'avatar')}
			>
				<img
					src={avatarImage(avatarId, name)}
					alt=""
					class="h-full w-full rounded-2xl shadow-2xl"
				/>
				<span
					class="glass-bar absolute -right-2 -bottom-2 flex h-9 w-9 items-center justify-center rounded-full"
				>
					<PencilSimpleIcon size={16} weight="bold" />
				</span>
			</button>

			<input
				class="mx-auto h-12 w-full max-w-xs rounded-full bg-white/10 px-5 text-center text-lg font-semibold outline-none placeholder:text-white/35 focus:bg-white/15"
				placeholder="Name"
				maxlength={32}
				bind:value={name}
			/>

			{#if error}
				<p class="text-error text-center text-sm">{error}</p>
			{/if}

			<div class="flex flex-col divide-y divide-white/10 rounded-3xl bg-white/6 px-4">
				<label class="flex items-center gap-4 py-3.5">
					<SmileyIcon size={24} class="shrink-0 opacity-70" />
					<span class="flex flex-1 flex-col">
						<span class="text-sm font-semibold">Kids Profile</span>
						<span class="text-xs opacity-55"
							>Marks this as a child's profile. Titles aren't filtered yet.</span
						>
					</span>
					<Toggle
						checked={isChild}
						ariaLabel="Kids Profile"
						onchange={(value) => (isChild = value)}
					/>
				</label>
				<label class="flex items-center gap-4 py-3.5">
					<LockSimpleIcon size={24} class="shrink-0 opacity-70" />
					<span class="flex flex-1 flex-col">
						<span class="text-sm font-semibold">Profile Lock</span>
						<span class="text-xs opacity-55">Require a 4-digit PIN to open this profile.</span>
					</span>
					<Toggle checked={locked} ariaLabel="Profile Lock" onchange={onLockChange} />
				</label>
				{#if locked}
					<button
						class="flex items-center gap-4 py-3.5 text-left"
						onclick={() => (step = 'pin-create')}
					>
						<span class="flex-1 pl-10 text-sm font-semibold">Change PIN</span>
						<CaretRightIcon size={18} class="opacity-60" />
					</button>
				{/if}
			</div>

			<button
				class="flex h-12 items-center justify-center gap-2 rounded-full bg-white text-sm font-bold text-black disabled:opacity-40"
				disabled={saving || !name.trim()}
				onclick={save}
			>
				{#if saving}<span class="loading loading-spinner loading-xs"></span>{/if}
				{saveLabel()}
			</button>

			{#if !isNew}
				<div class="mt-auto flex justify-center pt-4">
					{#if confirmingDelete}
						<div class="flex flex-col items-center gap-3">
							<p class="text-sm opacity-70">Delete this profile and its history?</p>
							<div class="flex gap-3">
								<button
									class="rounded-full bg-white/10 px-5 py-2 text-sm font-semibold"
									onclick={() => (confirmingDelete = false)}
								>
									Cancel
								</button>
								<button
									class="bg-error rounded-full px-5 py-2 text-sm font-semibold text-white"
									onclick={deleteProfile}
								>
									Delete
								</button>
							</div>
						</div>
					{:else}
						<button
							class="flex items-center gap-2 text-sm font-semibold opacity-80"
							onclick={() => (confirmingDelete = true)}
						>
							<TrashIcon size={20} />
							Delete Profile
						</button>
					{/if}
				</div>
			{/if}
		</div>
	</div>
{/if}

<style>
	.avatar-button {
		animation: avatar-in 520ms cubic-bezier(0.34, 1.56, 0.64, 1) 120ms both;
	}

	@keyframes avatar-in {
		from {
			scale: 0.6;
			opacity: 0;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.avatar-button {
			animation: none;
		}
	}
</style>
