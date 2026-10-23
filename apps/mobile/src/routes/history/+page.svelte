<script lang="ts">
	// Watch History: what the profile watched, newest first, grouped by day,
	// with each title's progress.
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import { detailPath, fetchHistory, type RailItem } from '@neoworks-dev/otter-app/discovery';
	import { notificationAge } from '@neoworks-dev/otter-app/notifications';
	import ClockCounterClockwiseIcon from 'phosphor-svelte/lib/ClockCounterClockwiseIcon';
	import ScreenHeader from '$lib/components/ScreenHeader.svelte';

	interface Day {
		label: string;
		items: RailItem[];
	}

	let items = $state<RailItem[]>([]);
	let loaded = $state(false);

	let days = $derived(groupByDay(items));

	$effect(() => {
		const profile = $activeProfile;
		if (!profile) return;
		let cancelled = false;
		fetchHistory(profile.id, 100).then((result) => {
			if (cancelled) return;
			items = result;
			loaded = true;
		});
		return () => {
			cancelled = true;
		};
	});

	function dayLabel(item: RailItem): string {
		if (!item.watchedAt) return 'Earlier';
		return notificationAge(item.watchedAt);
	}

	function groupByDay(watched: RailItem[]): Day[] {
		const groups: Day[] = [];
		for (const item of watched) {
			const label = dayLabel(item);
			const last = groups.at(-1);
			if (last && last.label === label) {
				last.items.push(item);
				continue;
			}
			groups.push({ label, items: [item] });
		}
		return groups;
	}

	function progressPercent(item: RailItem): number {
		if (item.progress === undefined) return 0;
		return item.progress * 100;
	}
</script>

<div class="-mx-3">
	<ScreenHeader title="Watch History" onback={() => history.back()} />
</div>

{#if days.length > 0}
	<div class="flex flex-col gap-6 pt-2 pb-8">
		{#each days as day, dayIndex (`${dayIndex}-${day.label}`)}
			<section class="flex flex-col gap-3">
				<h2 class="px-1 text-xs font-semibold tracking-wider text-white/50 uppercase">
					{day.label}
				</h2>
				<div class="glass-bar flex flex-col gap-3 rounded-3xl p-3">
					{#each day.items as item, index (`${index}-${item.id}`)}
						<a href={detailPath(item)} class="flex items-center gap-3">
							<span
								class="relative aspect-video w-32 shrink-0 overflow-hidden rounded-xl bg-white/5"
							>
								{#if item.backdropUrl}
									<img
										src={item.backdropUrl}
										alt=""
										loading="lazy"
										class="h-full w-full object-cover"
									/>
								{/if}
								<span class="absolute inset-x-0 bottom-0 h-1 bg-white/20">
									<span class="bg-primary block h-full" style:width="{progressPercent(item)}%"
									></span>
								</span>
							</span>
							<span class="flex min-w-0 flex-1 flex-col">
								<span class="line-clamp-1 text-sm font-semibold">{item.title}</span>
								{#if item.subtitle}
									<span class="line-clamp-1 text-xs text-white/55">{item.subtitle}</span>
								{/if}
							</span>
						</a>
					{/each}
				</div>
			</section>
		{/each}
	</div>
{:else if loaded}
	<div class="flex flex-col items-center gap-3 pt-16 text-center">
		<ClockCounterClockwiseIcon size={56} class="opacity-40" />
		<p class="max-w-xs text-sm opacity-60">What you watch shows up here.</p>
	</div>
{/if}
