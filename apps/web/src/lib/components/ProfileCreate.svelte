<script lang="ts">
	import { otter } from '@neoworks-dev/otter-app/api';
	import type { Profile } from '@neoworks-dev/otter-app/profile';
	import { PRESET_AVATARS, avatarImage } from '@neoworks-dev/otter-app/avatars';
	import { DEFAULT_LANGUAGES, LANGUAGE_CHOICES } from '@neoworks-dev/otter-app/languages';

	interface Props {
		oncreated: (profile: Profile) => void;
		oncancel: () => void;
	}

	let { oncreated, oncancel }: Props = $props();

	let name = $state('');
	let avatarUrl = $state('');
	let pin = $state('');
	let pinConfirm = $state('');
	let isChild = $state(false);
	// Audio languages, most preferred first.
	let languages = $state<string[]>([...DEFAULT_LANGUAGES]);
	let error = $state('');
	let loading = $state(false);

	let selectedAvatar = $state(PRESET_AVATARS[0]);

	async function submit() {
		error = '';
		if (!name.trim()) { error = 'Name required'; return; }
		if (pin.length !== 4 || !/^\d{4}$/.test(pin)) { error = 'PIN must be exactly 4 digits'; return; }
		if (pin !== pinConfirm) { error = 'PINs do not match'; return; }

		loading = true;
		try {
			const data = await otter.mutation({
				createProfile: {
					__args: { name: name.trim(), avatarUrl: selectedAvatar, pin, isChild, languages },
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
			oncreated(data.createProfile);
		} catch (e: unknown) {
			error = e instanceof Error ? e.message : 'Failed to create profile';
		} finally {
			loading = false;
		}
	}

	// Picking adds a language at the end of the order; picking again removes it, but one always stays.
	function toggleLanguage(code: string) {
		if (!languages.includes(code)) {
			languages = [...languages, code];
			return;
		}
		if (languages.length === 1) return;
		languages = languages.filter((language) => language !== code);
	}

	function onPinInput(e: Event, target: 'pin' | 'confirm') {
		const input = e.currentTarget as HTMLInputElement;
		const val = input.value.replace(/\D/g, '').slice(0, 4);
		if (target === 'pin') pin = val;
		else pinConfirm = val;
		input.value = val;
	}
</script>

<div class="flex flex-col gap-6">
	<h2 class="text-xl font-bold">Create Profile</h2>

	<div>
		<label class="label mb-1 text-sm font-medium">Avatar</label>
		<div class="grid grid-cols-4 gap-2">
			{#each PRESET_AVATARS as url}
				<button
					type="button"
					onclick={() => { selectedAvatar = url; }}
					class="rounded-xl border-2 p-1 transition-all {selectedAvatar === url
						? 'border-primary shadow-[0_0_12px_rgba(139,92,246,0.4)]'
						: 'border-transparent hover:border-base-content/20'}"
				>
					<img src={avatarImage(url, '')} alt="avatar" class="h-14 w-14 rounded-lg" />
				</button>
			{/each}
		</div>
	</div>

	<div class="form-control">
		<label class="label" for="profile-name"><span class="label-text">Name</span></label>
		<input
			id="profile-name"
			type="text"
			class="input input-bordered w-full"
			placeholder="e.g. Moritz"
			bind:value={name}
			maxlength={32}
		/>
	</div>

	<div class="grid grid-cols-2 gap-4">
		<div class="form-control">
			<label class="label" for="pin"><span class="label-text">4-digit PIN</span></label>
			<input
				id="pin"
				type="password"
				inputmode="numeric"
				class="input input-bordered w-full tracking-[0.5em]"
				placeholder="••••"
				value={pin}
				oninput={(e) => onPinInput(e, 'pin')}
				maxlength={4}
			/>
		</div>
		<div class="form-control">
			<label class="label" for="pin-confirm"><span class="label-text">Confirm PIN</span></label>
			<input
				id="pin-confirm"
				type="password"
				inputmode="numeric"
				class="input input-bordered w-full tracking-[0.5em]"
				placeholder="••••"
				value={pinConfirm}
				oninput={(e) => onPinInput(e, 'confirm')}
				maxlength={4}
			/>
		</div>
	</div>

	<label class="flex cursor-pointer items-center gap-3">
		<input type="checkbox" class="toggle toggle-sm" bind:checked={isChild} />
		<span class="text-sm">Child profile <span class="text-base-content/40">(restricted access)</span></span>
	</label>

	<div class="flex flex-col gap-2">
		<span class="text-sm">Languages <span class="text-base-content/40">(sources in these languages are tried first, in this order)</span></span>
		<div class="flex flex-wrap gap-2">
			{#each LANGUAGE_CHOICES as choice (choice.code)}
				{@const position = languages.indexOf(choice.code)}
				<button
					type="button"
					class="rounded-full px-3 py-1 text-sm transition-colors {position >= 0 ? 'bg-base-content/20' : 'bg-base-content/5 text-base-content/60'}"
					onclick={() => toggleLanguage(choice.code)}
				>
					{#if position >= 0}{position + 1}. {/if}{choice.name}
				</button>
			{/each}
		</div>
	</div>

	{#if error}
		<p class="text-error text-sm">{error}</p>
	{/if}

	<div class="flex gap-3">
		<button type="button" class="btn btn-ghost flex-1" onclick={oncancel} disabled={loading}>
			Cancel
		</button>
		<button type="button" class="btn btn-primary flex-1" onclick={submit} disabled={loading}>
			{loading ? 'Creating…' : 'Create'}
		</button>
	</div>
</div>
