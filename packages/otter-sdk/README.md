# @neoworks-dev/otter-sdk

TypeScript SDK for Otter plugins, and the typed GraphQL client for the backend.

A plugin does one or both of two things:

- **streams**: given a movie or episode TMDB already identified, find its links on a site.
- **sources**: given a hoster link, return a playable media URL.

Plugins run inside the backend, in an embedded JS engine (goja) on the NAS and on the phone, one fresh runtime per call. Only `fetch`, `URL`, `Buffer`, `AbortSignal.timeout`, `setTimeout` and `node-html-parser` exist there: no Node built-ins, no `atob`, no `TextDecoder`. The SDK helpers are written for that.

## Writing a plugin

A plugin is two files. `plugin.ts` only defines it, so tests can import it; `index.ts` is the bundle entry the backend executes.

```ts
// plugin.ts
import { definePlugin, type PluginContext } from "@neoworks-dev/otter-sdk/plugin";
import type { StreamListParams } from "@neoworks-dev/otter-sdk";

async function listStreams({ media }: StreamListParams, ctx: PluginContext) {
  if (media.object !== "episode") return ctx.list([]);

  const search = await ctx.http.get(`/serien?q=${encodeURIComponent(media.series.title)}`);
  const root = await search.html();
  const series = ctx.match.best(
    root.querySelectorAll("a.series").map((link) => ({ title: link.text, href: link.getAttribute("href") || "" })),
    media,
  );
  if (!series) return ctx.list([]); // not on this site: an empty list, not an error

  const page = await ctx.http.get(`${series.href}/staffel-${media.season_number}/episode-${media.episode_number}`);
  const buttons = (await page.html()).querySelectorAll("button[data-play-url]");
  return ctx.list(
    buttons.map((button) => ({
      url: ctx.http.absolute(button.getAttribute("data-play-url") || ""),
      language: "de",
    })),
  );
}

export default definePlugin({
  id: "s-to",
  name: "S.to",
  version: "0.2.0",
  base_url: "https://s.to",
  languages: ["de"],
  media: ["episode"],
  streams: { list: listStreams },
});
```

```ts
// index.ts
import { runPlugin } from "@neoworks-dev/otter-sdk/plugin";
import plugin from "./plugin.ts";

runPlugin(plugin);
```

`runPlugin` also still accepts the v1 shape `{ meta, streams(args), resolve(url) }`, so unported plugins keep working.

### Definition

| Field | |
| --- | --- |
| `id`, `name`, `version` | `id` is the stable key (settings, folder); `name` is shown in the UI |
| `description?`, `icon?` | emoji or URL |
| `base_url?` | the site. The backend shows it as an editable **Domain** setting for every plugin that has one and passes the user's value as `OTTER_BASE_URL`; `ctx.http` and `ctx.plugin.base_url` use it |
| `languages?`, `media?` | `["de"]`, `["movie", "episode"]` |
| `settings?` | extra fields; values arrive in `ctx.settings` |
| `streams?` | `{ list(params, ctx) }` returning `ctx.list(items)` |
| `subtitles?` | `{ list({ media, languages }, ctx) }` returning `ctx.list(SubtitleInput[])`; `SubtitleInput` is `{ url, language (ISO 639-1), format? ("srt" default | "vtt"), label?, release?, hearing_impaired?, headers? }` |
| `sources?` | `{ domains, fallback?, retrieve(params, ctx) }`; `fallback: true` also takes any other domain |

### Objects

Every object has an `object` tag, ids are strings, fields are snake_case.

```ts
streams.list(params: { media: MovieRef | EpisodeRef }, ctx) => List<StreamInput>
// MovieRef   { object: "movie", id: "movie:603", title, original_title?, year?, tmdb_id?, imdb_id? }
// EpisodeRef { object: "episode", id: "episode:<tmdb>:<season>:<episode>", series: SeriesRef, season_number, episode_number }
// StreamInput { url, label?, quality?, language?, format?, size_bytes? }   label defaults to the hoster's name, else the domain

sources.retrieve({ url }, ctx) => SourceInput   // { url, mime_type, headers?, quality? }
```

The SDK adds `object: "stream"` / `"source"` and converts to the backend's wire format (`{ downloads }` / `ResolveResult`).

### `ctx`

- **`ctx.http`**: `get(url, options?)`, `post(url, { body | json, ... })`, `finalUrl(url)`, `absolute(path)`. Options: `headers`, `referer`, `timeout_ms` (15 s), `retries` (2, for network errors and 5xx, never 4xx), `allow_error_status`, `follow_redirects`. Responses have `.text()`, `.json()`, `.html()` (node-html-parser root), `.response()`, `.status`, `.url`. Browser User-Agent and an Accept-Language from `languages` are set. A Cloudflare challenge throws `PluginBlockedError` (or goes through FlareSolverr when `FLARESOLVERR_URL` is set); 429 throws `PluginRateLimitError`; 5xx and unreachable sites throw `PluginSourceError("source_offline")`; other 4xx throw `HttpError`.
- **`ctx.match`**: `normalize(title)` lowercases, folds umlauts (ä to ae, ß to ss) and accents, drops apostrophes, collapses punctuation. `titles(candidate, media)` compares against the title and original title (of the series for an episode), ignoring a trailing "(1999)", a leading the/der/die/das and and/und/&. `best(candidates, media, { title?, year? })` returns the candidate with an exactly matching title; the year (plus or minus one) only decides between several.
- **`ctx.extract`**: `mediaUrls(html)` (m3u8/mpd/mp4/webm, also inside packed scripts), `unpack(html)` (Dean Edwards packer), `iframes(html, pageUrl)`, `scriptJson(html, name)`, `base64(text)` (URL-safe and unpadded too), `mimeType(url, fallback?)`.
- **`ctx.hosters`**: see below. **`ctx.settings`**: declared settings by key. **`ctx.log(message, data?)`**: stderr. **`ctx.list(items)`**.

### Hosters

`@neoworks-dev/otter-sdk/hosters` holds the extractors for file hosters: VOE, Vidara, Doodstream, Filemoon, Streamtape, Vinovo, and a generic page scanner for everything else.

```ts
hosters.retrieve(url, ctx)   // the hoster serving the link, else the generic scanner
hosters.supports(url); hosters.nameOf(url); hosters.domains();

export const myHoster = defineHoster({
  name: "MyHoster",
  domains: ["myhoster.example"],
  async retrieve(url, ctx) {
    const page = await (await ctx.http.get(url)).text();
    const [media] = ctx.extract.mediaUrls(page);
    if (!media) throw new PluginSourceError("extraction_failed", `no stream on ${url}`);
    return { url: media.url, mime_type: media.mime_type, headers: { Referer: url } };
  },
});
```

A resolver plugin is `sources: { domains: hosters.domains(), fallback: true, retrieve: ({ url }, ctx) => ctx.hosters.retrieve(url, ctx) }`.

### Errors

`OtterError(type, message, { code?, status? })` and `PluginBlockedError` (`blocked`), `PluginSourceError` (`source_offline` | `extraction_failed`), `PluginRateLimitError` (`rate_limited`), `PluginDeadLinkError` (`dead_link`: the hoster says the file was deleted or never existed, so the backend drops the link; throw it only for an explicit answer about that one link, never for a failed, blocked or empty request), `HttpError`. `runPlugin` answers `{ "error": "...", "code": "blocked" }`; the backend turns the code into `*plugins.PluginError`. A title that is not on the site is an empty list, never an error.

### Testing

```ts
import { testPlugin, movie, episode, fixtures } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin, { fetch: fixtures(`${import.meta.dir}/fixtures`) });
const streams = await subject.streams.list({ media: episode({ series: "Dark", tmdb_id: 70523 }, 1, 2) });
expect(streams.data[0].url).toBe("https://voe.sx/e/abc");
```

`testPlugin` runs the handlers with a real `ctx` (pass `env` to set `OTTER_BASE_URL` or settings) and returns the completed `list`/`source` objects. `fixtures(dir)` replaces the network: a request is served from the file named after its URL, `<host>_<path>_<query>` with every other character as `_`, with or without `.html`/`.json`/`.js`/`.txt`, first with the query string, then without. `https://s.to/serie/dark?x=1` is `s.to_serie_dark_x_1.html`, then `s.to_serie_dark.html`. Non-GET requests use the same name prefixed by the lowercase method (`post.s.to_api_login`). Anything else answers 404 and logs the expected name.

### Bundling

`cd apps/backend && bun scripts/bundle-plugins.ts` bundles every plugin into one CommonJS file (`node-html-parser` stays external; the backend provides it natively).

## Data client

A fully-typed client over the backend GraphQL API, for both plugins and the frontend. Built on a [genql](https://genql.dev)-generated client (`src/generated`, regenerate with `bun run codegen`) wrapped in an ergonomic per-resource facade.

```ts
import { createOtterClient } from "@neoworks-dev/otter-sdk/client";

const otter = createOtterClient({ url: "http://localhost:8080/graphql" });

const movies = await otter.movie.list({ limit: 50 });      // search/limit/offset
const movie  = await otter.movie.get(id);                  // by id
const made   = await otter.movie.create({ tmdbId: 603, title: "The Matrix", originalTitle: "The Matrix" });
await otter.movie.update(id, { tagline: "..." });
await otter.movie.remove(id);

const seasons = await otter.season.list({ seriesId });     // child lists take their parent id
```

Every read/write returns **all scalar fields** by default. Pass a genql selection as the last argument to pick fields or pull in relations — return types narrow to match:

```ts
const slim = await otter.movie.get(id, { id: true, title: true });   // typed as { id, title } | null
```

Resources: `movie`, `series`, `season`, `episode`, `person`, `collection`, `genre`, `keyword`, `network`, `productionCompany`, `watchProvider`, `credit`, `image`, `video`. Each exposes `get`, `list`, `create`, `update`, `remove`.

For subscriptions, settings, and bespoke mutations not covered by the facade, use the raw genql client:

```ts
const { jobs } = await otter.client.query({ jobs: { id: true, status: true } });
```

Regenerate the typed client whenever the backend schema changes:

```sh
bun run codegen   # concatenates apps/backend SDL -> runs genql -> src/generated
```

## Protocol

The backend runs a plugin by writing `{ "capability": "meta" | "streams" | "resolve" | "subtitles", "args": {...} }` to its stdin and reads one line from stdout: `{ "result": ... }` or `{ "error": "...", "code"?: "blocked" | "source_offline" | "extraction_failed" | "rate_limited" | "dead_link" }`. The wire types (`PluginMeta`, `StreamsArgs`, `Download`, `ResolveResult`) in `src/types.ts` mirror `apps/backend/internal/plugins/types.go`.

## License

MIT
