# AsyncWorldEdit 1.8 — JDK 25 fork

Fork of [SBPrime/AsyncWorldEdit](https://github.com/SBPrime/AsyncWorldEdit) (`v3.5.4`,
the last upstream version supporting Minecraft 1.8), fixed and adapted to build
with **JDK 25** and run on **MC 1.8.8** servers (Spigot / Paper / Apollo `v1_8_R3`)
on **modern Java** (tested on Temurin 25) as well as Java 8.

Fixed and adapted by **Gregoret (Kohi)**. Original plugin by **SBPrime** —
all original credit stays with him; see `license.txt`.

> On enable the server logs:
> `Fork of AsyncWorldEdit by SBPrime, fixed and adapted to work on MC 1.8 with JDK 25 by Gregoret (Kohi).`

## What works

- MC 1.8.8 (`v1_8_R3`) + WorldEdit 6.x (built against 6.1.9)
- Built with JDK 25, emits Java 8 bytecode (class v52) — runs on Java 8 through 25
- Injector, async pipeline, DirectChunkAPI adapter for `v1_8_R3`, disk-backed undo

## What was fixed vs upstream v3.5.4

- `ClassLoaderHelper` (v3.9.4 branch): `JavaPluginLoader.loaders` is a `List` on
  CraftBukkit but a `Map`/`LinkedHashMap` on Paper/Apollo forks — handled both
  (this was the `ClassCastException: LinkedHashMap cannot be cast to List` crash).
- `Reflection`: `Field.modifiers` is inaccessible on JDK 16+ — now optional
  (plain `setAccessible` still works for Bukkit/NMS/WE classes).
- `Loader.getResourceStream`: the `/res` obfuscation needs the private
  JarProcessor + seed patches (not redistributable) — community builds use
  plain resource names.
- `Loader.install`: `URLClassLoader.addURL` reflection is blocked on JDK 16+.
  Falls back to in-loader loading; start the server with
  `--add-opens java.base/java.net=ALL-UNNAMED` for the original flow.
- `Loader.loadClass`: tolerates blocked `findLoadedClass` reflection, never
  (re)defines `java.*`/`jdk.*`, parent-first delegation (single class universe,
  no duplicate-define `LinkageError`/`ClassCastException`).
- `WrappedChunk` (v1_8_R3 adapter): Minetick/Paper 1.8.8 forks removed
  `EntityTracker.a(EntityPlayer, Chunk)` — reimplemented with the fork's API.
- Missing build modules (`JarProcessor`, `spigot_v1_13_R2`, empty `patches/`)
  fixed so the reactor reads; Bukkit pinned to `1.12.2-R0.1-SNAPSHOT`.
- New `AsyncWorldEdit-Deploy18` module: 1.8.8-only fat jar
  (`target/AsyncWorldEdit.jar`) — merged API/APIInner/Utils/BlocksHub/Core/adapter,
  nested Injector. Optional PlotMe/progress integrations are skipped (tolerated).
- `Reflection.create` now prints the nested cause instead of swallowing it.

## Requirements

- JDK 25 (Microsoft/Temurin) + Maven 3.9+ to build; output runs on Java 8+
- `libs/worldedit/worldedit-{core,bukkit}-6.1.9-SNAPSHOT-dist.jar` (WorldEdit 6.1.9)
- `libs/APIs/Spigot_v1_8_R3.jar` — **your own 1.8.8 server jar** (NMS; cannot be
  downloaded publicly, copy it from your server)
- `libs/forge/fml-1.7.10-7.10.142.1104-1.7.10-universal.jar` (Forge, for Injector)
- Bukkit 1.12.2 API resolves automatically from the Spigot snapshots repo

## Build

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot'
mvn -DskipTests install -pl AsyncWorldEdit-API,AsyncWorldEditInjector,AsyncWorldEdit-APIInner,AsyncWorldEdit-Utils
mvn -DskipTests install -pl AsyncWorldEdit-BlocksHub_v1x,AsyncWorldEdit-BlocksHub_v2x,AsyncWorldEdit
mvn -DskipTests install -pl AsyncWorldEdit-spigot_v1_8_R3
mvn -DskipTests package -pl AsyncWorldEdit-Deploy18
# -> AsyncWorldEdit-Deploy18/target/AsyncWorldEdit.jar
```

## Server setup

1. WorldEdit 6.x installed.
2. Copy `AsyncWorldEdit.jar` to `plugins/`.
3. Java flags (required on Java 16+):
   `--add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED`
4. First boot installs the injector; expect `DirectChunkAPI adapter set to
   Spigot v1.8 R3` + `Enabled` with no `***` error blocks.

## Big schematics / low RAM

`//paste` of huge schematics can OOM small containers (exit 137) — use
`config-low-memory.yml` (in this repo) as `plugins/AsyncWorldEdit/config.yml`:
smaller queues (2M/1M global, 100K/50K player), flush every 3000 blocks, 3000
blocks/tick, update check off, disk undo on. Paste with `//paste -e` when you
don't need entities, and give the container RAM matching the schematic size.

## Branches

- `fix-18-jdk25` — this fork (v3.5.4 based, MC 1.8.8)
- `fix-v394-jdk25` (in `WorldUtil-master`) — v3.9.4 with JDK 25 build +
  Map-loaders fix (upstream v3.9.4 is 1.13+ only by design)

## License

Original `license.txt` applies. Source redistribution keeps the original
copyright notice and conditions.
