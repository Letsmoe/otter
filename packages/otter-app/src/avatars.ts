// Profile avatars. Presets are pictures shipped with each app under
// static/avatars/<set>-<name>.webp, stored on the profile as "<set>:<name>"
// (e.g. "otter:crown"). Older profiles keep their inline-SVG faces, stored as
// "avatar:<color>-<face>".

const AVATAR_PREFIX = 'avatar:';

export interface AvatarSet {
	key: string;
	label: string;
	/** Preset ids, "<set>:<name>". */
	avatars: string[];
}

function avatarSet(key: string, label: string, names: string[]): AvatarSet {
	return { key, label, avatars: names.map((name) => `${key}:${name}`) };
}

/** The groups the icon chooser shows, in order. */
export const AVATAR_SETS: AvatarSet[] = [
	avatarSet('otter', 'Otters', [
		'smile',
		'wink',
		'sunglasses',
		'headphones',
		'beanie',
		'sleepy',
		'surprised',
		'tongue',
		'bowtie',
		'popcorn',
		'glasses3d',
		'crown'
	]),
	avatarSet('heroes', 'Movie Heroes', [
		'astronaut',
		'detective',
		'cowboy',
		'pirate',
		'knight',
		'wizard',
		'ninja',
		'vampire',
		'superhero',
		'robot',
		'zombie',
		'rockstar'
	]),
	avatarSet('monsters', 'Monsters', [
		'fluffy',
		'alien',
		'dragon',
		'ghost',
		'yeti',
		'slime',
		'furball',
		'cyclops',
		'gremlin',
		'kraken',
		'moss',
		'star'
	]),
	avatarSet('forest', 'Forest', [
		'fox',
		'raccoon',
		'redpanda',
		'owl',
		'hedgehog',
		'bear',
		'fawn',
		'squirrel',
		'badger',
		'wolf',
		'bunny',
		'koala'
	]),
	avatarSet('sea', 'River & Sea', [
		'beaver',
		'seal',
		'penguin',
		'axolotl',
		'turtle',
		'puffin',
		'frog',
		'duckling',
		'octopus',
		'crab',
		'narwhal',
		'platypus'
	]),
	avatarSet('robots', 'Robots', [
		'happy',
		'wink',
		'hearts',
		'sleepy',
		'surprised',
		'laughing',
		'cool',
		'thinking',
		'excited',
		'shy',
		'grin',
		'lovestruck'
	]),
	avatarSet('kids', 'Kids', [
		'otter',
		'chick',
		'piglet',
		'puppy',
		'kitten',
		'elephant',
		'lamb',
		'panda',
		'giraffe',
		'bunny',
		'penguin',
		'dino'
	])
];

const PRESET_IDS = new Set(AVATAR_SETS.flatMap((set) => set.avatars));

const COLORS: Record<string, [string, string]> = {
	blue: ['#3b82f6', '#1d4ed8'],
	red: ['#ef4444', '#b91c1c'],
	green: ['#4ade80', '#16a34a'],
	yellow: ['#facc15', '#ca8a04'],
	purple: ['#a78bfa', '#6d28d9'],
	pink: ['#f472b6', '#be185d'],
	teal: ['#2dd4bf', '#0f766e'],
	orange: ['#fb923c', '#c2410c']
};

const EYES = '<circle cx="34" cy="40" r="6"/><circle cx="66" cy="40" r="6"/>';
const SMILE =
	'<path d="M28 62 Q50 80 72 62" fill="none" stroke="#fff" stroke-width="6" stroke-linecap="round"/>';

const FACES: Record<string, string> = {
	smile: EYES + SMILE,
	grin: EYES + '<path d="M28 58 H72 Q72 82 50 82 Q28 82 28 58 Z"/>',
	wink:
		'<circle cx="34" cy="40" r="6"/><path d="M58 40 H74" stroke="#fff" stroke-width="6" stroke-linecap="round"/>' +
		SMILE,
	surprised: EYES + '<circle cx="50" cy="68" r="9" fill="none" stroke="#fff" stroke-width="6"/>',
	happy:
		'<path d="M26 42 Q34 32 42 42 M58 42 Q66 32 74 42" fill="none" stroke="#fff" stroke-width="6" stroke-linecap="round"/>' +
		SMILE,
	cool: '<rect x="20" y="33" width="60" height="14" rx="7" fill="#111"/>' + SMILE
};

/** Every preset, all sets. */
export const PRESET_AVATARS: string[] = AVATAR_SETS.flatMap((set) => set.avatars);

// Profiles without an avatar get one of the otters.
const DEFAULT_AVATARS = AVATAR_SETS[0].avatars;

interface AvatarParts {
	colors: [string, string];
	face: string;
}

function parseAvatar(avatarId: string): AvatarParts | null {
	if (!avatarId.startsWith(AVATAR_PREFIX)) return null;
	const [colorName, faceName] = avatarId.slice(AVATAR_PREFIX.length).split('-');
	const colors = COLORS[colorName];
	const face = FACES[faceName];
	if (!colors || !face) return null;
	return { colors, face };
}

function svgDataUrl({ colors, face }: AvatarParts): string {
	const svg =
		'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">' +
		`<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${colors[0]}"/><stop offset="1" stop-color="${colors[1]}"/></linearGradient></defs>` +
		`<rect width="100" height="100" fill="url(#g)"/><g fill="#fff">${face}</g></svg>`;
	return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

/** Picks a stable preset for a name, used when a profile has no avatar. */
export function defaultAvatarFor(name: string): string {
	let hash = 0;
	for (const character of name) {
		hash = (hash * 31 + character.charCodeAt(0)) >>> 0;
	}
	return DEFAULT_AVATARS[hash % DEFAULT_AVATARS.length];
}

/**
 * Image URL for a stored avatar value: presets become their bundled
 * picture, old presets inline SVG, remote URLs pass through, anything else
 * falls back to the name's default otter.
 */
export function avatarImage(avatarUrl: string | null, name: string): string {
	if (avatarUrl && /^(https?:|data:)/.test(avatarUrl)) return avatarUrl;
	const presetImage = presetImageUrl(avatarUrl || '');
	if (presetImage) return presetImage;
	const parts = parseAvatar(avatarUrl || '');
	if (parts) return svgDataUrl(parts);
	return presetImageUrl(defaultAvatarFor(name)) || '';
}

function presetImageUrl(avatarId: string): string | null {
	if (!PRESET_IDS.has(avatarId)) return null;
	return `/avatars/${avatarId.replace(':', '-')}.webp`;
}
