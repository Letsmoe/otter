// ISO 639-1 codes and the three-letter codes subtitle sources use for them.
// The first three-letter code of a row is the one sources are asked with.

const LANGUAGE_ROWS: string[][] = [
  ["en", "eng"],
  ["de", "ger", "deu"],
  ["fr", "fre", "fra"],
  ["es", "spa"],
  ["it", "ita"],
  ["pt", "por", "pob", "pb"],
  ["nl", "dut", "nld"],
  ["pl", "pol"],
  ["ru", "rus"],
  ["tr", "tur"],
  ["sv", "swe"],
  ["da", "dan"],
  ["no", "nor", "nob"],
  ["fi", "fin"],
  ["cs", "cze", "ces"],
  ["sk", "slo", "slk"],
  ["hu", "hun"],
  ["ro", "rum", "ron"],
  ["bg", "bul"],
  ["el", "ell", "gre"],
  ["he", "heb"],
  ["ar", "ara"],
  ["zh", "chi", "zho", "zht", "zhc"],
  ["ja", "jpn"],
  ["ko", "kor"],
  ["hi", "hin"],
  ["th", "tha"],
  ["vi", "vie"],
  ["id", "ind"],
  ["uk", "ukr"],
  ["hr", "hrv"],
  ["sr", "scc", "srp"],
  ["sl", "slv"],
  ["lt", "lit"],
  ["lv", "lav"],
  ["et", "est"],
  ["fa", "per", "fas"],
  ["ca", "cat"],
  ["eu", "baq", "eus"],
  ["gl", "glg"],
  ["is", "ice", "isl"],
  ["ms", "may", "msa"],
  ["bn", "ben"],
  ["ta", "tam"],
  ["sq", "alb", "sqi"],
  ["mk", "mac", "mkd"],
  ["bs", "bos"],
  ["hy", "arm", "hye"],
  ["ka", "geo", "kat"],
];

const THREE_LETTER_TO_TWO = new Map<string, string>();
const TWO_TO_THREE_LETTER = new Map<string, string>();
for (const [twoLetter, preferred, ...others] of LANGUAGE_ROWS) {
  TWO_TO_THREE_LETTER.set(twoLetter, preferred);
  for (const threeLetter of [preferred, ...others]) THREE_LETTER_TO_TWO.set(threeLetter, twoLetter);
}

/** "ger" or "deu" to "de"; undefined for a language the table does not know. */
export function toIso6391(code: string): string | undefined {
  const lowered = code.trim().toLowerCase();
  if (lowered.length === 2 && TWO_TO_THREE_LETTER.has(lowered)) return lowered;
  return THREE_LETTER_TO_TWO.get(lowered);
}

/** "de" to the three-letter code subtitle sources are asked with ("ger"); undefined when unknown. */
export function toIso6392(code: string): string | undefined {
  return TWO_TO_THREE_LETTER.get(code.trim().toLowerCase());
}
