import { dood } from "./dood.ts";
import { filemoon } from "./filemoon.ts";
import { genericHoster } from "./generic.ts";
import { HosterRegistry } from "./registry.ts";
import { streamtape } from "./streamtape.ts";
import { vidara } from "./vidara.ts";
import { vinovo } from "./vinovo.ts";
import { voe } from "./voe.ts";

export { defineHoster, HosterRegistry, type HosterDefinition } from "./registry.ts";
export { dood, filemoon, genericHoster, streamtape, vidara, vinovo, voe };

/** The built-in hosters. Links no hoster serves go through the generic page scanner. */
export const hosters = new HosterRegistry([voe, vidara, dood, filemoon, streamtape, vinovo], genericHoster);
