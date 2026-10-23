import { describe, expect, test } from "bun:test";
import { best, normalize, titles } from "../src/match.ts";
import { episode, movie } from "../src/testing.ts";

describe("normalize", () => {
  test("folds German umlauts and eszett", () => {
    expect(normalize("Über die Brücke")).toBe("ueber die bruecke");
    expect(normalize("Das Mädchen mit dem Löwen")).toBe("das maedchen mit dem loewen");
    expect(normalize("Straße")).toBe("strasse");
  });

  test("folds other diacritics", () => {
    expect(normalize("Amélie")).toBe("amelie");
    expect(normalize("Léon: The Professional")).toBe("leon the professional");
    expect(normalize("Łódź")).toBe("lodz");
  });

  test("collapses punctuation and drops apostrophes", () => {
    expect(normalize("  Spider-Man: No Way   Home!! ")).toBe("spider man no way home");
    expect(normalize("Grey's Anatomy")).toBe("greys anatomy");
    expect(normalize("Grey’s Anatomy")).toBe("greys anatomy");
  });

  test("keeps scripts it cannot fold", () => {
    expect(normalize("千と千尋の神隠し")).toBe("千と千尋の神隠し");
  });
});

describe("titles", () => {
  const matrix = movie({ title: "Matrix", original_title: "The Matrix", year: 1999 });

  test("matches title and original title", () => {
    expect(titles("Matrix", matrix)).toBe(true);
    expect(titles("The Matrix", matrix)).toBe(true);
    expect(titles("Matrix Reloaded", matrix)).toBe(false);
  });

  test("tolerates trailing year, leading articles and ampersands", () => {
    expect(titles("Matrix (1999)", matrix)).toBe(true);
    expect(titles("Die Matrix", matrix)).toBe(true);
    const fast = movie({ title: "Fast & Furious" });
    expect(titles("Fast and Furious", fast)).toBe(true);
    expect(titles("Fast und Furious", fast)).toBe(true);
  });

  test("compares an episode against its series", () => {
    const dark = episode({ series: "Dark", original_title: "Dark" }, 1, 1);
    expect(titles("Dark", dark)).toBe(true);
    expect(titles("Dark Matter", dark)).toBe(false);
  });

  test("matches umlauts written either way", () => {
    const boot = movie({ title: "Das Boot" });
    expect(titles("Das Boot", boot)).toBe(true);
    const bruecke = movie({ title: "Die Brücke" });
    expect(titles("Die Bruecke", bruecke)).toBe(true);
  });

  test("never matches an empty title", () => {
    expect(titles("", matrix)).toBe(false);
    expect(titles("!!!", matrix)).toBe(false);
  });
});

describe("best", () => {
  const candidates = [
    { title: "Dune: Part Two", year: 2024 },
    { title: "Dune", year: 1984 },
    { title: "Dune", year: 2021 },
  ];
  const options = { year: (candidate: { year: number }) => candidate.year };

  test("exact title wins and year breaks ties", () => {
    const dune = movie({ title: "Dune", year: 2021 });
    expect(best(candidates, dune, options)).toEqual({ title: "Dune", year: 2021 });
  });

  test("a year one off is preferred over a distant one but is not required", () => {
    const dune = movie({ title: "Dune", year: 2022 });
    expect(best(candidates, dune, options)).toEqual({ title: "Dune", year: 2021 });
    const farOff = movie({ title: "Dune", year: 2010 });
    expect(best(candidates, farOff, options)).toEqual({ title: "Dune", year: 1984 });
  });

  test("returns undefined without an exact title match", () => {
    expect(best(candidates, movie({ title: "Arrival" }), options)).toBeUndefined();
  });

  test("reads titles through an accessor, and plain strings", () => {
    const matrix = movie({ title: "Matrix" });
    expect(best(["Matrix Reloaded", "Matrix"], matrix)).toBe("Matrix");
    const pages = [{ name: "Matrix" }];
    expect(best(pages, matrix, { title: (page) => page.name })).toEqual({ name: "Matrix" });
  });
});
