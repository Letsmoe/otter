// Decoder for Dean Edwards' packer: eval(function(p,a,c,k,e,d){...}('payload',radix,count,'words'.split('|'),0,{}))

const PACKER_SIGNATURE = /eval\(function\(p,a,c,k,e,[dr]\)/;
const PAYLOAD_START = /\}\(['"]/;
const RADIX_AND_COUNT = /^,\s*(\d+)\s*,\s*(\d+)\s*,\s*(?=['"])/;
const SPLIT_ON_PIPE = /^\s*\.split\(\s*['"]\|['"]\s*\)/;
const MAX_NESTED_LAYERS = 3;

interface QuotedString {
  value: string;
  /** Index right after the closing quote. */
  end: number;
}

const ESCAPES: Record<string, string> = { n: "\n", t: "\t", r: "\r" };

/** Reads a JS string literal whose opening quote sits at `start`. */
function readQuotedString(source: string, start: number): QuotedString | undefined {
  const quote = source[start];
  let value = "";
  let position = start + 1;
  while (position < source.length) {
    const character = source[position];
    if (character === quote) return { value, end: position + 1 };
    if (character !== "\\") {
      value += character;
      position += 1;
      continue;
    }
    const escaped = source[position + 1];
    value += ESCAPES[escaped] || escaped;
    position += 2;
  }
  return undefined;
}

/** The packer's number encoding: digits 0-9a-z, then A-Z for radixes above 36. */
function encodeNumber(value: number, radix: number): string {
  const remainder = value % radix;
  const prefix = value < radix ? "" : encodeNumber(Math.floor(value / radix), radix);
  if (remainder > 35) return prefix + String.fromCharCode(remainder + 29);
  return prefix + remainder.toString(36);
}

function buildDictionary(words: string[], radix: number, count: number): Map<string, string> {
  const dictionary = new Map<string, string>();
  for (let index = 0; index < count; index++) {
    const key = encodeNumber(index, radix);
    dictionary.set(key, words[index] || key);
  }
  return dictionary;
}

interface PackedBlock {
  payload: string;
  radix: number;
  count: number;
  words: string[];
}

/** Reads the arguments of the first packed block in `source`. */
function readPackedBlock(source: string): PackedBlock | undefined {
  const signature = PACKER_SIGNATURE.exec(source);
  if (!signature) return undefined;
  const payloadStart = source.slice(signature.index).search(PAYLOAD_START);
  if (payloadStart === -1) return undefined;

  const payload = readQuotedString(source, signature.index + payloadStart + 2);
  if (!payload) return undefined;
  const afterPayload = source.slice(payload.end);
  const radixAndCount = RADIX_AND_COUNT.exec(afterPayload);
  if (!radixAndCount) return undefined;
  const wordList = readQuotedString(afterPayload, radixAndCount[0].length);
  if (!wordList) return undefined;
  if (!SPLIT_ON_PIPE.test(afterPayload.slice(wordList.end))) return undefined;

  return {
    payload: payload.value,
    radix: parseInt(radixAndCount[1], 10),
    count: parseInt(radixAndCount[2], 10),
    words: wordList.value.split("|"),
  };
}

function unpackOnce(source: string): string | undefined {
  const block = readPackedBlock(source);
  if (!block) return undefined;
  const dictionary = buildDictionary(block.words, block.radix, block.count);
  return block.payload.replace(/\b\w+\b/g, (word) => {
    const replacement = dictionary.get(word);
    if (replacement === undefined) return word;
    return replacement;
  });
}

/** Fully decoded source of the packed script in `source`, or undefined when nothing is packed. */
export function unpack(source: string): string | undefined {
  let decoded = unpackOnce(source);
  if (decoded === undefined) return undefined;
  for (let layer = 1; layer < MAX_NESTED_LAYERS; layer++) {
    const inner = unpackOnce(decoded);
    if (inner === undefined) return decoded;
    decoded = inner;
  }
  return decoded;
}
