# Contributing to Stackmania

Stackmania is a solo-maintained fork. Contributions are welcome but the review queue is one-deep, so this document exists to help your PR land in fewer round-trips.

---

## Build environment

| Tool | Pinned version | Why |
|---|---|---|
| **JDK** | 17 (Temurin / Adoptium) | Forge 1.20.1 baseline. Java 21 is **not** supported — bytecode targeting differences break the build. |
| **Gradle** | 8.x via wrapper | Use `./gradlew`, never your system Gradle. |
| **ForgeGradle** | `net.minecraftforge.gradle:ForgeGradle:6.0.47` | **Do not bump.** See below. |
| **OS** | Linux/macOS/Windows | All three tested. Windows builds need long-path support enabled (`git config --global core.longpaths true`). |
| **RAM during build** | 8 GB free minimum | `./gradlew setup` decompiles all of Minecraft + Forge. 16 GB is comfortable. |

### Why ForgeGradle is pinned

ForgeGradle 6.0.47 is the last version verified end-to-end on this fork. Versions tried that broke the build:

- `6.0.48` / `6.0.49`: changed how `srgify` task is registered; our `buildSrc` patches can't find the task.
- `6.1.x`: changed Minecraft remap defaults; Spigot patches no longer apply cleanly.

If you have a working bump, **bench the resulting jar through the matrix in [BENCHMARKS.md](BENCHMARKS.md) before opening a PR.** A subtle build difference can move RAM numbers by 10%+ without breaking tests.

---

## Build commands

### First-time setup

```bash
git clone https://github.com/Lorenz0FF/Stackmania.git
cd Stackmania
./gradlew setup packageLibraries
```

This step is slow (10–30 min on first run, network-bound). It decompiles Minecraft, applies Forge patches, applies Spigot patches, and stages the library jars.

### Building the server jar

```bash
./gradlew stackmaniaJar
# Output: projects/stackmania/build/libs/stackmania-1.20.1-server.jar
```

Subsequent builds are fast (< 2 min) once `setup` has been done.

### On Windows

Use `.\gradlew.bat setup packageLibraries` then `.\gradlew.bat stackmaniaJar`.

### Clean build

```bash
./gradlew clean stackmaniaJar
```

If a clean build fails where an incremental one succeeded, that is a real bug — please open an issue with the gradle output.

---

## Branching and commits

- Branch off `main` for fixes, off the active `release-prep-*` branch for features targeting the next release.
- Use the project's standard branch naming: `<type>-<short-slug>-<YYYY-MM-DD>`, e.g. `fix-papi-classloader-2026-05-21`, `feat-bench-command-2026-05-21`.
- **Use [Conventional Commits](https://www.conventionalcommits.org/)** for subject lines:
  - `feat(<scope>): ...` — new feature
  - `fix(<scope>): ...` — bug fix
  - `refactor(<scope>): ...` — refactor with no behavior change
  - `docs(<scope>): ...` — docs only
  - `test(<scope>): ...` — test changes
  - `chore(<scope>): ...` — tooling, deps, etc.
- Scopes used in this repo: `core`, `crash`, `memory`, `compat`, `perf`, `registry`, `material`, `bukkit`, `security`, `player`, `bench`, `build`, `ci`, `docs`, `papi`, `mod` (the FML entry).
- Keep subject ≤ 72 chars. Body wraps at 72.

---

## Tests

The fork is gaining JUnit coverage but is still light on tests. When you add behavior, add a test. When you fix a bug, add a regression test.

### Adding a test

Tests live in `src/test/java/com/stackmania/...` mirroring the source tree. Use JUnit 5.

Example (skeleton):

```java
package com.stackmania.memory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialCacheManagerTest {

    @Test
    void cacheIsEmptyBeforeInit() {
        // Test that the cache doesn't claim to know things it hasn't been told.
        assertFalse(MaterialCacheManager.isLive());
    }

    @Test
    void initIsIdempotent() {
        MaterialCacheManager.initialize();
        int firstSize = MaterialCacheManager.size();
        MaterialCacheManager.initialize();
        assertEquals(firstSize, MaterialCacheManager.size());
    }
}
```

Run tests with:

```bash
./gradlew test
```

Mocking the full Minecraft server is out of scope — most tests should target pure logic (config parsing, cache behavior, command-line parsing) and avoid the server lifecycle.

---

## Adding a new Stackmania module

The recommended template for a new module under `src/main/java/com/stackmania/<package>/`:

```java
package com.stackmania.example;

import com.stackmania.core.StackmaniaConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Example module — replace this Javadoc with what your module actually does.
 *
 * <p>This template enforces the two project conventions every module must follow:
 * <ol>
 *   <li>{@code StackmaniaConfig.init()} runs before any module logic reads config.
 *   <li>The {@code modules.<name>.enabled} flag is honored — if disabled, init is a no-op.
 * </ol>
 */
public final class ExampleModule {

    public static final String MODULE_NAME = "example_module";
    private static final Logger LOGGER = LogManager.getLogger(ExampleModule.class);

    private static volatile boolean initialized = false;

    private ExampleModule() { /* static-only */ }

    /**
     * Idempotent init. Safe to call from anywhere in the boot path.
     */
    public static synchronized void initialize() {
        if (initialized) return;

        if (!StackmaniaConfig.isLoaded()) {
            StackmaniaConfig.init();
        }
        if (!StackmaniaConfig.isEnabled(MODULE_NAME)) {
            LOGGER.info("[{}] disabled in config, skipping init", MODULE_NAME);
            return;
        }

        // ... module-specific initialization ...

        initialized = true;
        LOGGER.info("[{}] initialized", MODULE_NAME);
    }

    public static synchronized void shutdown() {
        if (!initialized) return;
        // ... module-specific cleanup ...
        initialized = false;
        LOGGER.info("[{}] shutdown complete", MODULE_NAME);
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
```

Then:

1. Add a layer entry to [ARCHITECTURE.md](ARCHITECTURE.md). If it has init-order dependencies, document them.
2. Add the module's `enabled` flag to the default `stackmania.yml`.
3. Add a row to the BENCHMARKS.md matrix.
4. Wire the `initialize()` call into the appropriate boot stage (probably layer 5–12). If you need a layer 1–4 slot, also update ARCHITECTURE.md's sequential-section rationale.

---

## Pull requests

- Use the [PR template](.github/PULL_REQUEST_TEMPLATE.md) (it auto-applies).
- Include the exact gradle command you ran and its result in the PR's Verification section.
- For perf-sensitive changes, include before/after numbers from `/stackmania bench dump` (matrix subset is fine — full matrix is not required for every PR).
- CI runs build + tests. PRs are not mergeable until CI is green.

---

## Reporting issues

- Bugs → [unknown bug report](.github/ISSUE_TEMPLATE/unknown-bug-report.md) (existing template).
- Plugin/mod incompat → [plugin/mod report](.github/ISSUE_TEMPLATE/plugin---mod-report.md).
- Perf regressions → [perf regression](.github/ISSUE_TEMPLATE/perf-regression.md) — please use this one, the structured fields make triage much faster.
- Questions → [question](.github/ISSUE_TEMPLATE/question.md).

---

## What changes are likely to be rejected

- Bumps to ForgeGradle without a bench run.
- New module without an `enabled` flag.
- Marketing language in README / docs ("perfect", "100%", "zero", "best"). Honest, sourced claims only.
- Adding cross-module reads to layers 5–12 without updating ARCHITECTURE.md's parallel-init constraints.
- Sweeping refactors of upstream Mohist code. The fork tries to stay rebasable on upstream — if you change a file that lives in `src/main/java/net/minecraft/` or `src/main/java/org/bukkit/` extensively, expect a long conversation.

---

## Maintainer

Lorenz0FF — solo maintainer, contact via GitHub issues. Response time varies; this is a side project.
