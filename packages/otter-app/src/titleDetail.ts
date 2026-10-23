// Detail data shared by the movie and series pages.
import { otter } from './api';
import { posterUrl } from './tmdb';
import {
	TITLE_CARD_SELECT,
	mediaKindOf,
	mediaTypeOf,
	toRailItem,
	withoutRepeats,
	type MediaKind,
	type RailItem
} from './discovery';

export interface CastMember {
	personId: string;
	name: string;
	character: string | null;
	order: number;
	profileUrl: string | null;
}

export interface SeasonInfo {
	id: string;
	seasonNumber: number;
	name: string | null;
	episodeCount: number;
}

export interface TitleDetail {
	id: string;
	mediaType: MediaKind;
	title: string;
	tagline: string | null;
	overview: string | null;
	posterPath: string | null;
	backdropPath: string | null;
	year: number | null;
	// Minutes; movies only.
	runtime: number | null;
	status: string | null;
	voteAverage: number;
	trailerKey: string | null;
	genres: string[];
	cast: CastMember[];
	directors: string[];
	/** Directors with their person ids, for "Directed by" rows. */
	directorPeople: { personId: string; name: string }[];
	seasons: SeasonInfo[];
	related: RailItem[];
}

interface CreditRow {
	person: { id: string; name: string; profilePath: string | null };
	role: string;
	job: string | null;
	character: string | null;
	order: number;
}

const RELATED_LIMIT = 20;

const TITLE_DETAIL_SELECT = {
	...TITLE_CARD_SELECT,
	tagline: true,
	runtime: true,
	status: true,
	voteAverage: true,
	trailerKey: true,
	genres: true,
	credits: {
		person: { id: true, name: true, profilePath: true },
		role: true,
		job: true,
		character: true,
		order: true
	},
	seasons: { id: true, seasonNumber: true, name: true, episodeCount: true },
	related: { __args: { limit: RELATED_LIMIT }, ...TITLE_CARD_SELECT }
} as const;

function castOf(credits: CreditRow[]): CastMember[] {
	const cast = credits.filter((credit) => credit.role === 'cast');
	cast.sort((first, second) => first.order - second.order);
	return cast.map((credit) => ({
		personId: credit.person.id,
		name: credit.person.name,
		character: credit.character,
		order: credit.order,
		profileUrl: posterUrl(credit.person.profilePath, 'w185')
	}));
}

function directorCredits(credits: CreditRow[]): CreditRow[] {
	return credits.filter((credit) => credit.role === 'crew' && credit.job === 'Director');
}

function directorsOf(credits: CreditRow[]): string[] {
	return directorCredits(credits).map((credit) => credit.person.name);
}

function directorPeopleOf(credits: CreditRow[]): { personId: string; name: string }[] {
	return directorCredits(credits).map((credit) => ({
		personId: credit.person.id,
		name: credit.person.name
	}));
}

/** One roundtrip for a movie or series page; the backend fetches TMDB detail on first read. */
export async function loadTitleDetail(id: string): Promise<TitleDetail | null> {
	try {
		const data = await otter.query({ title: { __args: { id }, ...TITLE_DETAIL_SELECT } });
		const title = data.title;
		if (!title) return null;
		const seasons = [...title.seasons].sort(
			(first, second) => first.seasonNumber - second.seasonNumber
		);
		return {
			id: title.id,
			mediaType: mediaKindOf(title.mediaType),
			title: title.title,
			tagline: title.tagline,
			overview: title.overview,
			posterPath: title.posterPath,
			backdropPath: title.backdropPath,
			year: title.year,
			runtime: title.runtime,
			status: title.status,
			voteAverage: title.voteAverage,
			trailerKey: title.trailerKey,
			genres: title.genres,
			cast: castOf(title.credits),
			directors: directorsOf(title.credits),
			directorPeople: directorPeopleOf(title.credits),
			seasons,
			related: title.related.map(toRailItem)
		};
	} catch {
		return null;
	}
}

export interface TitleRail {
	key: string;
	title: string;
	items: RailItem[];
}

async function filmographyOf(personId: string, kind: MediaKind, job?: string): Promise<RailItem[]> {
	try {
		const data = await otter.query({
			person: { __args: { id: personId }, filmography: { __args: { job }, ...TITLE_CARD_SELECT } }
		});
		if (!data.person) return [];
		return data.person.filmography.map(toRailItem).filter((item) => item.mediaType === kind);
	} catch {
		return [];
	}
}

async function genreTitles(genre: string, kind: MediaKind): Promise<RailItem[]> {
	try {
		const data = await otter.query({
			browse: { __args: { mediaType: mediaTypeOf(kind), genre, limit: 20 }, ...TITLE_CARD_SELECT }
		});
		return data.browse.map(toRailItem);
	} catch {
		return [];
	}
}

function pluralOf(kind: MediaKind): string {
	if (kind === 'series') return 'Shows';
	return 'Movies';
}

/**
 * Grouped "more like this" rows for a title page: related titles, the lead
 * actor's and director's other work, and the first genre. Each title shows
 * once, and never the page's own title.
 */
export async function loadTitleRails(detail: TitleDetail): Promise<TitleRail[]> {
	const kind = detail.mediaType;
	const rails: Promise<TitleRail>[] = [
		Promise.resolve({ key: 'related', title: 'More Like This', items: detail.related })
	];
	const lead = detail.cast[0];
	if (lead) {
		rails.push(
			filmographyOf(lead.personId, kind).then((items) => ({
				key: 'lead',
				title: `More with ${lead.name}`,
				items
			}))
		);
	}
	const director = detail.directorPeople[0];
	if (director) {
		rails.push(
			filmographyOf(director.personId, kind, 'Director').then((items) => ({
				key: 'director',
				title: `Directed by ${director.name}`,
				items
			}))
		);
	}
	const genre = detail.genres[0];
	if (genre) {
		rails.push(
			genreTitles(genre, kind).then((items) => ({
				key: 'genre',
				title: `${genre} ${pluralOf(kind)}`,
				items
			}))
		);
	}
	const loaded = await Promise.all(rails);
	const deduped = withoutRepeats(
		loaded.map((rail) => rail.items),
		[detail.id]
	);
	return loaded.map((rail, index) => ({ ...rail, items: deduped[index] }));
}
