import { definePlugin } from "@neoworks-dev/otter-sdk";
import { listStreams } from "./src/streams.ts";

export default definePlugin({
  id: "kinoking",
  name: "Kinoking",
  version: "0.1.1",
  description: "German movies and series: hoster links from kinoking.cc, matched by TMDB id",
  base_url: "https://kinoking.cc",
  languages: ["de"],
  media: ["movie", "episode"],
  streams: { list: listStreams },
});
