<script lang="ts">
	import { goto } from '$app/navigation';
	import FocusList from '$lib/components/FocusList.svelte';
	import { sidebarFocus } from '$lib/stores/focus';
	import { posterUrl } from '@neoworks-dev/otter-app/tmdb';
	import { detailPath, type RailItem } from '@neoworks-dev/otter-app/discovery';

	let { data } = $props();
	let person = $derived(data.person);
	let filmography = $derived(person?.filmography ?? []);
	let photoUrl = $derived(posterUrl(person?.profilePath, 'w342'));

	let bioExpanded = $state(false);

	// Keyboard navigation: left edge opens the sidebar.
	let filmographyList = $state<{ focus: () => void; focusFirst: () => void }>();

	function handleEdge(direction: 'left' | 'right' | 'up' | 'down') {
		if (direction === 'left') sidebarFocus.openSidebar();
	}

	let initialFocusDone = false;
	$effect(() => {
		if (!filmographyList || initialFocusDone) return;
		initialFocusDone = true;
		queueMicrotask(() => filmographyList?.focusFirst());
	});

	let sidebarWasOpen = false;
	$effect(() => {
		const open = $sidebarFocus.open;
		if (sidebarWasOpen && !open) {
			queueMicrotask(() => filmographyList?.focus());
		}
		sidebarWasOpen = open;
	});

	function openEntry(entry: RailItem) {
		goto(detailPath(entry));
	}

	function lifespan(): string | null {
		if (!person?.birthday) return null;
		const born = person.birthday;
		if (person.deathday) return `${born} – ${person.deathday}`;
		return born;
	}
</script>

{#if !person}
	<div class="flex h-64 items-center justify-center">
		<span class="text-base-content/40">Person not found</span>
	</div>
{:else}
	<div class="flex flex-col gap-10">
		<!-- Bio header -->
		<div class="flex flex-col gap-6 sm:flex-row">
			{#if photoUrl}
				<img
					src={photoUrl}
					alt={person.name}
					class="ring-base-100/10 h-72 w-48 flex-shrink-0 rounded-xl object-cover shadow-2xl ring-1"
				/>
			{:else}
				<div
					class="bg-base-300 flex h-72 w-48 flex-shrink-0 items-center justify-center rounded-xl"
				>
					<span class="text-5xl opacity-20">👤</span>
				</div>
			{/if}

			<div class="flex flex-1 flex-col gap-3">
				<h1 class="text-5xl font-bold leading-tight">{person.name}</h1>
				<div class="text-base-content/60 flex flex-wrap items-center gap-2 text-sm">
					{#if lifespan()}<span>{lifespan()}</span>{/if}
					{#if lifespan() && person.placeOfBirth}<span class="opacity-30">·</span>{/if}
					{#if person.placeOfBirth}<span>{person.placeOfBirth}</span>{/if}
					{#if person.knownForDepartment}
						<span class="opacity-30">·</span>
						<span>{person.knownForDepartment}</span>
					{/if}
				</div>

				{#if person.biography}
					<p
						class="text-base-content/75 max-w-3xl text-sm leading-relaxed {bioExpanded
							? ''
							: 'line-clamp-6'}"
					>
						{person.biography}
					</p>
					<button
						class="text-base-content/50 hover:text-base-content self-start text-xs transition-colors"
						onclick={() => (bioExpanded = !bioExpanded)}
					>
						{bioExpanded ? 'Show less' : 'Show more'}
					</button>
				{/if}
			</div>
		</div>

		<!-- Filmography -->
		{#if filmography.length > 0}
			<div>
				<h2 class="mb-4 text-lg font-semibold">Filmography</h2>
				<FocusList
					bind:this={filmographyList}
					items={filmography}
					orientation="horizontal"
					ariaLabel="Filmography"
					onselect={(entry) => openEntry(entry)}
					onedge={handleEdge}
				>
					{#snippet item(entry)}
						<div class="flex w-40 shrink-0 flex-col gap-2">
							<div class="tv-card aspect-[2/3] w-40">
								{#if entry.posterUrl}
									<img
										src={entry.posterUrl}
										alt={entry.title}
										loading="lazy"
										class="h-full w-full object-cover"
									/>
								{:else}
									<div class="flex h-full w-full items-center justify-center">
										<span class="text-4xl opacity-10">🎬</span>
									</div>
								{/if}
							</div>
							<div class="flex flex-col">
								<span class="line-clamp-1 text-sm font-medium">
									{entry.title}
									{#if entry.subtitle}
										<span class="text-base-content/40">({entry.subtitle})</span>
									{/if}
								</span>
							</div>
						</div>
					{/snippet}
				</FocusList>
			</div>
		{/if}
	</div>
{/if}
