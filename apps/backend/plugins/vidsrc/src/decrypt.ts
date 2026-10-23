import { PluginSourceError, type PluginContext } from "@neoworks-dev/otter-sdk";
import type { DecryptorSource, StreamEnvelope } from "./lookup.ts";

/** The decryptor's ChaCha20 nonce sits in front of the plaintext in its memory. */
const NONCE_LENGTH = 12;

export interface DecryptorExports {
  memory: WebAssembly.Memory;
  alloc(length: number): number;
  decrypt(pointer: number, length: number): number;
}

/** The stream URLs, one per line, from the envelope: plain arrays pass through, strings are decrypted. */
export async function streamUrlsOf(envelope: StreamEnvelope, ctx: PluginContext): Promise<string[]> {
  if (Array.isArray(envelope.streamUrls)) return envelope.streamUrls.filter((url) => url.length > 0);
  const decryptor = await loadDecryptor(envelope.decryptor, ctx);
  return nonEmptyLines(decryptWith(decryptor, envelope.streamUrls));
}

export async function instantiateDecryptor(moduleBytes: BufferSource): Promise<DecryptorExports> {
  const module = await WebAssembly.compile(moduleBytes);
  const instance = await WebAssembly.instantiate(module, {});
  return instance.exports as unknown as DecryptorExports;
}

/** Decrypts a base64 nonce||ciphertext string. Buffer, not TextDecoder: the phone runtime has no TextDecoder. */
export function decryptWith(exports: DecryptorExports, encryptedBase64: string): string {
  const encrypted = Buffer.from(encryptedBase64, "base64");
  const pointer = exports.alloc(encrypted.length);
  new Uint8Array(exports.memory.buffer, pointer, encrypted.length).set(encrypted);
  const plaintextLength = exports.decrypt(pointer, encrypted.length);
  const plaintext = new Uint8Array(exports.memory.buffer, pointer + NONCE_LENGTH, plaintextLength);
  return Buffer.from(plaintext).toString("utf8");
}

async function loadDecryptor(source: DecryptorSource | undefined, ctx: PluginContext): Promise<DecryptorExports> {
  if (!source) throw new PluginSourceError("extraction_failed", "VidSrc: encrypted stream_urls without a decryptor");
  return instantiateDecryptor(await decryptorBytesOf(source, ctx));
}

async function decryptorBytesOf(source: DecryptorSource, ctx: PluginContext): Promise<BufferSource> {
  if ("wasmUrl" in source) return fetchBytes(source.wasmUrl, ctx);
  return new Uint8Array(Buffer.from(source.wasmBase64, "base64"));
}

async function fetchBytes(url: string, ctx: PluginContext): Promise<ArrayBuffer> {
  const response = await ctx.http.get(url);
  return response.response().arrayBuffer();
}

function nonEmptyLines(text: string): string[] {
  return text.split("\n").filter((line) => line.length > 0);
}
