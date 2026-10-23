import { definePlugin } from "@neoworks-dev/otter-sdk";
import { listStreams } from "./src/streams.ts";
import { retrieveSource } from "./src/retrieve.ts";

export default definePlugin({
  id: "filmo",
  name: "Filmo",
  version: "0.1.0",
  description: "German and English movies: finds hoster links for titles on filmo.to",
  base_url: "https://filmo.to",
  languages: ["de", "en"],
  media: ["movie"],
  streams: { list: listStreams },
  sources: {
    domains: ["filmo.to"],
    retrieve: retrieveSource,
  },
});
