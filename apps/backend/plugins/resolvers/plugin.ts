import { definePlugin, hosters } from "@neoworks-dev/otter-sdk";

// Every link the SDK hosters serve, plus any other domain through the generic page scanner.
export default definePlugin({
  id: "resolvers",
  name: "Stream Resolvers",
  version: "0.3.0",
  description: "Turns hoster links (VOE, Vidara, Doodstream, ...) into playable URLs; generic page scan for every other host",
  icon: "🔗",
  sources: {
    domains: hosters.domains(),
    fallback: true,
    retrieve: (params, ctx) => ctx.hosters.retrieve(params.url, ctx),
  },
});
