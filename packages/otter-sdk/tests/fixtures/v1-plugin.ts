import { runPlugin } from "../../src/plugin.ts";

runPlugin({
  meta: { name: "legacy", version: "1.0.0", capabilities: ["streams", "resolve"], domains: ["legacy.example"] },
  streams: async (args) => ({ downloads: [{ label: args.title, url: "https://legacy.example/" + args.tmdb_id }] }),
  resolve: async (url) => ({ url: url + "/video.mp4", mime_type: "video/mp4" }),
});
