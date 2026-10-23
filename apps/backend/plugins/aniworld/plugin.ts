import { definePlugin } from "@neoworks-dev/otter-sdk";
import { listStreams } from "./src/streams.ts";

export default definePlugin({
  id: "aniworld",
  name: "AniWorld",
  version: "0.1.0",
  description: "German anime streaming aggregator: finds playable streams for anime episodes on aniworld.to",
  icon: "https://aniworld.to/favicon.ico",
  base_url: "https://aniworld.to",
  languages: ["de"],
  media: ["episode"],
  streams: { list: listStreams },
});
