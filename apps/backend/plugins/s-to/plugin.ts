import { definePlugin } from "@neoworks-dev/otter-sdk";
import { listStreams } from "./src/streams.ts";

export default definePlugin({
  id: "s-to",
  name: "S.to",
  version: "0.4.0",
  description: "German streaming aggregator: finds playable streams for episodes on serienstream.to (formerly s.to)",
  icon: "https://serienstream.to/favicon.ico",
  base_url: "https://serienstream.to",
  languages: ["de"],
  media: ["episode"],
  streams: { list: listStreams },
});
