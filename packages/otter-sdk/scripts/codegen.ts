// Concatenates the backend SDL files into one schema, then runs genql to
// generate the typed GraphQL client into src/generated.
//
// Offline by default (reads the SDL from the repo). The backend also serves the
// same SDL at GET /api/schema if you prefer introspection.

import { $ } from "bun";
import { readdirSync, readFileSync, writeFileSync } from "fs";
import { join } from "path";

const schemaDir = join(
  import.meta.dir,
  "../../../apps/backend/internal/api/graphql/schema"
);
const combinedPath = join(import.meta.dir, "../schema.graphql");

const files = readdirSync(schemaDir)
  .filter((name) => name.endsWith(".graphql"))
  .sort();

const combined = files
  .map((name) => `# ${name}\n${readFileSync(join(schemaDir, name), "utf8")}`)
  .join("\n\n");

writeFileSync(combinedPath, combined);
console.log(`Combined ${files.length} SDL files -> ${combinedPath}`);

await $`bunx @genql/cli --schema ${combinedPath} --output ${join(import.meta.dir, "../src/generated")} --esm`;
console.log("genql client generated -> src/generated");
