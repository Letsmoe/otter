<script lang="ts">
	// Icon picker: the current icon on top, then one grid per avatar set
	// (Otters, Movie Heroes, Monsters…). Collapsed sets show their first row;
	// tapping the header expands the rest.
	import { untrack } from 'svelte';
	import { slide } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import CaretDownIcon from 'phosphor-svelte/lib/CaretDownIcon';
	import { AVATAR_SETS, avatarImage, type AvatarSet } from '@neoworks-dev/otter-app/avatars';
	import ScreenHeader from '../ScreenHeader.svelte';

	const COLUMNS = 4;

	interface Props {
		selected: string;
		onback: () => void;
		onselect: (avatarId: string) => void;
	}

	let { selected, onback, onselect }: Props = $props();

	// The set holding the current icon starts open.
	let expanded = $state<Record<string, boolean>>(untrack(initialExpanded));

	function initialExpanded(): Record<string, boolean> {
		const open: Record<string, boolean> = {};
		for (const set of AVATAR_SETS) open[set.key] = set.avatars.includes(selected);
		return open;
	}

	function toggle(set: AvatarSet) {
		expanded[set.key] = !expanded[set.key];
	}

	function firstRow(set: AvatarSet): string[] {
		return set.avatars.slice(0, COLUMNS);
	}

	function remainingRows(set: AvatarSet): string[] {
		return set.avatars.slice(COLUMNS);
	}

	function caretClass(set: AvatarSet): string {
		if (expanded[set.key]) return 'rotate-180';
		return '';
	}

	function tileClass(avatarId: string): string {
		if (avatarId === selected) return 'ring-2 ring-white ring-offset-2 ring-offset-transparent';
		return '';
	}
</script>

{#snippet tile(avatarId: string)}
	<button
		class="aspect-square overflow-hidden rounded-2xl {tileClass(avatarId)}"
		aria-label={avatarId}
		aria-pressed={avatarId === selected}
		onclick={() => onselect(avatarId)}
	>
		<img src={avatarImage(avatarId, '')} alt="" loading="lazy" class="h-full w-full" />
	</button>
{/snippet}

<div class="flex h-full flex-col">
	<ScreenHeader title="Choose Icon" {onback} />

	<div
		class="no-scrollbar flex flex-1 flex-col gap-3 overflow-y-auto px-5 pt-2 pb-[calc(var(--safe-bottom)+20px)]"
	>
		<div class="flex items-center gap-3 pb-2">
			<img src={avatarImage(selected, '')} alt="" class="h-14 w-14 rounded-2xl" />
			<span class="text-sm text-white/60">Current icon</span>
		</div>

		{#each AVATAR_SETS as set (set.key)}
			<section class="flex flex-col gap-3 rounded-3xl bg-white/5 p-3">
				<button
					class="flex items-center justify-between px-1 text-left"
					aria-expanded={expanded[set.key]}
					onclick={() => toggle(set)}
				>
					<span class="text-base font-bold">{set.label}</span>
					<span class="flex items-center gap-1 text-xs text-white/55">
						{set.avatars.length}
						<CaretDownIcon size={16} class="transition-transform duration-300 {caretClass(set)}" />
					</span>
				</button>
				<div class="grid grid-cols-4 gap-3">
					{#each firstRow(set) as avatarId (avatarId)}
						{@render tile(avatarId)}
					{/each}
				</div>
				{#if expanded[set.key]}
					<div
						class="grid grid-cols-4 gap-3"
						transition:slide={{ duration: 320, easing: cubicOut }}
					>
						{#each remainingRows(set) as avatarId (avatarId)}
							{@render tile(avatarId)}
						{/each}
					</div>
				{/if}
			</section>
		{/each}
	</div>
</div>
