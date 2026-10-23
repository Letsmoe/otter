// AES-GCM decryption in plain JS: the plugin runtime has neither crypto.subtle nor node:crypto.

const BLOCK_SIZE = 16;
const GCM_TAG_LENGTH = 16;
const GCM_NONCE_LENGTH = 12;
const FIELD_POLYNOMIAL = 0x11b;

interface AesTables {
  substitutionBox: Uint8Array;
}

let cachedTables: AesTables | undefined;

function multiplyInField(left: number, right: number): number {
  let product = 0;
  let factor = left;
  let remaining = right;
  while (remaining > 0) {
    if (remaining & 1) product ^= factor;
    factor <<= 1;
    if (factor & 0x100) factor ^= FIELD_POLYNOMIAL;
    remaining >>= 1;
  }
  return product;
}

function rotateLeft(value: number, bits: number): number {
  return ((value << bits) | (value >> (8 - bits))) & 0xff;
}

/** The S-box derived from its definition: multiplicative inverse in GF(2^8), then the affine transform. */
function buildSubstitutionBox(): Uint8Array {
  const box = new Uint8Array(256);
  const inverses = new Uint8Array(256);
  for (let value = 1; value < 256; value++) {
    for (let candidate = 1; candidate < 256; candidate++) {
      if (multiplyInField(value, candidate) === 1) inverses[value] = candidate;
    }
  }
  for (let value = 0; value < 256; value++) {
    const inverse = inverses[value];
    box[value] = inverse ^ rotateLeft(inverse, 1) ^ rotateLeft(inverse, 2) ^ rotateLeft(inverse, 3) ^ rotateLeft(inverse, 4) ^ 0x63;
  }
  return box;
}

function tables(): AesTables {
  if (!cachedTables) cachedTables = { substitutionBox: buildSubstitutionBox() };
  return cachedTables;
}

/** Round keys for a 16, 24 or 32 byte key, as one flat byte array of 16 bytes per round. */
function expandKey(key: Uint8Array, substitutionBox: Uint8Array): Uint8Array {
  const keyWords = key.length / 4;
  const rounds = keyWords + 6;
  const expanded = new Uint8Array(BLOCK_SIZE * (rounds + 1));
  expanded.set(key);
  let roundConstant = 1;
  for (let word = keyWords; word < 4 * (rounds + 1); word++) {
    const previous = expanded.slice((word - 1) * 4, word * 4);
    if (word % keyWords === 0) {
      previous.set([substitutionBox[previous[1]] ^ roundConstant, substitutionBox[previous[2]], substitutionBox[previous[3]], substitutionBox[previous[0]]]);
      roundConstant = multiplyInField(roundConstant, 2);
    } else if (keyWords > 6 && word % keyWords === 4) {
      for (let index = 0; index < 4; index++) previous[index] = substitutionBox[previous[index]];
    }
    for (let index = 0; index < 4; index++) {
      expanded[word * 4 + index] = expanded[(word - keyWords) * 4 + index] ^ previous[index];
    }
  }
  return expanded;
}

function addRoundKey(state: Uint8Array, roundKeys: Uint8Array, round: number): void {
  for (let index = 0; index < BLOCK_SIZE; index++) state[index] ^= roundKeys[round * BLOCK_SIZE + index];
}

function substituteBytes(state: Uint8Array, substitutionBox: Uint8Array): void {
  for (let index = 0; index < BLOCK_SIZE; index++) state[index] = substitutionBox[state[index]];
}

/** The state is column-major, so row r of column c sits at index c * 4 + r. */
function shiftRows(state: Uint8Array): void {
  const copy = state.slice();
  for (let column = 0; column < 4; column++) {
    for (let row = 0; row < 4; row++) state[column * 4 + row] = copy[((column + row) % 4) * 4 + row];
  }
}

function mixColumns(state: Uint8Array): void {
  for (let column = 0; column < 4; column++) {
    const [a, b, c, d] = state.slice(column * 4, column * 4 + 4);
    state[column * 4] = multiplyInField(a, 2) ^ multiplyInField(b, 3) ^ c ^ d;
    state[column * 4 + 1] = a ^ multiplyInField(b, 2) ^ multiplyInField(c, 3) ^ d;
    state[column * 4 + 2] = a ^ b ^ multiplyInField(c, 2) ^ multiplyInField(d, 3);
    state[column * 4 + 3] = multiplyInField(a, 3) ^ b ^ c ^ multiplyInField(d, 2);
  }
}

function encryptBlock(block: Uint8Array, roundKeys: Uint8Array, substitutionBox: Uint8Array): Uint8Array {
  const rounds = roundKeys.length / BLOCK_SIZE - 1;
  const state = block.slice();
  addRoundKey(state, roundKeys, 0);
  for (let round = 1; round < rounds; round++) {
    substituteBytes(state, substitutionBox);
    shiftRows(state);
    mixColumns(state);
    addRoundKey(state, roundKeys, round);
  }
  substituteBytes(state, substitutionBox);
  shiftRows(state);
  addRoundKey(state, roundKeys, rounds);
  return state;
}

function incrementCounter(counter: Uint8Array): void {
  for (let index = BLOCK_SIZE - 1; index >= BLOCK_SIZE - 4; index--) {
    counter[index] = (counter[index] + 1) & 0xff;
    if (counter[index] !== 0) return;
  }
}

/** Multiplication in GF(2^128) with GCM's bit order. */
function multiplyHash(left: Uint8Array, right: Uint8Array): Uint8Array {
  const result = new Uint8Array(BLOCK_SIZE);
  const shifted = right.slice();
  for (let bit = 0; bit < 128; bit++) {
    if (left[bit >> 3] & (0x80 >> (bit & 7))) {
      for (let index = 0; index < BLOCK_SIZE; index++) result[index] ^= shifted[index];
    }
    const carry = shifted[BLOCK_SIZE - 1] & 1;
    for (let index = BLOCK_SIZE - 1; index > 0; index--) shifted[index] = (shifted[index] >> 1) | ((shifted[index - 1] & 1) << 7);
    shifted[0] >>= 1;
    if (carry) shifted[0] ^= 0xe1;
  }
  return result;
}

function ghash(hashKey: Uint8Array, ciphertext: Uint8Array): Uint8Array {
  let digest: Uint8Array = new Uint8Array(BLOCK_SIZE);
  const lengthBlock = new Uint8Array(BLOCK_SIZE);
  new DataView(lengthBlock.buffer).setUint32(12, ciphertext.length * 8);
  for (let offset = 0; offset < ciphertext.length; offset += BLOCK_SIZE) {
    const block = ciphertext.subarray(offset, offset + BLOCK_SIZE);
    for (let index = 0; index < block.length; index++) digest[index] ^= block[index];
    digest = multiplyHash(digest, hashKey);
  }
  for (let index = 0; index < BLOCK_SIZE; index++) digest[index] ^= lengthBlock[index];
  return multiplyHash(digest, hashKey);
}

function tagsMatch(expected: Uint8Array, actual: Uint8Array): boolean {
  let difference = 0;
  for (let index = 0; index < GCM_TAG_LENGTH; index++) difference |= expected[index] ^ actual[index];
  return difference === 0;
}

/**
 * Decrypts `ciphertext || 16 byte tag` (no additional data, 12 byte nonce) and checks the tag.
 * Throws when the key, nonce or data is wrong.
 */
export function decryptAesGcm(key: Uint8Array, nonce: Uint8Array, encrypted: Uint8Array): Uint8Array {
  if (key.length !== 16 && key.length !== 24 && key.length !== 32) throw new Error("AES-GCM: invalid key length");
  if (nonce.length !== GCM_NONCE_LENGTH) throw new Error("AES-GCM: only 12 byte nonces are supported");
  if (encrypted.length < GCM_TAG_LENGTH) throw new Error("AES-GCM: data shorter than the tag");

  const { substitutionBox } = tables();
  const roundKeys = expandKey(key, substitutionBox);
  const ciphertext = encrypted.subarray(0, encrypted.length - GCM_TAG_LENGTH);
  const tag = encrypted.subarray(encrypted.length - GCM_TAG_LENGTH);

  const counter = new Uint8Array(BLOCK_SIZE);
  counter.set(nonce);
  counter[BLOCK_SIZE - 1] = 1;
  const tagMask = encryptBlock(counter, roundKeys, substitutionBox);
  const hashKey = encryptBlock(new Uint8Array(BLOCK_SIZE), roundKeys, substitutionBox);
  const expectedTag = ghash(hashKey, ciphertext);
  for (let index = 0; index < BLOCK_SIZE; index++) expectedTag[index] ^= tagMask[index];
  if (!tagsMatch(expectedTag, tag)) throw new Error("AES-GCM: authentication failed");

  const plaintext = new Uint8Array(ciphertext.length);
  for (let offset = 0; offset < ciphertext.length; offset += BLOCK_SIZE) {
    incrementCounter(counter);
    const keystream = encryptBlock(counter, roundKeys, substitutionBox);
    const blockLength = Math.min(BLOCK_SIZE, ciphertext.length - offset);
    for (let index = 0; index < blockLength; index++) plaintext[offset + index] = ciphertext[offset + index] ^ keystream[index];
  }
  return plaintext;
}
