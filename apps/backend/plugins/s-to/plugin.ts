import { definePlugin } from "@letsmoe/otter-sdk";
import { SITE_DOMAINS } from "./src/site.ts";
import { retrieveSource } from "./src/sources.ts";
import { listStreams } from "./src/streams.ts";

export default definePlugin({
  id: "s-to",
  name: "S.to",
  version: "0.6.0",
  description: "German streaming aggregator: finds playable streams for episodes on serienstream.to (formerly s.to)",
  icon: "https://serienstream.to/favicon.ico",
  base_url: "https://serienstream.to",
  languages: ["de"],
  media: ["episode"],
  streams: { list: listStreams },
  // Play links behind the site's captcha, passed in the Otter app on play.
  sources: { domains: SITE_DOMAINS, retrieve: retrieveSource },
});
