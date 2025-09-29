# Messenger (Fabric 1.21.8)

Client-side mod that **sends messages or runs commands on a timer** as _you_ (no server-side plugin).
Configure from the **ESC Messenger** screen.

## Features

- Add any message or `/command` with **fractional minutes** (e.g. `1.5`).
- Per-task **Enable/Disable**, **Edit**, **Delete**.
- Sends **once immediately** when enabling.
- Live **countdown** until next run.
- **Pagination** for large lists.
- **Persisted** across sessions.
- Quick **Export / Import** (JSON) and **Open Folder** button.

## UI Tips

- Top form set **Minutes** and **Message**, click **Add**.
- Each row shows **#index**, message, minutes, **ON/OFF**, **Edit**, **Del**, and a countdown.
- Export/Import use the config folder; click **Open Folder** to manage JSON files.

## Commands

- `/messenger add <minutes> <text...>`
- `/messenger list`
- `/messenger enable <index>` • `/messenger disable <index>` • `/messenger toggle <index>`
- `/messenger setinterval <index> <minutes>`
- `/messenger enableall` • `/messenger disableall`

## Requirements

- Minecraft **1.21.8**
- Fabric Loader **0.17.2+**
- Fabric API **0.133.4+1.21.8**

## License

MIT
