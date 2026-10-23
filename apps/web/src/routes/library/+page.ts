import { fetchLibraryPage } from '@neoworks-dev/otter-app/discovery';

const RAIL_SIZE = 25;

// Stored titles of each kind, most popular first, each linking to its full grid.
export async function load() {
	const [movies, series] = await Promise.all([
		fetchLibraryPage('movie', '', 0, RAIL_SIZE),
		fetchLibraryPage('series', '', 0, RAIL_SIZE)
	]);
	return { movies, series };
}
