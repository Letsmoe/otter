<script lang="ts">
	// Start or join a Watch Together room, or, inside one: its code to share,
	// who is there and Leave. Used by the Watch Together sheet and the player.
	import { untrack } from 'svelte';
	import { fly, scale } from 'svelte/transition';
	import { flip } from 'svelte/animate';
	import { backOut, cubicOut } from 'svelte/easing';
	import UsersThreeIcon from 'phosphor-svelte/lib/UsersThreeIcon';
	import ShareNetworkIcon from 'phosphor-svelte/lib/ShareNetworkIcon';
	import CopyIcon from 'phosphor-svelte/lib/CopyIcon';
	import CheckIcon from 'phosphor-svelte/lib/CheckIcon';
	import PlusIcon from 'phosphor-svelte/lib/PlusIcon';
	import ArrowRightIcon from 'phosphor-svelte/lib/ArrowRightIcon';
	import SignOutIcon from 'phosphor-svelte/lib/SignOutIcon';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import {
		inviteText,
		roomCodeFromText,
		type TogetherPeer,
		type TogetherStatus
	} from '@neoworks-dev/otter-app/together';
	import { together } from '$lib/stores/together.svelte';
	import { shareContent } from '$lib/share';
	import PeerAvatar from './PeerAvatar.svelte';
	import CodeInput from './CodeInput.svelte';

	const COPIED_MS = 1600;

	interface Props {
		/** Prefilled from an invite link. */
		initialCode?: string;
	}

	let { initialCode = '' }: Props = $props();

	let codeInput = $state(untrack(() => initialCode));
	let parsedCode = $derived(roomCodeFromText(codeInput));
	let room = $derived(together.room);
	let copied = $state(false);
	let codeCharacters = $derived(Array.from(room?.code || ''));

	async function start() {
		const profile = $activeProfile;
		if (!profile) return;
		await together.host(profile.id);
	}

	async function join() {
		const profile = $activeProfile;
		if (!profile || !parsedCode) return;
		await together.join(profile.id, parsedCode);
	}

	async function invite() {
		if (!room) return;
		await shareContent({ title: 'Watch Together', text: inviteText(room.code) });
	}

	async function copyInvite() {
		if (!room) return;
		await navigator.clipboard?.writeText(inviteText(room.code));
		copied = true;
		setTimeout(() => (copied = false), COPIED_MS);
	}

	function statusText(status: TogetherStatus): string {
		if (status === 'WAITING') return 'Waiting for friends';
		if (status === 'CONNECTING') return 'Connecting to the host';
		if (status === 'CLOSED') return 'The host ended the room';
		if (status === 'FAILED') return 'Could not reach the host';
		return 'Connected';
	}

	function statusTone(status: TogetherStatus): string {
		if (status === 'CONNECTED') return 'bg-green-400';
		if (status === 'CLOSED' || status === 'FAILED') return 'bg-red-400';
		return 'bg-amber-300 status-dot-pulse';
	}

	function peerRole(peer: TogetherPeer): string {
		if (!room) return '';
		if (peer.id === room.self.id) return 'You';
		if (room.peers[0]?.id === peer.id) return 'Host';
		return '';
	}

	function isSearching(status: TogetherStatus | undefined): boolean {
		return status === 'WAITING' || status === 'CONNECTING';
	}
</script>

{#if !room}
	<div class="flex flex-col gap-5" in:fly={{ y: 16, duration: 320, easing: cubicOut }}>
		<div class="flex flex-col items-center gap-3 pt-2 text-center">
			<span class="hero-icon relative flex h-16 w-16 items-center justify-center">
				<span class="hero-ring absolute inset-0 rounded-full"></span>
				<span class="hero-ring hero-ring-late absolute inset-0 rounded-full"></span>
				<span class="glass-bar relative flex h-16 w-16 items-center justify-center rounded-full">
					<UsersThreeIcon size={30} />
				</span>
			</span>
			<h2 class="text-xl font-bold">Watch Together</h2>
			<p class="max-w-xs text-sm text-white/60">
				Play in sync with friends on their own phones, with chat and reactions.
			</p>
		</div>

		<button
			class="flex h-12 items-center justify-center gap-2 rounded-full bg-white text-sm font-bold text-black disabled:opacity-40"
			disabled={together.busy}
			onclick={start}
		>
			<PlusIcon size={18} weight="bold" />
			Start a room
		</button>

		<div class="flex items-center gap-3 text-xs text-white/40">
			<span class="h-px flex-1 bg-white/10"></span>
			or join with a code
			<span class="h-px flex-1 bg-white/10"></span>
		</div>

		<div class="flex flex-col gap-3">
			<CodeInput
				initial={initialCode}
				disabled={together.busy}
				onchange={(code) => (codeInput = code)}
				oncomplete={join}
			/>
			<button
				class="join-button flex h-12 items-center justify-center gap-2 rounded-full text-sm font-bold"
				class:join-ready={parsedCode !== null}
				disabled={!parsedCode || together.busy}
				onclick={join}
			>
				Join room
				<ArrowRightIcon size={18} weight="bold" />
			</button>
		</div>
	</div>
{:else}
	<div class="flex flex-col gap-4" in:fly={{ y: 16, duration: 320, easing: cubicOut }}>
		<div class="flex items-center justify-between">
			<h2 class="text-lg font-bold">Watch Together</h2>
			{#key room.status}
				<span
					class="flex h-7 items-center gap-1.5 rounded-full bg-white/8 px-2.5 text-[11px] font-semibold text-white/80"
					in:scale={{ start: 0.85, duration: 260, easing: backOut }}
				>
					<span class="h-1.5 w-1.5 rounded-full {statusTone(room.status)}"></span>
					{statusText(room.status)}
				</span>
			{/key}
		</div>

		<div class="flex items-center gap-3 rounded-3xl bg-white/6 py-3 pr-3 pl-4">
			<button class="flex min-w-0 flex-1 flex-col items-start" onclick={copyInvite}>
				<span class="text-[10px] font-semibold tracking-[0.2em] text-white/45 uppercase"
					>Room code</span
				>
				<span
					class="flex font-mono text-2xl font-bold tracking-[0.12em]"
					class:code-copied={copied}
				>
					{#each codeCharacters as character, index (index)}
						<span class="code-character" style:animation-delay="{index * 45}ms">{character}</span
						>{#if index === 3}<span class="code-character px-1 text-white/30">-</span>{/if}
					{/each}
				</span>
			</button>
			<button
				class="glass-bar flex h-11 w-11 shrink-0 items-center justify-center rounded-full"
				aria-label="Copy invite"
				onclick={copyInvite}
			>
				{#key copied}
					<span in:scale={{ start: 0.5, duration: 240, easing: backOut }}>
						{#if copied}<CheckIcon size={18} weight="bold" />{:else}<CopyIcon size={18} />{/if}
					</span>
				{/key}
			</button>
			<button
				class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-white text-black"
				aria-label="Invite"
				onclick={invite}
			>
				<ShareNetworkIcon size={18} weight="bold" />
			</button>
		</div>

		<div class="flex flex-col gap-2.5">
			<span class="text-[10px] font-semibold tracking-[0.2em] text-white/45 uppercase">
				Watching · {room.peers.length}
			</span>
			<div class="no-scrollbar -mx-1 flex gap-3 overflow-x-auto px-1">
				{#each room.peers as peer (peer.id)}
					<div
						class="flex w-14 shrink-0 flex-col items-center gap-1"
						animate:flip={{ duration: 300 }}
						in:scale={{ start: 0.6, duration: 380, easing: backOut }}
						out:scale={{ start: 0.6, duration: 200 }}
					>
						<PeerAvatar {peer} size={48} buffering={together.isBuffering(peer.id)} />
						<span class="w-full truncate text-center text-[11px] font-semibold">{peer.name}</span>
						{#if peerRole(peer)}
							<span class="-mt-1 text-[10px] text-white/45">{peerRole(peer)}</span>
						{/if}
					</div>
				{/each}
				{#if isSearching(room.status)}
					<button class="flex w-14 shrink-0 flex-col items-center gap-1" onclick={invite}>
						<span
							class="waiting-tile flex h-12 w-12 items-center justify-center rounded-[28%] border-2 border-dashed border-white/25"
						>
							<PlusIcon size={18} class="opacity-50" />
						</span>
						<span class="text-[11px] text-white/40">Invite</span>
					</button>
				{/if}
				<button
					class="ml-auto flex w-14 shrink-0 flex-col items-center gap-1"
					onclick={() => together.leave()}
				>
					<span
						class="flex h-12 w-12 items-center justify-center rounded-[28%] bg-red-400/10 text-red-300"
					>
						<SignOutIcon size={20} />
					</span>
					<span class="text-[11px] text-red-300/80">Leave</span>
				</button>
			</div>
		</div>
	</div>
{/if}

<style>
	/* Radar rings behind the icon. */
	.hero-ring {
		border: 1px solid rgb(255 255 255 / 0.35);
		animation: ring-out 2400ms ease-out infinite;
	}

	.hero-ring-late {
		animation-delay: 1200ms;
	}

	@keyframes ring-out {
		from {
			opacity: 0.8;
			scale: 1;
		}
		to {
			opacity: 0;
			scale: 1.9;
		}
	}

	.join-button {
		color: rgb(255 255 255 / 0.35);
		background-color: rgb(255 255 255 / 0.06);
		transition:
			background-color 220ms ease-out,
			color 220ms ease-out,
			scale 320ms cubic-bezier(0.34, 1.56, 0.64, 1);
	}

	.join-ready {
		background-color: white;
		color: black;
		scale: 1.02;
	}

	.code-character {
		display: inline-block;
		animation: tile-in 420ms cubic-bezier(0.34, 1.56, 0.64, 1) backwards;
	}

	.code-copied {
		color: rgb(134 239 172);
		transition: color 300ms ease-out;
	}

	@keyframes tile-in {
		from {
			opacity: 0;
			translate: 0 10px;
			scale: 0.7;
		}
	}

	.waiting-tile {
		animation: breathe 1800ms ease-in-out infinite;
	}

	@keyframes breathe {
		50% {
			opacity: 0.45;
			scale: 0.94;
		}
	}

	:global(.status-dot-pulse) {
		animation: together-dot-pulse 1200ms ease-in-out infinite;
	}

	@keyframes -global-together-dot-pulse {
		50% {
			opacity: 0.35;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.hero-ring,
		.code-character,
		.waiting-tile,
		:global(.status-dot-pulse) {
			animation: none;
		}
	}
</style>
