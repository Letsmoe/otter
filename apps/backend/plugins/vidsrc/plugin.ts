import { definePlugin } from "@neoworks-dev/otter-sdk";
import { retrieveSource } from "./src/sources.ts";
import { listStreams } from "./src/streams.ts";
import { SITE_DOMAIN } from "./src/constants.ts";

export default definePlugin({
  id: "vidsrc",
  name: "VidSrc",
  version: "0.1.0",
  description: "English movies and series: HLS streams from the VidSrc player",
  base_url: "https://vidsrc.sh",
  languages: ["en"],
  media: ["movie", "episode"],
  streams: { list: listStreams },
  sources: { domains: [SITE_DOMAIN], retrieve: retrieveSource },
});
