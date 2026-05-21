<div align="center">

# 🚀 STACKMANIA

### A hybrid Minecraft server — Forge + Fabric + Bukkit on the same JVM

**By Valonia Games**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-47.4.13-blue?style=for-the-badge&logo=curseforge&logoColor=white)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)
[![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-GPL--3.0-green?style=for-the-badge)](LICENSE)

[![Download](https://img.shields.io/badge/⬇_DOWNLOAD-Latest_Release-FF6B6B?style=for-the-badge&logo=download&logoColor=white)](https://github.com/Lorenz0FF/Stackmania/releases/latest)

---

**Run Forge mods, Fabric mods (via Sinytra Connector), AND Bukkit plugins on the same server.**

</div>

---

## 🎯 What is Stackmania?

**Stackmania** is a fork of [Mohist](https://github.com/MohistMC/Mohist) for Minecraft 1.20.1, with a set of experimental optimization and compatibility modules layered on top of upstream. Built and maintained for the [The Walking Craft](https://github.com/Lorenz0FF) post-apocalyptic server (target: 200–300 concurrent players).

### Position vs other hybrid options

| Server | Forge | Fabric | Bukkit |
|---|---|---|---|
| Forge (vanilla) | ✅ | ❌ | ❌ |
| Fabric | ❌ | ✅ | ❌ |
| Paper / Spigot | ❌ | ❌ | ✅ |
| Mohist | ✅ | ❌ | ✅ |
| Arclight | ✅ | ❌ | ✅ |
| **Stackmania** | ✅ | ✅ (via [Sinytra Connector](https://github.com/Sinytra/Connector)) | ✅ |

Fabric support relies on Sinytra Connector + Forgified Fabric API, not a native Fabric loader.

---

## 🏗️ 12-Layer Architecture

Stackmania initializes 12 optional modules on top of stock Mohist. Each one can be turned off individually via `stackmania-config/stackmania.yml` to benchmark its real impact.

```
┌──────────────────────────────────────────────────────────────┐
│                    STACKMANIA 12-LAYER                       │
├──────────────────────────────────────────────────────────────┤
│  Layer 1  │ Security Manager        │ Plugin hot-load lockdown │
│  Layer 2  │ Universal Compatibility │ Conflict bookkeeping     │
│  Layer 3  │ Perfect Bukkit API      │ Material + Player cache  │
│  Layer 4  │ Perfect Registry        │ Snapshots & rollback     │
│  Layer 5  │ Zero-Crash System       │ Watchdog + recovery      │
│  Layer 6  │ Performance Perfection  │ TPS + GC monitor         │
│  Layer 7  │ Memory Manager          │ Threshold-based cleanup  │
│  Layer 8  │ Aggressive Optimizer    │ Soft caches + GC trigger │
│  Layer 9  │ Tick Optimizer          │ Pre/post-tick hooks      │
│  Layer 10 │ Universal Platform      │ Bridge layer             │
│  Layer 11 │ Fabric Fallback         │ Minimal Fabric shims     │
│  Layer 12 │ Sinytra Connector       │ Full Fabric integration  │
└──────────────────────────────────────────────────────────────┘
```

> ⚠️ **Status:** the 12 modules ship enabled by default. Their *real* effect on TPS, RAM and crash rate is currently being measured — see the bench harness on the `bench-stackmania-modules-*` branch. Treat any performance number you see in older docs as a **design target**, not a measurement.

---

## 🔌 Platform Compatibility

### Mod loaders

| Platform | Status | Method |
|---|---|---|
| Forge 1.20.1 | ✅ Native | Mohist base |
| NeoForge | ⚠️ Experimental | Compatibility shims |
| Fabric | ⚠️ Experimental | Sinytra Connector + Forgified Fabric API |
| Quilt | ⚠️ Experimental | Via Fabric bridge |

### Plugin APIs

| Platform | Status | Method |
|---|---|---|
| Bukkit | ✅ Native | CraftBukkit |
| Spigot | ✅ Native | Spigot patches |
| Paper | ⚠️ Partial | Subset of Paper API exposed |
| Sponge | ⚠️ Experimental | API translation layer |

"Experimental" = ships, builds, loads typical plugins/mods of that platform in dev testing, but **not** validated against a large public test corpus yet. Report breakages in [issues](https://github.com/Lorenz0FF/Stackmania/issues).

### Example mods folder

```
/mods/
  ├── Connector-1.0.0-beta.46+1.20.1.jar    # required for Fabric
  ├── ForgifiedFabricAPI-0.92.2+1.20.1.jar  # required for Fabric
  ├── Create-1.20.1.jar                     # Forge mod
  ├── Sodium-fabric-1.20.1.jar              # Fabric mod
  └── ...

/plugins/
  ├── EssentialsX.jar                       # Bukkit
  ├── LuckPerms.jar                         # Bukkit
  └── ...
```

---

## 📦 Installation

1. Download the latest Stackmania JAR from [Releases](https://github.com/Lorenz0FF/Stackmania/releases/latest).
2. Place it in your server directory.
3. Run:
   ```bash
   java -Xms4G -Xmx6G -jar stackmania-1.20.1-server.jar
   ```
4. Optionally edit `stackmania-config/stackmania.yml` after first boot.

### For Fabric mod support

Drop [Sinytra Connector](https://github.com/Sinytra/Connector/releases) and [Forgified Fabric API](https://github.com/Sinytra/ForgifiedFabricAPI/releases) into `/mods/`.

### Suggested JVM flags

Conservative G1GC baseline, adjust to taste:

```bash
java -Xms4G -Xmx6G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:+AlwaysPreTouch \
  -XX:G1HeapRegionSize=16M \
  -XX:+DisableExplicitGC \
  -jar stackmania-1.20.1-server.jar
```

---

## 🔧 Building from source

### Requirements

- JDK 17+ (Adoptium / Temurin)
- Gradle 8.12+ (included via `gradlew`)
- 8 GB+ RAM for the build (the Minecraft decompile step alone uses 4 GB)

### Build

```bash
git clone https://github.com/Lorenz0FF/Stackmania.git
cd Stackmania

# First time: set up the MCP pipeline + download libraries
./gradlew setup packageLibraries

# Build the server jar
./gradlew stackmaniaJar

# Output:
# projects/stackmania/build/libs/stackmania-1.20.1-<commit>-server.jar
```

On Windows: `.\gradlew.bat` instead of `./gradlew`.

---

## ⚙️ Configuration

Main config: `stackmania-config/stackmania.yml` (created on first boot).

Highlights:

```yaml
modules:
  # Each Stackmania module can be turned off here to benchmark its impact.
  tick_optimizer:         { enabled: true }
  aggressive_memory:      { enabled: true }
  zero_crash:             { enabled: true }
  fabric_compatibility:   { enabled: true }
  sinytra_bridge:         { enabled: true }
  # ... see stackmania.yml for the full list

security:
  enable_logs: true
  validate_plugin_sources: true

performance:
  watchdog_enabled: true
```

Toggling a module requires a restart. The boot log records each module's state with a `[Bench] ... DISABLED via stackmania.yml` line, which makes benchmark comparisons explicit.

---

## 📊 Performance — current state

There is no published benchmark suite yet. The 12 modules ship with explicit **design targets**:

| Module | Design target |
|---|---|
| Tick Optimizer | Hold 20 TPS under typical load |
| Aggressive Memory Optimizer | Reduce RSS under sustained load |
| Zero-Crash System | Lower crash rate vs stock Mohist |
| Performance Perfection | GC pause monitoring |

These are *goals*, not measurements. The bench harness shipping in the `bench-stackmania-modules-*` branch lets operators flip each module off and capture before/after Spark profiles to establish real numbers. PRs welcome.

---

## 🤝 Contributing

1. Fork the repository.
2. Create a feature branch: `git checkout -b feat/<topic>`.
3. Commit with a Conventional Commits-style prefix (`fix:`, `feat:`, `refactor:`, etc.).
4. Push and open a PR. Include in the description what you tested and any benchmark numbers you can share.

---

## 📜 Credits

### Based on
- [**Mohist**](https://github.com/MohistMC/Mohist) — upstream hybrid server. Stackmania pulls from Mohist's 1.20.1 line.

### Upstream projects
- [MinecraftForge](https://github.com/MinecraftForge/MinecraftForge) — mod loader
- [Bukkit](https://hub.spigotmc.org/stash/scm/spigot/bukkit.git) — plugin API
- [CraftBukkit](https://hub.spigotmc.org/stash/scm/spigot/craftbukkit.git) — plugin implementation
- [Spigot](https://hub.spigotmc.org/stash/scm/spigot/spigot.git) — performance patches
- [Paper](https://github.com/PaperMC/Paper) — extended API
- [Sinytra Connector](https://github.com/Sinytra/Connector) — Fabric-on-Forge bridge

---

## 📄 License

GPL-3.0 — see [LICENSE](LICENSE). Same license as upstream Mohist.

---

<div align="center">

**Made by Valonia Games**

</div>
