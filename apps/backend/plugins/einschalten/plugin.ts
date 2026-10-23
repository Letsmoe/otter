import { definePlugin } from "@neoworks-dev/otter-sdk";
import { listStreams } from "./src/streams.ts";

export default definePlugin({
  id: "einschalten",
  name: "Einschalten",
  version: "0.1.0",
  description: "German movies: hoster links from einschalten.in, looked up by TMDB id",
  base_url: "https://einschalten.in",
  languages: ["de"],
  media: ["movie"],
  streams: { list: listStreams },
});
