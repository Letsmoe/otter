<script lang="ts">
	// Asks for an own TMDB key: once on a fresh install (the built-in key can be
	// kept) and whenever TMDB rejects the key in use.
	import { invalidateAll } from '$app/navigation';
	import ArrowSquareOutIcon from 'phosphor-svelte/lib/ArrowSquareOutIcon';
	import KeyIcon from 'phosphor-svelte/lib/KeyIcon';
	import {
		TMDB_API_SETTINGS_URL,
		TMDB_SIGNUP_URL,
		checkTmdbKey,
		dismissTmdbKeyPrompt,
		fetchTmdbKeyState,
		promptFor,
		saveTmdbKey,
		type TmdbKeyPrompt
	} from '@neoworks-dev/otter-app/tmdbKey';

	let prompt = $state<TmdbKeyPrompt>(null);
	let key = $state('');
	let saving = $state(false);
	let error = $state('');

	// Checked on load and whenever the window regains focus, so a key that
	// stops working mid-session is noticed.
	function refresh() {
		if (prompt) return;
		fetchTmdbKeyState().then((state) => (prompt = promptFor(state)));
	}

	$effect(() => {
		refresh();
	});

	function heading(): string {
		if (prompt === 'broken') return 'TMDB key needed';
		return 'Use your own TMDB key?';
	}

	function intro(): string {
		if (prompt === 'broken') {
			return 'Otter gets all movie and series info from TMDB, and TMDB stopped accepting the key in use. Enter your own free key to keep things working.';
		}
		return 'Otter gets all movie and series info from TMDB. It comes with a shared key that works out of the box, but your own free key keeps working even if the shared one gets blocked.';
	}

	async function save() {
		const trimmed = key.trim();
		if (!trimmed || saving) return;
		saving = true;
		error = '';
		try {
			if (!(await checkTmdbKey(trimmed))) {
				error = 'TMDB does not accept this key. Copy the "API Key", not the Read Access Token.';
				return;
			}
			await saveTmdbKey(trimmed);
			prompt = null;
			await invalidateAll();
		} catch {
			error = 'Could not reach TMDB to check the key. Try again in a moment.';
		} finally {
			saving = false;
		}
	}

	async function keepBuiltInKey() {
		prompt = null;
		await dismissTmdbKeyPrompt();
	}
</script>

<svelte:window onfocus={refresh} />

{#if prompt}
	<div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-6">
		<div
			class="border-base-300 bg-base-200 flex w-full max-w-lg flex-col gap-5 rounded-xl border p-6 shadow-2xl"
			role="dialog"
			aria-modal="true"
			aria-labelledby="tmdb-key-heading"
		>
			<div class="flex items-start gap-3">
				<span class="bg-primary/15 text-primary flex h-10 w-10 shrink-0 items-center justify-center rounded-full">
					<KeyIcon size={20} weight="fill" />
				</span>
				<div class="flex flex-col gap-1">
					<h2 id="tmdb-key-heading" class="text-lg font-semibold">{heading()}</h2>
					<p class="text-base-content/65 text-sm leading-relaxed">{intro()}</p>
				</div>
			</div>

			<ol class="text-base-content/80 flex list-decimal flex-col gap-2 pl-5 text-sm leading-relaxed">
				<li>
					Create a free account on
					<a href={TMDB_SIGNUP_URL} target="_blank" rel="noreferrer" class="text-primary inline-flex items-center gap-0.5 underline">
						themoviedb.org <ArrowSquareOutIcon size={12} />
					</a>
					and confirm your email.
				</li>
				<li>
					Open
					<a href={TMDB_API_SETTINGS_URL} target="_blank" rel="noreferrer" class="text-primary inline-flex items-center gap-0.5 underline">
						Settings → API <ArrowSquareOutIcon size={12} />
					</a>
					and request an API key. Choose "Developer" and describe it as a personal media app.
				</li>
				<li>Copy the <strong>API Key</strong> (32 characters, not the long Read Access Token) and paste it here.</li>
			</ol>

			<form
				class="flex flex-col gap-2"
				onsubmit={(event) => {
					event.preventDefault();
					save();
				}}
			>
				<input
					type="text"
					class="border-base-300 bg-base-100 focus:border-base-content/30 w-full rounded-md border px-3 py-2 font-mono text-sm transition-colors outline-none"
					placeholder="xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
					autocomplete="off"
					spellcheck="false"
					bind:value={key}
				/>
				{#if error}
					<p class="text-error text-xs">{error}</p>
				{/if}
				<div class="flex items-center justify-end gap-2 pt-2">
					{#if prompt === 'welcome'}
						<button
							type="button"
							class="text-base-content/70 hover:bg-base-300 hover:text-base-content rounded-md px-4 py-2 text-sm font-medium transition-colors"
							onclick={keepBuiltInKey}
						>
							Use the built-in key
						</button>
					{/if}
					<button
						type="submit"
						class="flex items-center gap-2 rounded-md bg-white px-4 py-2 text-sm font-semibold text-black transition-opacity hover:opacity-90 disabled:opacity-50"
						disabled={saving || !key.trim()}
					>
						{#if saving}<span class="loading loading-spinner loading-xs"></span>{/if}
						Save key
					</button>
				</div>
			</form>
		</div>
	</div>
{/if}
