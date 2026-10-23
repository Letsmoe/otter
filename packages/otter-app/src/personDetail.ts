import { otter } from './api';
import { TITLE_CARD_SELECT, toRailItem, type RailItem } from './discovery';

export interface PersonDetail {
	id: string;
	name: string;
	biography: string | null;
	birthday: string | null;
	deathday: string | null;
	placeOfBirth: string | null;
	knownForDepartment: string | null;
	profilePath: string | null;
	filmography: RailItem[];
}

/** Actor page data; the backend fetches biography + filmography on first read. */
export async function loadPersonDetail(id: string): Promise<PersonDetail | null> {
	try {
		const data = await otter.query({
			person: {
				__args: { id },
				id: true,
				name: true,
				biography: true,
				birthday: true,
				deathday: true,
				placeOfBirth: true,
				knownForDepartment: true,
				profilePath: true,
				filmography: TITLE_CARD_SELECT
			}
		});
		const person = data.person;
		if (!person) return null;
		return { ...person, filmography: person.filmography.map(toRailItem) };
	} catch {
		return null;
	}
}
