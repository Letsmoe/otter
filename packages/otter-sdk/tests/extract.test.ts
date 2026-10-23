import { describe, expect, test } from "bun:test";
import { base64, extract, iframes, mediaUrls, mimeType, scriptJson } from "../src/extract.ts";
import { unpack } from "../src/packer.ts";

const WRAPPER_HEAD =
  "eval(function(p,a,c,k,e,d){e=function(c){return(c<a?'':e(parseInt(c/a)))+((c=c%a)>35?String.fromCharCode(c+29):c.toString(36))};" +
  "if(!''.replace(/^/,String)){while(c--){d[e(c)]=k[c]||e(c)}k=[function(e){return d[e]}];e=function(){return'\\\\w+'};c=1};" +
  "while(c--){if(k[c]){p=p.replace(new RegExp('\\\\b'+e(c)+'\\\\b','g'),k[c])}}return p}(";

function encodeNumber(value: number, radix: number): string {
  const remainder = value % radix;
  const prefix = value < radix ? "" : encodeNumber(Math.floor(value / radix), radix);
  return prefix + (remainder > 35 ? String.fromCharCode(remainder + 29) : remainder.toString(36));
}

/** Packs a script the way Dean Edwards' packer does. */
function pack(script: string, radix: number): string {
  const words = [...new Set(script.match(/\b\w+\b/g))];
  const payload = script.replace(/\b\w+\b/g, (word) => encodeNumber(words.indexOf(word), radix));
  const escaped = payload.replace(/\\/g, "\\\\").replace(/'/g, "\\'");
  return `${WRAPPER_HEAD}'${escaped}',${radix},${words.length},'${words.join("|")}'.split('|'),0,{}))`;
}

/** Runs the packer's own decoder function, the ground truth for `unpack`. */
function evaluate(packed: string): string {
  return new Function("eval", "return " + packed)((decoded: string) => decoded);
}

const PLAYER_SCRIPT =
  `var player = jwplayer("vplayer"); player.setup({sources:[{file:"https://cdn.example.com/hls/abc123/master.m3u8?t=9&s=1"}],` +
  `title: "It's a \\"test\\"", image: "https://cdn.example.com/poster.jpg"}); var counter = 10 + 20 + 30 + 40 + 50 + 60;`;

describe("unpack", () => {
  for (const radix of [10, 36, 62]) {
    test(`decodes a packed script in base ${radix}`, () => {
      const packed = pack(PLAYER_SCRIPT, radix);
      expect(evaluate(packed)).toBe(PLAYER_SCRIPT);
      expect(unpack(`<script>${packed}</script>`)).toBe(PLAYER_SCRIPT);
    });
  }

  test("returns undefined for pages without a packed script", () => {
    expect(unpack("<script>var a = 1;</script>")).toBeUndefined();
  });

  test("unwraps nested packers", () => {
    const inner = pack(PLAYER_SCRIPT, 36);
    expect(unpack(pack(inner, 62))).toBe(PLAYER_SCRIPT);
  });
});

describe("mediaUrls", () => {
  test("finds m3u8, mpd, mp4 and webm in plain, escaped and packed sources", () => {
    const html = `
      <video src="https://a.example/video.mp4?token=1&amp;x=2"></video>
      <script>var cfg = {"hls":"https:\\/\\/b.example\\/live\\/index.m3u8","dash":"https://c.example/m.mpd#t"};</script>
      <script>${pack('var f = "https://packed.example/p/master.m3u8?sig=abc";', 36)}</script>
      <a href="https://d.example/page.html">no</a><img src="https://d.example/poster.jpg">
      <source src="https://e.example/clip.webm">`;
    expect(mediaUrls(html)).toEqual([
      { url: "https://a.example/video.mp4?token=1&x=2", mime_type: "video/mp4" },
      { url: "https://b.example/live/index.m3u8", mime_type: "application/x-mpegURL" },
      { url: "https://c.example/m.mpd#t", mime_type: "application/dash+xml" },
      { url: "https://e.example/clip.webm", mime_type: "video/webm" },
      { url: "https://packed.example/p/master.m3u8?sig=abc", mime_type: "application/x-mpegURL" },
    ]);
  });

  test("does not mistake a longer extension for a media URL", () => {
    expect(mediaUrls('<a href="https://x.example/file.mp4.html">')).toEqual([]);
  });

  test("lists each URL once", () => {
    const html = '<a href="https://x.example/a.mp4"></a><script>"https://x.example/a.mp4"</script>';
    expect(mediaUrls(html)).toHaveLength(1);
  });
});

describe("mimeType", () => {
  test("goes by extension", () => {
    expect(mimeType("https://x/a.m3u8?x=1")).toBe("application/x-mpegURL");
    expect(mimeType("https://x/a.mpd")).toBe("application/dash+xml");
    expect(mimeType("https://x/a.webm")).toBe("video/webm");
    expect(mimeType("https://x/a.mp4")).toBe("video/mp4");
    expect(mimeType("https://x/stream", "application/x-mpegURL")).toBe("application/x-mpegURL");
  });
});

describe("base64", () => {
  test("decodes padded, unpadded and URL-safe input", () => {
    expect(base64("aGVsbG8gd29ybGQ=")).toBe("hello world");
    expect(base64("aGVsbG8gd29ybGQ")).toBe("hello world");
    const urlSafe = Buffer.from("subjects?_d>>~").toString("base64").replace(/\+/g, "-").replace(/\//g, "_");
    expect(base64(urlSafe.replace(/=+$/, ""))).toBe("subjects?_d>>~");
  });

  test("decodes UTF-8", () => {
    expect(base64(Buffer.from("Brücke ✓").toString("base64"))).toBe("Brücke ✓");
  });
});

describe("iframes", () => {
  test("returns absolute URLs and skips javascript: sources", () => {
    const html = `<iframe src="//player.example/e/1"></iframe><iframe src="/embed/2"></iframe>
      <iframe data-src="https://lazy.example/3"></iframe><iframe src="javascript:void(0)"></iframe><iframe></iframe>`;
    expect(iframes(html, "https://site.example/movie/1")).toEqual([
      "https://player.example/e/1",
      "https://site.example/embed/2",
      "https://lazy.example/3",
    ]);
  });
});

describe("scriptJson", () => {
  test("reads assigned objects and arrays, with braces inside strings", () => {
    const html = `<script>window.__DATA__ = {"a":{"b":"}{"},"list":[1,2]}; var other = 1;</script>
      <script>var episodes = [{"n":1},{"n":2}];</script>`;
    expect(scriptJson(html, "__DATA__")).toEqual({ a: { b: "}{" }, list: [1, 2] });
    expect(scriptJson(html, "episodes")).toEqual([{ n: 1 }, { n: 2 }]);
  });

  test("reads the content of a script tag with that id", () => {
    const html = '<script id="__NEXT_DATA__" type="application/json">{"props":{"x":1}}</script>';
    expect(scriptJson(html, "__NEXT_DATA__")).toEqual({ props: { x: 1 } });
  });

  test("returns undefined when missing or not JSON", () => {
    expect(scriptJson("<script>var a = 1</script>", "b")).toBeUndefined();
    expect(scriptJson("<script>var a = {x: 1};</script>", "a")).toBeUndefined();
  });

  test("is part of the extract object", () => {
    expect(extract.scriptJson("<script>var a = [1];</script>", "a")).toEqual([1]);
  });
});
