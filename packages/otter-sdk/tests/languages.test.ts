import { describe, expect, test } from "bun:test";
import { toIso6391, toIso6392 } from "../src/languages.ts";

describe("language codes", () => {
  test("maps both three-letter variants to the two-letter code", () => {
    expect(toIso6391("ger")).toBe("de");
    expect(toIso6391("deu")).toBe("de");
    expect(toIso6391("pob")).toBe("pt");
    expect(toIso6391("GER")).toBe("de");
  });

  test("keeps a known two-letter code and drops unknown ones", () => {
    expect(toIso6391("de")).toBe("de");
    expect(toIso6391("xx")).toBeUndefined();
    expect(toIso6391("zzz")).toBeUndefined();
  });

  test("asks sources with their preferred three-letter code", () => {
    expect(toIso6392("de")).toBe("ger");
    expect(toIso6392("el")).toBe("ell");
    expect(toIso6392("xx")).toBeUndefined();
  });
});
