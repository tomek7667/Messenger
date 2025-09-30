# Messenger (Fabric 1.21.8)

Client-side mod that **sends messages or runs `/commands` on a timer** as _you_.  
Configure everything from **ESC Messenger**.

## Features

- Add any message or `/command` with **fractional minutes** (e.g. `1.5`).
- Per-task **Enable/Disable**, **Edit**, **Delete**.
- **Sends once immediately** when enabling.
- Live **countdown** until next run.
- **Pagination** with circular Prev/Next (hidden if 1 page).
- **Persistence** across sessions.
- **Export / Import** (JSON) + **Open Folder** to manage files.
- Clean UI with labels, IDs, and status colors.

## Install

1. Install Fabric Loader **0.17.2+** for Minecraft **1.21.8**.
2. Install Fabric API **0.133.4+1.21.8**.
3. Drop `Messenger-<version>.jar` into your `mods/` folder.

## Usage

Open **ESC Messenger**.  
Top form: enter **Minutes** and **Message**, click **Add**.  
Each row shows **#index**, message, minutes, **ON/OFF**, **Edit**, **Del**, and a countdown.

**Export / Import:** Uses the config folder JSON.  
Click **Open Folder** to manage files in your OS file explorer.

## Commands

- `/messenger add <minutes> <text...>`
- `/messenger list`
- `/messenger enable <index>` `/messenger disable <index>` `/messenger toggle <index>`
- `/messenger setinterval <index> <minutes>`
- `/messenger enableall` `/messenger disableall`

## Build from source

```bash
# Windows PowerShell
.\gradlew clean build
# Dev run
.\gradlew runClient
```

Artifacts are in `build/libs/`.

## Project Metadata

- Mod ID: `messenger`
- Group: `pl.cyberman.messenger`
- Version: see **top-right** of the in-game screen (reads from `fabric.mod.json`).

## License

MIT

# Development

## Build

```bash
.\gradlew.bat clean build
```

## Running

```bash
.\gradlew.bat runClient
```

## Release

```bash
git tag v1.0.1 -m "test release"
git push origin v1.0.1
```
