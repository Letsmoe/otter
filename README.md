<p align="center">
  <img src="apps/backend/logo.svg" alt="Otter" width="360">
</p>

<p align="center">
  <strong>Find something to watch. Press play. That's it.</strong>
</p>

<p align="center">
  Movies and series from all over the web, in one cozy place — on your home server and on your phone.
</p>

---

## What is Otter?

Otter is your own little movie night companion. Search for any film or show, and Otter goes off to find where it can be streamed, then lets you watch it right away. No hopping between a dozen websites, no pop-ups, no dodgy players.

It lives on your home server (or NAS) and on your Android phone, so your library, watchlist and progress are always with you.

## What it does

**Find anything**
Search across every movie and series out there. Posters, trailers, cast, ratings and descriptions are pulled in automatically.

**Just press play**
Otter checks lots of streaming sites at once and shows you every source it finds. Pick one and start watching.

**Recommendations that get you**
The more you watch, rate and save, the better Otter understands your taste. You'll get rows like _"Because you watched…"_ and discover more from the directors and actors you already love.

**Pick up where you left off**
Your watch history and progress are remembered, so the next episode is always just one click away.

**Watchlist**
Spotted something for later? Save it and it'll be waiting for you.

**A profile for everyone**
Everyone in the household gets their own profile, with their own recommendations and history.

**Downloads**
Save movies and episodes to watch offline, perfect for trains and planes.

**Expandable**
New streaming sites can be added as small plugins, so Otter keeps growing over time.

## Where it runs

- **Home server / NAS.** Runs quietly in the background and keeps your library in one place.
- **Browser.** Open it on your laptop or desktop and start browsing.
- **Android.** Take Otter with you. The app has everything built in, no server needed.

## The mobile app

Otter on Android is the full experience in your pocket. It works on its own, with everything built in, or connects to your home server so your library and progress follow you around.

- **Watch offline.** Download movies and episodes before a trip and watch them without a connection.
- **Send it to the TV.** Found something good? Beam it to a TV on the same Wi-Fi with a tap.
- **Watch Together.** Start a room, pick something, and everyone follows along, with a chat to say hi.
- **Skip intros.** Jump over intros and recaps automatically and get straight to the good part.
- **Lockable profiles.** Protect a profile with a 4-digit PIN, or set one up as a kids profile.
- **Made for touch.** A home screen with "Continue watching", "New episodes", "More like this" and trailers that play right in the app.

## Getting started

The easiest way is to run Otter on your home server with Docker:

```sh
docker build -f apps/backend/Dockerfile -t otter .
docker run -d -p 8080:8080 -v otter-data:/data otter
```

Then point the Otter app at your server, create a profile and start exploring.

> Want to contribute or tinker with the code? The technical details live in [`CLAUDE.md`](CLAUDE.md).

## A note

Otter doesn't host any content itself. It simply helps you find what's already out there. Please respect the laws of your country when streaming.

We recommend using a VPN while streaming. Otter can even make sure of it: turn on "Require VPN" in the settings and it won't look for or play anything unless your VPN is connected.
