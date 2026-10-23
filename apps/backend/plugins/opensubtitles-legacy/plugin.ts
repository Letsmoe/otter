import { definePlugin } from "@neoworks-dev/otter-sdk";
import { listSubtitles } from "./src/subtitles.ts";

export default definePlugin({
  id: "opensubtitles-legacy",
  name: "OpenSubtitles (REST)",
  version: "0.1.0",
  description: "Subtitles in many languages from the OpenSubtitles REST API, no account needed",
  media: ["movie", "episode"],
  subtitles: { list: listSubtitles },
});
