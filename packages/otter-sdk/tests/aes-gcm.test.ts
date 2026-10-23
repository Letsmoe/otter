import { describe, expect, test } from "bun:test";
import { createCipheriv, randomBytes } from "node:crypto";
import { decryptAesGcm } from "../src/aes-gcm.ts";

function encrypt(key: Buffer, nonce: Buffer, plaintext: Buffer): Buffer {
  const cipher = createCipheriv(`aes-${key.length * 8}-gcm` as "aes-256-gcm", key, nonce);
  const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  return Buffer.concat([ciphertext, cipher.getAuthTag()]);
}

describe("decryptAesGcm", () => {
  test("matches the NIST AES-256 test vector", () => {
    const key = new Uint8Array(32);
    const nonce = new Uint8Array(12);
    const encrypted = Buffer.from("cea7403d4d606b6e074ec5d3baf39d18" + "d0d1c8a799996bf0265b98b5d48ab919", "hex");
    expect(Buffer.from(decryptAesGcm(key, nonce, encrypted)).toString("hex")).toBe("00000000000000000000000000000000");
  });

  test("round-trips every key size and odd lengths", () => {
    for (const keyLength of [16, 24, 32]) {
      for (const length of [0, 1, 15, 16, 17, 100, 1000]) {
        const key = randomBytes(keyLength);
        const nonce = randomBytes(12);
        const plaintext = randomBytes(length);
        const decrypted = decryptAesGcm(key, nonce, encrypt(key, nonce, plaintext));
        expect(Buffer.from(decrypted).equals(plaintext)).toBe(true);
      }
    }
  });

  test("rejects a wrong key or tampered data", () => {
    const key = randomBytes(32);
    const nonce = randomBytes(12);
    const encrypted = encrypt(key, nonce, Buffer.from("hello world"));
    expect(() => decryptAesGcm(randomBytes(32), nonce, encrypted)).toThrow("authentication failed");
    encrypted[0] ^= 1;
    expect(() => decryptAesGcm(key, nonce, encrypted)).toThrow("authentication failed");
  });
});
