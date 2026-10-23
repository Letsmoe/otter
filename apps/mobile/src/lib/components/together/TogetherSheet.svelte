<script lang="ts">
	// Watch Together over the app: the room, and once in one, the chat.
	import { fly } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import BottomSheet from '$lib/components/BottomSheet.svelte';
	import { together } from '$lib/stores/together.svelte';
	import TogetherPanel from './TogetherPanel.svelte';
	import TogetherChat from './TogetherChat.svelte';
</script>

<BottomSheet onclose={() => together.closeSheet()}>
	{#key together.inviteCode}
		<TogetherPanel initialCode={together.inviteCode} />
	{/key}
	{#if together.open}
		<div
			class="mt-5 flex h-[42dvh] shrink-0 flex-col border-t border-white/8 pt-4"
			in:fly={{ y: 24, duration: 360, delay: 120, easing: cubicOut }}
		>
			<TogetherChat />
		</div>
	{/if}
</BottomSheet>
