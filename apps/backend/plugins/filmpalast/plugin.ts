import { definePlugin } from "@neoworks-dev/otter-sdk";
import { listStreams } from "./src/streams.ts";

export default definePlugin({
  id: "filmpalast",
  name: "FilmPalast",
  version: "0.4.0",
  description: "German streaming site: finds playable streams for movies and episodes on filmpalast.to",
  icon: "https://filmpalast.to/favicon.ico",
  base_url: "https://filmpalast.to",
  languages: ["de"],
  media: ["movie", "episode"],
  streams: { list: listStreams },
});
