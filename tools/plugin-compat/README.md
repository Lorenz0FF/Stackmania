# Stackmania plugin-compat

## What this is

Boot Stackmania 1.1.2+ with one plugin at a time in a Docker container, fail
the run if a `NullPointerException`, a `MohistMC.classLoader is null`, or any
other known-regression pattern surfaces in `latest.log`. This is the safety
net that ensures fixes shipped in 1.1.1 / 1.1.2 (especially the
`PlaceholderAPI` classloader fix) don't silently rot in a future refactor.

## Quick local run

```bash
cd tools/plugin-compat
docker build -t stackmania-compat .

# Test one plugin
docker run --rm stackmania-compat /server/test_plugin.sh placeholderapi

# Test all of them sequentially
for p in placeholderapi luckperms essentialsx worldedit coreprotect vault protocollib; do
    docker run --rm stackmania-compat /server/test_plugin.sh "$p" \
        || echo "::: $p failed :::"
done
```

A successful run ends with `PASS: <plugin name>` and exits `0`. A failure
ends with `FAIL: <plugin> — reason: <pattern>` and exits `1`. The full
captured `latest.log` is dumped between `===== BEGIN latest.log =====` and
`===== END latest.log =====` sentinels for grep-friendly diagnosis.

## Adding a new plugin

1. Open `plugins.yml`. Add an entry under `plugins:`:

   ```yaml
   myplugin:
     name: "MyPlugin"
     url: "https://github.com/.../MyPlugin-1.0.0.jar"
     filename: "MyPlugin-1.0.0.jar"
     pass_pattern: "\\[MyPlugin\\].*Enabled"
     extra_fail_patterns:
       - "NullPointerException.*MyPlugin"
   ```

2. Pick `pass_pattern` carefully. It's a POSIX ERE regex, fed to
   `grep -E`, and must match a line the plugin reliably logs on enable.
   Anchor it with the plugin's bracketed prefix (`\[MyPlugin\]`) to avoid
   accidental matches inside unrelated stack traces.

3. `extra_fail_patterns` is the place to encode "if this string ever
   shows up again, that's the regression we feared". The
   `MohistMC.classLoader is null` pattern is already covered globally in
   `test_plugin.sh` — only add patterns that are plugin-specific.

4. Append the new id to `.github/workflows/plugin-compat.yml` under
   `strategy.matrix.plugin`. The matrix is the only place CI learns about
   new plugins.

5. Run it locally with `docker run --rm stackmania-compat
   /server/test_plugin.sh myplugin` before pushing.

## CI

The workflow lives at
[`.github/workflows/plugin-compat.yml`](../../.github/workflows/plugin-compat.yml)
and runs on **`workflow_dispatch` only** (Actions tab → "plugin-compat" →
"Run workflow"). It accepts a `stackmania_tag` input so you can validate
a prerelease tag against the same plugin matrix before tagging it as
stable.

On failure, each matrix cell uploads its captured log under the artifact
name `plugin-compat-<plugin>-log` with 7-day retention.

## Why workflow_dispatch and not on every PR

Each matrix cell boots a full Stackmania server (~30-60 s) plus a 60 s
plugin-enable hold window plus a 30 s clean-shutdown, times 7 plugins.
Even with parallelism, that's ~10-15 min of GitHub Actions minutes per
PR — too much friction for routine commits, especially because most PRs
don't touch plugin-facing code paths.

Roadmap:

1. **Now**: manual dispatch before tagging a release.
2. **Next**: nightly cron once the matrix has been green for a release
   cycle, with Discord notification on regression.
3. **Eventually**: gate critical paths (changes to
   `MohistMC`, `MohistPlaceholderAPI`, classloader plumbing) on a
   subset of this matrix triggered by `pull_request` path filters.

## Known false positives

Filtered out automatically by `test_plugin.sh` (`IGNORE_PATTERNS`):

- `NoClassDefFoundError:
  org/embeddedt/modernfix/forge/config/NightConfigWatchThrottler$1$1`
  — ModernFix mod race during early boot. Silenced in Stackmania 1.1.2
  but the throttler's inner class can still surface before the silencer
  hook attaches. Harmless.

Filtered out by the minimal `server.properties.template`:

- "Missing forge mod" warnings — the container ships no mods, only the
  Stackmania base server. Plugins under test must enable without the
  Forge content side. This is intentional: many production TWC plugins
  must coexist with Forge mods, but the regression we're hunting (the
  Bukkit-side classloader bug) is independent of Forge mods.

## Limitations

This framework is a **smoke test for `onEnable()`**, not a functional
test. Specifically, it does **not** cover:

- **Plugin-plugin interactions** — e.g. LuckPerms + Vault is the
  combination that exercises the most permissions code paths in
  production. We test each in isolation here. Run a real staging
  server to validate combinations.
- **Player traffic** — no client connects, so packet handlers, chunk
  loading on-demand, and login flow are not exercised. The `PASS`
  verdict only confirms the plugin's main thread enable() completed.
- **Long-running drift** — we hold for 60 s, then shut down. A plugin
  that throws after 5 min of uptime will be reported PASS here.
- **Forge mod compatibility** — no mods are loaded. Use the bench
  framework in `tools/bench/` for mod-heavy scenarios.
- **Resource cost** — the JVM is capped at `-Xmx2G`, more than enough
  for a single-plugin boot but not representative of TWC's 16 GB+
  production heap. Memory regressions need their own benchmark.

For the gap between "this passes plugin-compat" and "this works on TWC",
the answer is and will remain a staging server.
