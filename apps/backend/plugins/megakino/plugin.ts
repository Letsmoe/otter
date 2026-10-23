import { definePlugin } from "@neoworks-dev/otter-sdk";
import { listStreams } from "./src/streams.ts";

export default definePlugin({
  id: "megakino",
  name: "Megakino",
  version: "0.1.0",
  description: "German movies and series: finds hoster links for titles on megakino23.com",
  base_url: "https://megakino23.com",
  languages: ["de"],
  media: ["movie", "episode"],
  streams: { list: listStreams },
});
