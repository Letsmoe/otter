# Contributing to Otter

Thanks for wanting to help! This guide covers how the project is laid out, how to run it, and how to send a change.

## How Otter is built

Otter is one Go binary that does everything: it talks to TMDB, runs the streaming-site plugins, proxies and caches streams, and serves a GraphQL API. The same binary runs on a NAS (in Docker) and, cross-compiled, inside the Android app.

- `apps/backend` is the Go backend: GraphQL API (gqlgen), SQLite (pure Go, no cgo) and the plugin runtime.
- `apps/backend/plugins` holds the streaming-site, hoster and subtitle plugins, one TypeScript package each.
- `apps/android` is the Android app: Kotlin, Jetpack Compose, Apollo and Media3.
- `apps/web` is the SvelteKit web app.
- `packages/otter-sdk` is the plugin SDK and the typed GraphQL client.

Everything is stored in SQLite plus plain files on disk. The apps only talk to the backend over GraphQL, and neither plugins nor apps touch the database.

### What happens when you open a title

1. **TMDB.** The backend fetches the movie or series once (details, credits, trailers, recommendations) and stores it.
2. **Streaming sites.** Every `streams` plugin is asked in parallel; links show up in the app as each site answers. A link is turned into a playable URL only when you press play.
3. **Subtitles.** `subtitles` plugins are asked on play, and the best file for the profile's language is cleaned up and served.
4. **Related titles.** TMDB recommendations and the director's and lead actors' filmographies become the "More like this" rows.

Recommendations are ranked against a per-profile taste profile built from ratings, watches and the watchlist (`apps/backend/internal/recommend`).

## Getting set up

You need [Bun](https://bun.sh), Go (version in `apps/backend/go.mod`), [process-compose](https://github.com/F1bonacc1/process-compose) and [air](https://github.com/air-verse/air). For the Android app you also need JDK 21 and the Android SDK.

```sh
bun install
echo "TMDB_API_KEY=<your key>" > .env   # free at themoviedb.org, or set it in the app later
bun run dev
```

`bun run dev` starts the plugin bundler (watching for changes), the backend with live reload, and the web app. The backend listens on port `8080`, the web app on `3000`. Data lives in `apps/backend/data`.

Some ISPs block streaming sites at the DNS level, which is why the backend uses `1.1.1.1` and `8.8.8.8` by default.

## Working on the backend

- **GraphQL schema:** `apps/backend/internal/api/graphql/schema/*.graphql`. After changing it, regenerate the server code with `go run github.com/99designs/gqlgen generate` in `apps/backend`, and the TypeScript client with `bun run codegen` in `packages/otter-sdk`. The Android app reads the schema file directly.
- **Database:** migrations in `apps/backend/internal/db/migrations` are append-only. Add a new `NNN_name.sql` instead of editing an old one.
- **IDs:** the API returns IDs as plain strings (`media_id`), never as nested objects.
- **Hooks:** every executable in `$DATA_DIR/hooks` runs on events like `title.added` or `watch.completed`, with the event in `$OTTER_EVENT` and the payload as JSON on stdin. Handy for automations without touching the code.

## Writing a plugin

Each streaming site is a small TypeScript package in `apps/backend/plugins/<name>/`. A plugin can:

- **find streams** (`streams`): given a title TMDB already identified, find its links on the site,
- **resolve hoster links** (`resolve`): turn a hoster page into a playable video URL,
- **find subtitles** (`subtitles`): return subtitle files in the requested languages.

The [SDK README](packages/otter-sdk/README.md) walks through writing one. Plugins run inside the backend's embedded JavaScript engine, not in Node: only `fetch`, `URL`, `Buffer`, `setTimeout`, `node-html-parser`, `WebAssembly` and the SDK are available. Headless browsers aren't.

While `bun run dev` runs, the bundler rebuilds plugins on save and the backend reloads them right away.

**Shipping a plugin update:** bump `version` in the plugin's `package.json`. CI publishes it to npm as `@letsmoe/otter-plugin-<name>`, and every Otter install picks up the new version within a few hours, without an app update.

## Working on the Android app

```sh
cd apps/android
bun run android   # build backend + plugins, install a debug build over adb, start it
bun run apk       # optimized release APK
```

The app runs the backend on the phone itself (`127.0.0.1:47833`). Don't name a Kotlin package `data`: the root `.gitignore` drops every `data/` folder.

## Tests

```sh
cd apps/backend && go test ./...   # backend
bun test                           # SDK and plugins
```

Plugin tests marked `(live)` hit the real sites, so they fail when a site is down, blocks your network or rate-limits you. That's expected; check whether a failure is new before chasing it.

## Code style

Readable over clever:

- Descriptive names (`customerAccount`, not `acct`), `camelCase` unless the language says otherwise.
- Explicit control flow. Avoid `??`, ternaries and other compact conditionals unless they save a lot of repetition.
- At most two levels of nesting. Use guard clauses, early returns and small helper functions instead.
- Short functions with one clear job.
- Comments only where the code isn't obvious at a glance, and then technical and to the point.
- Match the code around you.

## Sending a change

1. Keep a pull request to one topic, with a short title that says what changes.
2. Explain the why in the description when it isn't obvious.
3. Add or update tests where it makes sense, and make sure they pass.
4. For UI changes, a screenshot or a short clip helps a lot.

Found a bug or have an idea? [Open an issue](https://github.com/Letsmoe/otter/issues).
