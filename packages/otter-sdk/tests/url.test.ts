import { expect, test } from "bun:test";
import { originOf, resolveAgainst } from "../src/url.ts";

test("originOf keeps the port", () => {
  expect(originOf("http://127.0.0.1:8080/a?b=1")).toBe("http://127.0.0.1:8080");
  expect(originOf("https://sub.example.com/e/1")).toBe("https://sub.example.com");
  expect(() => originOf("not a url")).toThrow();
});

test("resolveAgainst follows the URL standard", () => {
  const base = "https://a.example/de/serie/page?x=1";
  expect(resolveAgainst(base, "https://b.example/x")).toBe("https://b.example/x");
  expect(resolveAgainst(base, "//cdn.example/x")).toBe("https://cdn.example/x");
  expect(resolveAgainst(base, "/root?y=2")).toBe("https://a.example/root?y=2");
  expect(resolveAgainst(base, "sibling")).toBe("https://a.example/de/serie/sibling");
  expect(resolveAgainst(base, "../up/./x.html#h")).toBe("https://a.example/de/up/x.html#h");
  expect(resolveAgainst(base, "?z=3")).toBe("https://a.example/de/serie/page?z=3");
  expect(resolveAgainst(base, "#top")).toBe("https://a.example/de/serie/page?x=1#top");
  expect(resolveAgainst("https://a.example", "x")).toBe("https://a.example/x");
  expect(resolveAgainst("https://a.example/de/", "x")).toBe("https://a.example/de/x");
});

test("resolveAgainst agrees with new URL for typical links", () => {
  const base = "http://h.example:9000/a/b/c.html";
  for (const reference of ["d.html", "../d", "/d/e", "//o.example/p", "./q", "d?x=1"]) {
    expect(resolveAgainst(base, reference)).toBe(new URL(reference, base).href);
  }
});
