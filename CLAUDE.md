# Otter

An easy way to find and stream movies and series. One Go binary (SQLite + files on disk + embedded JS plugins) that runs on a NAS and, cross-compiled, inside the Android app. SvelteKit frontend.

## Git

You're allowed to use git. Every time you make a big change, commit the current worktree if it's dirty before changing anything, then write your changes, choose a short to-the-point commit message, and push to the remote repository. If you are unsure about what to write in the commit message, ask for help. Write an explanation of the change in the body of the commit message if it is not obvious from the title. Always mention that a commit was made by you and not an actual human. Never add a `Co-Authored-By` trailer to commits.

## Data Structure

When returning results from a fetch never return the full RecordId structure ({ Table: ..., ID: ... }) only ever return it as ...\_id: string

## Style

Neoworks uses a shared design system defined in the ui package. This package also contains some predefined components that can be reused throughout the applications. When creating new components, try to reuse existing ones as much as possible, and if you need to create new ones, follow the design system guidelines.

## Interruptions

Sometimes you might get a new prompt in the midst of creating something, if that happens, write a quick todo list that captures the work you still have open and prioritize based on what seems most important.

## Code Style

When editing or generating code, prioritize readability and maintainability over cleverness.

Rules:

- Preserve descriptive names. Do not shorten identifiers.
  - Good: `recordId`, `customerAccount`, `paymentMethod`
  - Bad: `rid`, `acct`, `pm`

- Use `camelCase` for variables, functions, parameters, and object fields unless the language, framework, or existing codebase requires another convention.
- Prefer explicit control flow over shorthand.
- Avoid `??`, ternary `?:`, and compact conditional expressions unless they clearly prevent a large amount of repetitive code without reducing readability.
- Do not deeply nest logic.
  - More than 2 indentation levels is too much.
  - Use guard clauses, early returns, helper functions, or extracted validation steps instead.

- Keep functions short and focused.
  - Split large functions into smaller functions instead of writing one huge function.
  - Each function should have one clear responsibility.

- Add comments only when the code is not immediately readable when skimming.
- Comments must be technical, concise, and useful.
  - Good: `// Normalize external IDs before database lookup.`
  - Bad: `// Now we loop through the items and do the thing.`

- Do not add obvious comments that restate the code.
- Prefer clearly named helper functions over long inline logic with comments.
- Do not change behavior, public APIs, data shapes, validation rules, or side effects unless explicitly asked.
- Match the surrounding code style when it conflicts with these rules.

## Tests

After having written all your changes, think about if any of these changes require writing a test, if so, create a test in the tests/ directory and run the `bun test` command to see if any of the tests are failing, if they do, investigate further, otherwise everything is fine and you're done.

## Stack

- **Backend**: Go (`github.com/neoworks/otter`) in `apps/backend`, GraphQL API (gqlgen)
- **Frontend**: SvelteKit in `apps/web`, talks to the backend via GraphQL only (genql client from `packages/otter-sdk`)
- **Mobile**: Capacitor Android shell in `apps/mobile`; runs the backend binary as `libotter.so` on `127.0.0.1:47832`
- **Native Android** (in progress, replaces `apps/mobile`): Kotlin + Jetpack Compose in `apps/android` (Apollo Kotlin against the backend schema, Media3 player); app id `dev.neoworks.otter.next`, backend on `127.0.0.1:47833`
- **DB**: SQLite (`modernc.org/sqlite`, pure Go, no cgo) at `$DATA_DIR/otter.db`
- **Files**: plain files under `$DATA_DIR/blobs` (stream + image cache, LRU-evicted)
- **Cache**: `kv` table in SQLite (no Redis)
- **Plugin runtime**: embedded goja JS engine, one fresh runtime per call (no Bun at runtime)

## The loop

Hardcoded in `internal/library` — there is no flow engine:

1. **TMDB**: `EnsureTitle` fetches a movie/series once (detail + credits + keywords + videos + TMDB recommendations in one request) and stores it; list results (search, trending, related, filmographies) are stored as stubs.
2. **Streaming sites**: `FindStreams` asks every `streams` plugin in parallel; each plugin's links are stored and pushed to `Subscription.streams` as they arrive. Links are resolved to playable URLs on play (`resolveStream` → `resolve` plugins → stream proxy).
3. **Related**: TMDB's recommendations become `related` edges (source `tmdb`); the director's and lead actors' filmographies become `people` edges.

`internal/recommend` ranks everything stored against a per-profile, time-decayed taste profile (ratings, watches, watchlist) and groups results into reason rows ("Because you watched X").

`internal/hooks` is the "bash tool": every executable in `$DATA_DIR/hooks` runs on every event (`title.added`, `streams.found`, `watch.started`, `watch.completed`, `rating.set`, `watchlist.added`) with `$OTTER_EVENT` set and the payload as JSON on stdin. On Android, `*.sh` files run through `/system/bin/sh`.

## Plugin system

Plugins are TypeScript packages in `apps/backend/plugins/<name>/`. `bun scripts/bundle-plugins.ts [--watch] [--out dir]` bundles each into `<out>/<folder>/plugin.js` + `package.json` (default out: `$DATA_DIR/plugins`); the backend hot-reloads bundles from `PLUGIN_DIR` via `fsnotify`.

**Capabilities** (`meta.capabilities`):

- `streams` — receives `StreamsArgs` (TMDB-known title: title, original title, year, tmdb/imdb id, season/episode numbers), locates it on the site and returns `{ downloads: Download[] }`
- `resolve` — receives a hoster URL (matched by `meta.domains`, `"*"` = fallback) and returns a playable `ResolveResult` (url, mime type, headers)

**Protocol** (stdin/stdout JSON):

```
stdin  → { "capability": "...", "args": {...} }
stdout → { "result": ... }  |  { "error": "...", "code"?: "blocked" | "source_offline" | "extraction_failed" | "rate_limited" | "dead_link" }
```

**Plugin contract:**

- Written with `definePlugin` + `runPlugin` from the SDK (`plugin.ts` defines, `index.ts` runs; see the SDK README). `runPlugin` still accepts the v1 `{ meta, streams, resolve }` shape
- Must handle `capability: "meta"` → `{ name, version, capabilities[] }` (the SDK does it from the definition). A `base_url` in the meta makes the backend offer an editable "Domain" setting, passed back as env `OTTER_BASE_URL`
- Types in `packages/otter-sdk/src/types.ts` mirror `apps/backend/internal/plugins/types.go` exactly — keep in sync
- Only `fetch`, `node-html-parser` (implemented natively in Go), `Buffer`, `URL`, `setTimeout`, `WebAssembly` (compile/instantiate of modules without imports, run by wazero with a memory cap and a 5 s limit per call) and the SDK are available — no Node built-ins beyond those, no `atob`/`TextDecoder`, no headless browsers
- Neither plugins nor frontend touch the DB directly

## Key paths

| Path                                                | Purpose                                           |
| --------------------------------------------------- | ------------------------------------------------- |
| `apps/backend/cmd/server/main.go`                   | Entry point, wires all services                   |
| `apps/backend/internal/api/graphql/schema/*.graphql` | GraphQL schema (`bun run codegen` in the SDK)    |
| `apps/backend/internal/db/migrations/`              | SQLite schema (append-only `NNN_name.sql`)        |
| `apps/backend/internal/library/`                    | The loop: TMDB → streams → related                |
| `apps/backend/internal/recommend/`                  | Recommendation engine (all weights in `weights.go`) |
| `apps/backend/internal/hooks/`                      | Event hooks (bash tool)                           |
| `apps/backend/internal/plugins/`                    | Plugin types, embedded runtime, registry          |
| `apps/mobile/android/.../EmbeddedBackend.java`      | Starts the backend on the phone                   |

## Running

```sh
bun run dev   # process-compose: plugin bundler (--watch), backend (air), web
```

Ports: backend `8080`, frontend `3000`. Data lives in `apps/backend/data`. Put `TMDB_API_KEY` in `.env` at the repo root (or set it in Settings). `DNS_SERVERS=1.1.1.1,8.8.8.8` is set because ISP resolvers block several streaming sites.

NAS: `docker build -f apps/backend/Dockerfile -t otter-backend .` from the repo root (Go binary + plugin bundles, data in the `/data` volume, hooks in `/data/hooks`).

Android: `cd apps/mobile && bun run apk` (web as static SPA + backend for `GOOS=android GOARCH=arm64` + plugin bundles as assets).

Native Android: `cd apps/android && bun run apk` (backend + plugin bundles, then `./gradlew assembleDebug`); `bun run android` installs and starts it over adb. Don't name a Kotlin package `data`: the root `.gitignore` drops every `data/` folder.

## s.to plugin (`plugins/s-to/`)

URL patterns:

- Series list: `/serien` — `li.series-item > a[href="/serie/<slug>"]`
- Series page: `/serie/<slug>` — `h1`, `span.description-text`, `a[href^="/genre/"]`, `a[data-season-pill]`
- Season pages: `/serie/<slug>/staffel-<n>` — must fetch each separately; main page only shows S1
- Episode rows: `tr.episode-row[onclick]` → `.episode-number-cell`, `.episode-title-ger`, `.episode-title-eng`
- Episode page: `/serie/<slug>/staffel-<n>/episode-<n>` — stream links as `button.link-box[data-play-url][data-provider-name][data-language-label][data-language-id]`
- Stream URL: `https://s.to/r?t=<token>` — behind Cloudflare turnstile, stored as-is
- Backdrop image: `meta[property="og:image"]`

## Code Style

When editing or generating code, prioritize readability and maintainability over cleverness.

Rules:

- Preserve descriptive names. Do not shorten identifiers.
  - Good: `recordId`, `customerAccount`, `paymentMethod`
  - Bad: `rid`, `acct`, `pm`

- Use `camelCase` for variables, functions, parameters, and object fields unless the language, framework, or existing codebase requires another convention.
- Prefer explicit control flow over shorthand.
- Avoid `??`, ternary `?:`, and compact conditional expressions unless they clearly prevent a large amount of repetitive code without reducing readability.
- Do not deeply nest logic.
  - More than 2 indentation levels is too much.
  - Use guard clauses, early returns, helper functions, or extracted validation steps instead.

- Keep functions short and focused.
  - Split large functions into smaller functions instead of writing one huge function.
  - Each function should have one clear responsibility.

- Add comments only when the code is not immediately readable when skimming.
- Comments must be technical, concise, and useful.
  - Good: `// Normalize external IDs before database lookup.`
  - Bad: `// Now we loop through the items and do the thing.`

- Do not add obvious comments that restate the code.
- Prefer clearly named helper functions over long inline logic with comments.
- Do not change behavior, public APIs, data shapes, validation rules, or side effects unless explicitly asked.
- Match the surrounding code style when it conflicts with these rules.

## Tests

After having written all your changes, think about if any of these changes require writing a test, if so, create a test in the tests/ directory and run the `bun test` command to see if any of the tests are failing, if they do, investigate further, otherwise everything is fine and you're done.
