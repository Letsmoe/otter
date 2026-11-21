import { definePlugin } from "@letsmoe/otter-sdk";
import { listFiller } from "./src/filler.ts";

export default definePlugin({
  id: "animefillerlist",
  name: "Anime Filler List",
  version: "0.1.0",
  description: "Which episodes of an anime are filler, mixed or canon, from animefillerlist.com",
  base_url: "https://www.animefillerlist.com",
  media: ["episode"],
  filler: { list: listFiller },
});
