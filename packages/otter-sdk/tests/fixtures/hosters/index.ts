// Bundle entry that runs the hoster extractors against the site named by
// $SITE, so the backend's tests can prove they work in the embedded runtime.
import { writeSync } from "fs";
import { createContext } from "../../../src/context.ts";
import { dood, filemoon, genericHoster, streamtape, voe } from "../../../src/hosters/index.ts";
import { definePlugin } from "../../../src/plugin.ts";

const site = process.env.SITE as string;
const ctx = createContext(definePlugin({ id: "hosters", name: "Hosters", version: "1" }));

async function main() {
  const results = {
    dood: await dood.retrieve(`${site}/e/ID1`, ctx),
    streamtape: await streamtape.retrieve(`${site}/v/VID`, ctx),
    voe: await voe.retrieve(`${site}/voe/abc`, ctx),
    filemoon: await filemoon.retrieve(`${site}/fm/ID`, ctx),
    generic: await genericHoster.retrieve(`${site}/gen`, ctx),
  };
  writeSync(1, JSON.stringify({ result: results }) + "\n");
  process.exit(0);
}

main().catch((error) => {
  writeSync(1, JSON.stringify({ error: String(error.message) }) + "\n");
  process.exit(0);
});
