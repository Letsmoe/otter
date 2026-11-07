<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/Letsmoe/otter/main/.github/assets/logo-white.svg">
    <img src="https://raw.githubusercontent.com/Letsmoe/otter/main/apps/backend/logo.svg" alt="Otter" width="320">
  </picture>
</p>

<h3 align="center">Find something to watch. Press play. That's it.</h3>

<p align="center">
  Movies and series from all over the web, in one cozy place: on your phone, your TV and your home server.
</p>

<p align="center">
  <a href="https://github.com/Letsmoe/otter/releases/latest"><img src="https://img.shields.io/github/v/release/Letsmoe/otter?style=flat-square&label=release&color=4c8bf5" alt="Latest release"></a>
  <a href="https://github.com/Letsmoe/otter/releases"><img src="https://img.shields.io/github/downloads/Letsmoe/otter/total?style=flat-square&color=4c8bf5" alt="Downloads"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/self--hosted-Docker-2496ed?style=flat-square&logo=docker&logoColor=white" alt="Self-hosted with Docker">
</p>

<p align="center">
  <a href="https://github.com/Letsmoe/otter/releases/latest"><strong>Download the Android app →</strong></a>
</p>

---

## What is Otter?

Otter is your own little movie night companion. Search for any film or show, and Otter goes off to find where it can be streamed, then lets you watch it right away. No hopping between a dozen websites, no pop-ups, no dodgy players.

It runs right on your Android phone with everything built in, or on your home server (or NAS) for the whole household.

## Features

- **Find anything.** Every movie and series out there, with posters, trailers, cast and ratings pulled in automatically.
- **Just press play.** Otter checks lots of streaming sites at once and starts the best source it finds.
- **Send it to the TV.** Play on a Chromecast or any smart TV on your Wi-Fi, with a remote right in the app.
- **Watch Together.** Start a room, pick something, and everyone follows along, with a chat to say hi.
- **Downloads.** Save movies and episodes for trains and planes. They play offline, on the phone and on the TV.
- **Recommendations that get you.** The more you watch, rate and save, the better your _"Because you watched…"_ rows get.
- **A profile for everyone.** Own history, watchlist and picks for everyone. Lock a profile with a PIN, or make it a kids profile.
- **VPN guard.** Otter won't look for or play anything until your VPN is connected (you can turn this off).
- **Skip intros.** Jump over intros and recaps and get straight to the good part.
- **New episode alerts.** Get notified when a show on your list airs a new episode or a movie becomes available.

## Getting started

### On your phone

1. Grab the APK from the [latest release](https://github.com/Letsmoe/otter/releases/latest) and install it.
2. Create a profile, pick your languages, and start exploring.

Everything runs on the phone itself, no server needed. New versions install from inside the app.

### On your home server

```sh
docker build -f apps/backend/Dockerfile -t otter .
docker run -d -p 8080:8080 -v otter-data:/data otter
```

Then enter `http://<your-server>:8080` under **App Settings → Server** in the Android app, and your library, watchlist and progress live on the server for everyone in the house.

## Growing with plugins

Every streaming site Otter knows is a small plugin. Plugins update on their own in the background, so when a site changes, Otter keeps up without a new app release.

> Want to contribute or tinker with the code? Have a look at [`CONTRIBUTING.md`](CONTRIBUTING.md).

## A note

Otter doesn't host any content itself. It simply helps you find what's already out there. Please respect the laws of your country when streaming.
