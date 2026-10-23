// Voice boost: a Web Audio chain that lifts dialogue over music and effects.
// It cuts the low end, raises the speech range (about 1 to 4 kHz) and
// compresses the result, so quiet speech comes up without loud scenes getting louder.

interface FilterSetting {
	type: BiquadFilterType;
	frequency: number;
	gain: number;
	q?: number;
}

const FILTER_SETTINGS: FilterSetting[] = [
	{ type: 'lowshelf', frequency: 200, gain: -4 },
	{ type: 'peaking', frequency: 1000, gain: 3, q: 1 },
	{ type: 'peaking', frequency: 2800, gain: 6, q: 1 }
];

const COMPRESSOR_SETTINGS = { threshold: -24, knee: 12, ratio: 4, attack: 0.005, release: 0.25 };

// Compensates the level the compressor takes away (about +3 dB).
const MAKEUP_GAIN = 1.4;

export interface VoiceBoost {
	setEnabled(enabled: boolean): void;
	destroy(): void;
}

interface AudioGraph {
	context: AudioContext;
	source: MediaElementAudioSourceNode;
	chainInput: AudioNode;
}

function createFilter(context: AudioContext, setting: FilterSetting): BiquadFilterNode {
	const filter = context.createBiquadFilter();
	filter.type = setting.type;
	filter.frequency.value = setting.frequency;
	filter.gain.value = setting.gain;
	if (setting.q !== undefined) filter.Q.value = setting.q;
	return filter;
}

function createCompressor(context: AudioContext): DynamicsCompressorNode {
	const compressor = context.createDynamicsCompressor();
	compressor.threshold.value = COMPRESSOR_SETTINGS.threshold;
	compressor.knee.value = COMPRESSOR_SETTINGS.knee;
	compressor.ratio.value = COMPRESSOR_SETTINGS.ratio;
	compressor.attack.value = COMPRESSOR_SETTINGS.attack;
	compressor.release.value = COMPRESSOR_SETTINGS.release;
	return compressor;
}

// Connects filters, compressor and makeup gain in a row; returns both ends.
function buildChain(context: AudioContext): { input: AudioNode; output: AudioNode } {
	const filters = FILTER_SETTINGS.map((setting) => createFilter(context, setting));
	const compressor = createCompressor(context);
	const makeup = context.createGain();
	makeup.gain.value = MAKEUP_GAIN;
	const nodes = [...filters, compressor, makeup];
	for (let index = 0; index < nodes.length - 1; index++) {
		nodes[index].connect(nodes[index + 1]);
	}
	return { input: nodes[0], output: nodes[nodes.length - 1] };
}

// A context the browser keeps suspended (no user gesture yet) would silence the
// element once it is routed through it, so only a running one is used.
async function startContext(): Promise<AudioContext | null> {
	const context = new AudioContext();
	try {
		await context.resume();
	} catch {
		// Stays suspended; handled below.
	}
	if (context.state === 'running') return context;
	await context.close();
	return null;
}

async function createGraph(media: HTMLMediaElement): Promise<AudioGraph | null> {
	const context = await startContext();
	if (!context) return null;
	try {
		const chain = buildChain(context);
		chain.output.connect(context.destination);
		// An element can only ever feed one source node, so the graph is built once.
		const source = context.createMediaElementSource(media);
		return { context, source, chainInput: chain.input };
	} catch (error) {
		await context.close();
		throw error;
	}
}

/**
 * The chain is created the first time voice boost is enabled. A media element
 * cannot be released from its source node afterwards, so turning it off routes
 * the source straight to the speakers instead.
 */
export function createVoiceBoost(media: HTMLMediaElement): VoiceBoost {
	let wanted = false;
	let graph: AudioGraph | null = null;
	let building = false;
	let unsupported = false;
	let destroyed = false;

	function route(): void {
		if (!graph) return;
		graph.source.disconnect();
		if (wanted) {
			graph.source.connect(graph.chainInput);
			return;
		}
		graph.source.connect(graph.context.destination);
	}

	async function build(): Promise<void> {
		building = true;
		try {
			graph = await createGraph(media);
		} catch {
			unsupported = true;
		}
		building = false;
		if (destroyed) {
			destroy();
			return;
		}
		route();
	}

	// Enabled before the first user gesture: try again once playback starts.
	function retryOnPlay(): void {
		if (wanted && !graph && !building && !unsupported) void build();
	}

	function setEnabled(enabled: boolean): void {
		wanted = enabled;
		if (graph) {
			route();
			return;
		}
		if (wanted && !building && !unsupported) void build();
	}

	function destroy(): void {
		destroyed = true;
		media.removeEventListener('play', retryOnPlay);
		if (graph) void graph.context.close();
		graph = null;
	}

	media.addEventListener('play', retryOnPlay);
	return { setEnabled, destroy };
}
