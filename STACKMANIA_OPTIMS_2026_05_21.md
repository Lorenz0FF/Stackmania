# Stackmania Optimizations — 2026-05-21

## Branche `optim-2026-05-21`

Cette branche apporte 3 améliorations à Stackmania :

### Commit 1 : `fix(plugins): rebrand internal plugin.yml name`

`stackmaniaplugins/src/main/resources/plugin.yml` disait encore `name: Mohist`. Maintenant : `name: Stackmania`. Visible dans `/plugins` in-game.

### Commit 2 : `merge: sync with Mohist upstream/1.20.1 (Forge 47.4.13 + 18 fixes)`

Sync avec Mohist upstream pour récupérer 18+ commits récents :
- **Forge 47.4.13** (était 47.4.10 dans la dernière release Stackmania)
- Forge 47.4.12 et 47.4.11 intermédiaires
- `Error checking for disabling coreprotect`
- Fixed #3662, #3668, #3672, #3680
- VanillaEra modpack compatibility
- Patches Language.java
- `-Xmx4G` JVM args pour la compilation (était 3G)

Conflits résolus en gardant la version Stackmania pour :
- 12 fichiers de config avec rename `mohist*` → `stackmania*`
- `Material.java` (logique anti-double-injection)
- `MohistMC.java` (12-layer init flow)
- `AbstractHurtingProjectile.java.patch` (preserve preOnHit, TACZ-aware)
- README.md, mods.toml, lang files

Conflits résolus en prenant upstream pour :
- `gradle.properties` (jvmargs)
- `build.gradle` compile options

Doublons supprimés (introduits par le merge sans ancêtre commun) :
- `mohistlauncher/` (on garde `stackmanialauncher/`)
- `mohistplugins/` (on garde `stackmaniaplugins/`)

### Commit 3 : `ci: trigger on main + optim-* branches + manual dispatch`

La CI déclenchait uniquement sur `1.20.1` (branche inexistante). Désormais :
- Push sur `1.20.1`, `main`, `optim-*` → build auto
- PR vers `1.20.1` ou `main` → build auto
- Manual trigger depuis Actions tab (`workflow_dispatch`)
- Artifacts conservés 30 jours

## Comment build et tester

### Option A : Build via GitHub Actions (recommandé)

1. Push la branche :
   ```bash
   cd /opt/Stackmania
   git push origin optim-2026-05-21
   ```
2. Aller sur https://github.com/Lorenz0FF/Stackmania/actions
3. Le workflow "Java CI with Gradle" devrait démarrer
4. Attendre ~10-20 min
5. Télécharger l'artifact `Stackmania-1.20.1-server` (contient le `.jar`)

### Option B : Build local

Le build local échoue actuellement avec :
```
java.lang.NumberFormatException: For input string: "1-snapshot-1"
  at net.minecraftforge.srgutils.MinecraftVersion.splitDots(MinecraftVersion.java:68)
  at net.minecraftforge.gradle.common.util.Utils.<clinit>(Utils.java:556)
```

C'est un bug ForgeGradle 6.x présent aussi dans Mohist upstream. La CI GitHub Actions (Ubuntu+Temurin 17) ne reproduit pas le bug — donc Option A est plus fiable.

Workarounds à essayer si on veut absolument builder en local :
- Tester avec OpenJDK Temurin 17 (pas le `default-jdk` Debian)
- Upgrade srgutils à 0.7.x dans `buildSrc/build.gradle`
- Downgrade ForgeGradle à `6.0.13` dans `build.gradle` plugins block

## Comment déployer sur The Walking Craft (TWC)

Une fois le jar buildé :

1. **Backup TWC** (déjà fait : `Manuel-20260521-130454.tar.gz` = 3.22 GB)
2. **Stop le serveur** via Pterodactyl panel
3. **SFTP/Wings** : remplacer `/var/lib/pterodactyl/volumes/cf1e29e8-ed9c-429e-bc89-dea74703cb00/stackmania-1.20.1-1.1.0.jar` par le nouveau jar
4. **Vérifier le nom** : le startup command Pterodactyl pointe vers ce nom de jar (peut nécessiter rename ou update du command)
5. **Start le serveur** + monitorer les logs

À vérifier dans les logs au boot :
- `Forge version 47.4.13` (vs 47.4.10 actuellement)
- `Mohist version 1.20.1-XXXXXXX` avec un commit hash réel (pas `00000000`)
- Tous les plugins chargent : LuckPerms, Vault, EssentialsX, etc.
- Tous les mods chargent : TACZ, KubeJS, etc.
- KubeJS scripts loaded 5/5

## Plan d'optims futures (au-delà de cette session)

### Court terme

- **Bench** : avant/après ce merge, mesurer RAM, TPS, boot time avec Spark profiler
- **Test PAPI** : vérifier si PlaceholderAPI charge enfin avec Forge 47.4.13 et les fixes ColorAPI / classloader. Si oui → débloque OneAC, TAB, Spartan
- **Test plugins ProtectionLib + TCPShield + CoreProtect** : valider qu'ils tournent avec le nouveau core

### Moyen terme

- **Benchmark les 27 classes `com.stackmania.*`** :
  - `ZeroCrashSystem` est-il vraiment utile ? Mesurer crash rate avant/après désactivation
  - `AggressiveMemoryOptimizer` réduit-il vraiment 45% RAM ? Mesurer
  - `PerformancePerfection` impacte-t-il les TPS ?
  - Stub vs vraie implémentation pour chaque classe
- **README honnête** : remplacer claims marketing par benchmarks réels mesurés

### Long terme

- **Décision sharding** (cf doc 11) : si on vise 200-300 CCU, il faut Velocity + 3 backends + Proxy Compatible Forge
- **Hardware** : Xeon E5 v4 (2016) = mur pour MC modded. Considérer OVH Game range (Ryzen 7950X3D)
- **Patcher le bug ForgeGradle local** pour autosuffisance de build sans CI

## Commits sur cette branche

```
8a42e92bb ci: trigger on main + optim-* branches + manual dispatch
4b5bbf626 merge: sync with Mohist upstream/1.20.1 (Forge 47.4.13 + 18 fixes)
2a5e6cd9a fix(plugins): rebrand internal plugin.yml name from Mohist to Stackmania
```

Tous merge-able sur `main` via Pull Request quand validés en build.
