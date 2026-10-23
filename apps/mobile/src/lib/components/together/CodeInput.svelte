<script lang="ts">
	// One box per room-code character, in two groups of four. Typing moves to
	// the next box, Backspace on an empty box goes back, and pasting a code or
	// an invite text fills every box.
	import { onMount, untrack } from 'svelte';
	import { roomCodeFromText } from '@neoworks-dev/otter-app/together';

	const CODE_LENGTH = 8;
	// Room-code alphabet: no I, O, 0 or 1.
	const CODE_CHARACTER = /^[A-HJ-NP-Z2-9]$/;

	interface Props {
		/** Prefill, e.g. from an invite link. */
		initial?: string;
		disabled?: boolean;
		onchange: (code: string) => void;
		/** All eight boxes are filled. */
		oncomplete?: (code: string) => void;
	}

	let { initial = '', disabled = false, onchange, oncomplete }: Props = $props();

	let characters = $state<string[]>(untrack(() => initialCharacters(initial)));
	let boxes = $state<HTMLInputElement[]>([]);

	function initialCharacters(code: string): string[] {
		const filled = codeCharacters(code).slice(0, CODE_LENGTH);
		return Array.from({ length: CODE_LENGTH }, (_, index) => filled[index] || '');
	}

	function codeCharacters(text: string): string[] {
		return Array.from(text.toUpperCase()).filter((character) => CODE_CHARACTER.test(character));
	}

	onMount(() => {
		if (!initial) boxes[0]?.focus();
	});

	// Writes characters from a box onwards and focuses the box after the last one.
	function fillFrom(startIndex: number, text: string) {
		const typed = codeCharacters(text);
		let index = startIndex;
		for (const character of typed) {
			if (index >= CODE_LENGTH) break;
			characters[index] = character;
			pop(boxes[index]);
			index += 1;
		}
		focusBox(Math.min(index, CODE_LENGTH - 1));
		report();
	}

	function onInput(index: number, event: Event) {
		const box = event.currentTarget as HTMLInputElement;
		const typed = box.value;
		if (typed === '') {
			characters[index] = '';
			report();
			return;
		}
		fillFrom(index, typed);
		// Rejected characters (e.g. "O") leave the box as it was.
		box.value = characters[index];
	}

	function onKeydown(index: number, event: KeyboardEvent) {
		if (event.key === 'Backspace' && characters[index] === '' && index > 0) {
			event.preventDefault();
			characters[index - 1] = '';
			focusBox(index - 1);
			report();
			return;
		}
		if (event.key === 'ArrowLeft') focusBox(Math.max(0, index - 1));
		if (event.key === 'ArrowRight') focusBox(Math.min(CODE_LENGTH - 1, index + 1));
	}

	// A pasted invite text fills the whole code; anything else fills from here.
	function onPaste(index: number, event: ClipboardEvent) {
		event.preventDefault();
		const text = event.clipboardData?.getData('text') || '';
		const code = roomCodeFromText(text);
		if (code) {
			fillFrom(0, code);
			return;
		}
		fillFrom(index, text);
	}

	function focusBox(index: number) {
		const box = boxes[index];
		if (!box) return;
		box.focus();
		box.select();
	}

	function report() {
		const code = characters.join('');
		onchange(code);
		if (code.length === CODE_LENGTH) oncomplete?.(code);
	}

	function pop(box: HTMLInputElement | undefined) {
		box?.animate([{ scale: 0.82 }, { scale: 1.08 }, { scale: 1 }], {
			duration: 280,
			easing: 'cubic-bezier(0.34, 1.56, 0.64, 1)'
		});
	}

	// Lets the keyboard offer a copied code on the first box.
	function autocompleteFor(index: number): AutoFill {
		if (index === 0) return 'one-time-code';
		return 'off';
	}

	// The middle gap splits the code into two groups of four.
	function boxClass(index: number): string {
		if (index === CODE_LENGTH / 2 - 1) return 'mr-3';
		return '';
	}
</script>

<div class="flex justify-center gap-1.5">
	{#each characters as character, index (index)}
		<input
			bind:this={boxes[index]}
			class="code-box h-13 w-9 min-w-0 rounded-xl text-center font-mono text-xl font-bold uppercase caret-transparent outline-none {boxClass(
				index
			)}"
			class:code-box-filled={character !== ''}
			value={character}
			{disabled}
			inputmode="text"
			autocapitalize="characters"
			autocomplete={autocompleteFor(index)}
			spellcheck="false"
			aria-label="Code character {index + 1}"
			oninput={(event) => onInput(index, event)}
			onkeydown={(event) => onKeydown(index, event)}
			onpaste={(event) => onPaste(index, event)}
			onfocus={(event) => event.currentTarget.select()}
		/>
	{/each}
</div>

<style>
	.code-box {
		background-color: rgb(255 255 255 / 0.06);
		border: 1px solid rgb(255 255 255 / 0.08);
		transition:
			background-color 180ms ease-out,
			border-color 180ms ease-out,
			box-shadow 180ms ease-out;
	}

	.code-box-filled {
		background-color: rgb(255 255 255 / 0.12);
	}

	.code-box:focus {
		border-color: rgb(255 255 255 / 0.7);
		box-shadow: 0 0 0 3px rgb(255 255 255 / 0.12);
	}
</style>
