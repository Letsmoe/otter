<script lang="ts">
	// Watch Together chat: quick reactions, the conversation and a message field.
	import { fly } from 'svelte/transition';
	import { backOut, cubicOut } from 'svelte/easing';
	import PaperPlaneRightIcon from 'phosphor-svelte/lib/PaperPlaneRightIcon';
	import ChatCircleDotsIcon from 'phosphor-svelte/lib/ChatCircleDotsIcon';
	import { together } from '$lib/stores/together.svelte';
	import PeerAvatar from './PeerAvatar.svelte';

	const REACTIONS = ['😂', '😮', '😍', '😱', '👏', '🔥'];

	let draft = $state('');
	let list = $state<HTMLDivElement>();
	// Which reaction just popped; the key restarts its animation.
	let popped = $state<{ emoji: string; key: number } | null>(null);

	function send(event: SubmitEvent) {
		event.preventDefault();
		together.sendChat(draft);
		draft = '';
	}

	function react(emoji: string) {
		together.sendReaction(emoji);
		popped = { emoji, key: Date.now() };
	}

	function popKey(emoji: string): number {
		if (popped?.emoji !== emoji) return 0;
		return popped.key;
	}

	// Newest message stays in view.
	$effect(() => {
		if (together.chat.length === 0 || !list) return;
		list.scrollTo({ top: list.scrollHeight, behavior: 'smooth' });
	});

	function bubbleClass(mine: boolean): string {
		if (mine) return 'self-end origin-bottom-right bg-white text-black';
		return 'glass-bar self-start origin-bottom-left';
	}

	function bubbleOffset(mine: boolean): number {
		if (mine) return 16;
		return -16;
	}
</script>

<div class="flex min-h-0 flex-1 flex-col gap-2.5">
	<span class="text-[10px] font-semibold tracking-[0.2em] text-white/45 uppercase">Chat</span>
	<div class="flex justify-between">
		{#each REACTIONS as emoji (emoji)}
			<button
				class="flex h-10 w-10 items-center justify-center rounded-full bg-white/6 text-xl"
				aria-label="React {emoji}"
				onclick={() => react(emoji)}
			>
				{#key popKey(emoji)}
					<span class:reaction-pop={popKey(emoji) !== 0}>{emoji}</span>
				{/key}
			</button>
		{/each}
	</div>

	<div
		bind:this={list}
		class="no-scrollbar flex min-h-24 flex-1 flex-col gap-2 overflow-y-auto rounded-3xl bg-white/5 p-3"
	>
		{#if together.chat.length === 0}
			<div class="m-auto flex flex-col items-center gap-2 text-white/35">
				<ChatCircleDotsIcon size={28} />
				<p class="text-xs">Say hi to everyone watching.</p>
			</div>
		{/if}
		{#each together.chat as line (line.id)}
			<div
				class="flex max-w-[85%] shrink-0 items-end gap-2 rounded-2xl {bubbleClass(line.mine)}"
				in:fly={{ x: bubbleOffset(line.mine), y: 8, duration: 320, easing: backOut }}
			>
				{#if !line.mine}
					<span class="py-1.5 pl-1.5"><PeerAvatar peer={line.from} size={22} /></span>
				{/if}
				<p class="px-3 py-2 text-sm break-words">{line.text}</p>
			</div>
		{/each}
	</div>

	<form class="glass-bar flex h-12 items-center gap-2 rounded-full p-1 pl-4" onsubmit={send}>
		<input
			class="h-full min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-white/35"
			placeholder="Message"
			maxlength={300}
			enterkeyhint="send"
			bind:value={draft}
		/>
		<button
			type="submit"
			class="send-button flex h-10 w-10 items-center justify-center rounded-full"
			class:send-ready={draft.trim() !== ''}
			aria-label="Send"
			disabled={!draft.trim()}
		>
			<span in:fly={{ x: -6, duration: 200, easing: cubicOut }}>
				<PaperPlaneRightIcon size={18} weight="fill" />
			</span>
		</button>
	</form>
</div>

<style>
	.reaction-pop {
		display: inline-block;
		animation: pop 420ms cubic-bezier(0.34, 1.56, 0.64, 1);
	}

	@keyframes pop {
		40% {
			scale: 1.45;
			rotate: -10deg;
		}
	}

	.send-button {
		color: rgb(255 255 255 / 0.35);
		transition:
			background-color 220ms ease-out,
			color 220ms ease-out,
			scale 320ms cubic-bezier(0.34, 1.56, 0.64, 1);
	}

	.send-ready {
		background-color: white;
		color: black;
		scale: 1.05;
	}

	@media (prefers-reduced-motion: reduce) {
		.reaction-pop {
			animation: none;
		}
	}
</style>
