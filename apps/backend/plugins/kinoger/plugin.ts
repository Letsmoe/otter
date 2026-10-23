import { definePlugin } from "@neoworks-dev/otter-sdk";
import { listStreams } from "./src/streams.ts";
import { retrieveKinogerLink } from "./src/resolve.ts";

export default definePlugin({
  id: "kinoger",
  name: "Kinoger",
  version: "0.4.0",
  description: "German streaming aggregator: finds playable streams for movies and episodes and resolves player embeds on kinoger.ru and kinoger.be",
  icon: "https://kinoger.com/favicon.ico",
  base_url: "https://kinoger.com",
  languages: ["de"],
  media: ["movie", "episode"],
  streams: { list: listStreams },
  sources: {
    domains: ["kinoger.ru", "kinoger.be"],
    retrieve: retrieveKinogerLink,
  },
});
