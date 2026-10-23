import { describe, expect, test } from "bun:test";
import { decryptWith, instantiateDecryptor } from "../src/decrypt.ts";

const fixtureDirectory = `${import.meta.dir}/fixtures`;

describe("vidsrc decrypt", () => {
  test("the API's encrypted stream_urls decrypt to the three stream URLs", async () => {
    const api = await Bun.file(`${fixtureDirectory}/data.vidsrc.sh_api.php.json`).json();
    const decryptorBytes = new Uint8Array(await Bun.file(`${fixtureDirectory}/data.vidsrc.sh_wasm.php`).arrayBuffer());
    const exports = await instantiateDecryptor(decryptorBytes);
    const decryptedLines = decryptWith(exports, api.data.stream_urls).split("\n").filter((line) => line.length > 0);
    const expectedText = await Bun.file(`${fixtureDirectory}/expected-stream-urls.txt`).text();
    expect(decryptedLines).toEqual(expectedText.split("\n").filter((line) => line.length > 0));
    expect(decryptedLines).toHaveLength(3);
  });
});
