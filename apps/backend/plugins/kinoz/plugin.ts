import { definePlugin } from "@neoworks-dev/otter-sdk";
import { listStreams } from "./src/streams.ts";

export default definePlugin({
  id: "kinoz",
  name: "Kinoz",
  version: "0.4.0",
  description: "German streaming site: finds playable streams for movies and episodes on kinoz.to",
  icon: "https://kinoz.to/favicon.ico",
  base_url: "https://kinoz.to",
  languages: ["de"],
  media: ["movie", "episode"],
  streams: { list: listStreams },
});
