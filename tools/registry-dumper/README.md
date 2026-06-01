# Registry Dumper

A minimal, diagnostics-only Forge 1.20.1 mod that dumps the block-state registry tables on
both server and client sides, so you can diff them and prove (or rule out) a registry-sync
bug in a hybrid Mohist environment.

## What this is

This mod dumps two tables — `Block.BLOCK_STATE_REGISTRY` (the per-state IdMapper used by
chunk packets) and `ForgeRegistries.BLOCKS` (the parent registry) — to TSV files at three
lifecycle points (`*_pre`, `server_post`, `client_post`). Compare `server_post` against
`client_post` to localize the desync (per-state vs. per-block).

## Build

```bash
cd tools/registry-dumper
./gradlew build           # POSIX
.\gradlew.bat build       # Windows
```

The first build downloads the Forge MDK userdev artifacts (~500 MB) and runs the official
mappings remap. Expect 5–15 minutes the first time, ~30 s thereafter.

> **Note on the Gradle wrapper.** This project ships its own wrapper (`gradlew`,
> `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`)
> copied from the parent Stackmania repo (Gradle 8.12.1). If for some reason those files are
> missing, restore them with:
>
> ```bash
> cp ../../gradlew                              gradlew
> cp ../../gradlew.bat                          gradlew.bat
> cp ../../gradle/wrapper/gradle-wrapper.jar    gradle/wrapper/gradle-wrapper.jar
> cp ../../gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.properties
> chmod +x gradlew    # POSIX only
> ```

> **Do not bump ForgeGradle past 6.0.47.** The parent fork documents that 6.0.48+ broke
> end-to-end builds; this mini-mod follows the same pin. See `docs/CONTRIBUTING.md` in
> the parent repo.

## What it produces

```
build/libs/registry-dumper-1.0.0.jar
```

(plus a `-sources.jar` if you ran `./gradlew sourcesJar`).

## Deploy server-side (Pterodactyl / TWC)

```bash
# Adjust path if the volume UUID differs.
SERVER_MODS=/var/lib/pterodactyl/volumes/cf1e29e8-ed9c-429e-bc89-dea74703cb00/mods
cp build/libs/registry-dumper-1.0.0.jar "$SERVER_MODS/"
chown pterodactyl:pterodactyl "$SERVER_MODS/registry-dumper-1.0.0.jar"
chmod 644 "$SERVER_MODS/registry-dumper-1.0.0.jar"
```

## Deploy client-side

Drop the jar into the same mods folder you load on a normal client session:

- **Vanilla launcher:** `%APPDATA%\.minecraft\mods\`
- **Valonia Launcher / MultiMC / Prism:** the per-instance `mods/` folder

Both server and client need the **same** jar — a `client-pre` dump confirms the client
loaded it correctly.

## Trigger the dumps

1. **Restart the server.** When it finishes loading you should see in the console:
   ```
   [registry-dumper] dumped server_pre  -> registry-dumps/server_pre_block_state_registry.tsv ...
   [registry-dumper] dumped server_post -> registry-dumps/server_post_block_state_registry.tsv ...
   ```
   Files appear in the server's working directory (typically the volume root next to
   `world/`, not inside `mods/`).
2. **Start the client, then connect to the server.** Two events fire:
   - `client_pre` — at FML load complete (before connecting). Dumped to
     `.minecraft/registry-dumps/client_pre_*.tsv`.
   - `client_post` — after the FML handshake completes and the player is in. Dumped to
     `.minecraft/registry-dumps/client_post_*.tsv`.

## Collect

The two files that matter for the bug are:

```
<server>/registry-dumps/server_post_block_state_registry.tsv
<client>/.minecraft/registry-dumps/client_post_block_state_registry.tsv
```

`scp` / `sftp` the server one down, copy the client one out of `.minecraft/`, and put both
in a single directory.

## Compare

Use the sibling tool (Agent B's deliverable):

```bash
python tools/registry-sync/diff_registries.py \
    --server server_post_block_state_registry.tsv \
    --client client_post_block_state_registry.tsv
```

If the parent `*_blocks_registry.tsv` files are in sync but the `*_block_state_registry.tsv`
files diverge, the bug lives in per-block state-container init order (not in registry sync
itself). That's the actionable signal.

## Cleanup

When done with the diagnostic run:

```bash
# Server:
rm /var/lib/pterodactyl/volumes/.../mods/registry-dumper-1.0.0.jar
# Client:
rm "%APPDATA%/.minecraft/mods/registry-dumper-1.0.0.jar"
```

The mod is side-effect-free at runtime (no mixins, no registry mutations, no network packets),
but it has no business sitting on a production server once you have the dumps you need.
