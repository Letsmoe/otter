import { definePlugin } from "@neoworks-dev/otter-sdk";
import { retrieveSource } from "./src/sources.ts";
import { listStreams } from "./src/streams.ts";
import { STREAM_HOST } from "./src/api.ts";

export default definePlugin({
  id: "aurorascreen",
  name: "AuroraScreen",
  version: "0.1.0",
  description: "English movies and series: HLS streams from the AuroraScreen stream API",
  base_url: "https://aurorascreen.org",
  languages: ["en"],
  media: ["movie", "episode"],
  streams: { list: listStreams },
  sources: { domains: [STREAM_HOST], retrieve: retrieveSource },
});
