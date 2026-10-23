import { definePlugin } from "@neoworks-dev/otter-sdk";
import { listStreams } from "./src/streams.ts";

export default definePlugin({
  id: "movie2k",
  name: "Movie2k",
  version: "0.1.0",
  description: "German movies and series: finds hoster links for titles on movie2k.cx",
  base_url: "https://movie2k.cx",
  languages: ["de"],
  media: ["movie", "episode"],
  streams: { list: listStreams },
});
