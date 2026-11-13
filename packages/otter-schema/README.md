# @letsmoe/otter collection

The [OpenSchema](https://openschema.neoworks.dev) definition of what Otter syncs through
Neoworks: profiles with their watch progress, My List and ratings, and the household's
settings. `otter.schema` explains the model; `openschema.yaml` is the registry manifest.

Regenerate after every schema change and commit `generated/` together with
`openschema.lock`, the ledger of every field number ever used:

```sh
openschema otter.schema --target descriptor,codec,collection --out generated
```

`openschema publish` uploads a new version once `@letsmoe` exists as a handle on
neoworks.dev.
