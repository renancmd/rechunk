# 🌍 ReChunk (Hytale World Management)

> **Note:** Formerly known as "The Chunker". ReChunk is now a complete suite containing both an in-game Mod and an External Tool.

![GitHub all releases](https://img.shields.io/github/downloads/renancmd/ReChunk/total?style=for-the-badge&color=green)
![License](https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge)
![Platform](https://img.shields.io/badge/Platform-Windows%20|%20Linux-lightgrey?style=for-the-badge)
![Language](https://img.shields.io/badge/Language-Java%20|%20Python-orange?style=for-the-badge)

**The ultimate solution to safely reset old terrain in Hytale to generate new updates (like the Zone 4 Underground Jungle) without losing your base.**

![Screenshot of ReChunk](images/screenshot.png)

## 📖 The Problem
Hytale updates often bring new biomes, mobs (like the Cave Rex), and structures. However, if you have already explored your world, new content won't load in existing chunks. To see the new features, players usually have to start a new world or travel thousands of blocks away.

## 🛠 The Solution: ReChunk Suite
**ReChunk** deletes the chunks you choose so the game generates them again with the new content, while the chunks holding your builds are left untouched.

We offer **two ways** to manage your world:

### 🤔 Which one should I use?

| Feature | 🧩 **ReChunk Mod** (Recommended) | 🛠️ **External Tool** (Classic) |
| :--- | :--- | :--- |
| **Best for...** | Precise, per-chunk control while you play. | Mass wiping, huge maps, offline maintenance. |
| **Interface** | **In-Game pages** & chat. | Desktop Window (GUI). |
| **Requirement** | Server must be **ONLINE**. | Server must be **OFFLINE**. |
| **OS** | Any (Runs on Hytale Server). | Windows & Linux. |
| **Key Feature** | Presets, confirmation step and live progress. | Fast processing of large region files. |

---

## 🧩 Option 1: ReChunk Mod (Server Plugin)
*The integrated way to refresh your world without leaving the game.*

Built and tested against Hytale server **0.6.8**.

### Features
* **In-game pages:** select chunks, pick a mode and start, all from inside Hytale.
* **Use my position:** fill the coordinates with where you are standing.
* **Buffer (0 to 9):** select the chunks around a position, up to a 19 by 19 square.
* **Two modes:**
  * **Protect selection** keeps your selected chunks and regenerates a 65 by 65 chunk area around them.
  * **Reset selection** regenerates only the selected chunks.
* **Confirmation step:** shows how many chunks will be deleted and which teleporters are affected before anything happens.
* **Live progress in chat**, as a percentage and a chunk count, with errors reported in red.
* **Cancel** a running regeneration at any time.
* **Presets:** save a selection under a name and add it back later, so you do not have to select your base again after every update.
* **Saved selections:** your selection survives leaving the world or restarting the server.
* **Teleporter cleanup:** a teleporter in a deleted chunk has its destination removed, so nobody is sent to a place with no way back.
* **Built-in manual** in English and Portuguese (Brazil).

### 🚀 How to Use (Mod)
1. **Back up your world.** A deleted chunk cannot be recovered by ReChunk. Hytale also keeps automatic backups in the world's `backup` folder.
2. **Install:** drop the `.jar` file into your `mods` folder and restart.
3. **Permissions:** you must be an Operator. In single player, run `/op self`.
4. **Open the page:** type **`/rechunk`** in chat.
5. **Select chunks:** type a block's X and Z (or press **Use my position**), choose the buffer and press **Add to selection**.
6. **Choose the mode:** *Protect selection* or *Reset selection*.
7. **Start:** press **Start**, read the confirmation and press **Regenerate**. Follow the progress in chat.

New to the mod? Run **`/rechunk manual`** for the full guide.

### ⌨️ Commands

| Command | What it does |
| :--- | :--- |
| `/rechunk` | Opens the ReChunk page. |
| `/rechunk presets` | Opens the presets page (create, rename, delete). |
| `/rechunk manual` | Opens the in-game manual. |
| `/rechunk cancel` | Stops the regeneration running in your world. |

---

## 🛠️ Option 2: ReChunk Tool (External App)
*The classic desktop experience. Best for heavy-duty map cleaning.*

### Features
* **Multi-Base Support:** Protect your main house, village, and outposts simultaneously.
* **Simulation Mode:** Dry-run to see exactly what will happen before deleting files.
* **Auto-Backup:** Automatically zips region files before deletion.
* **Cross-Platform:** Standalone `.exe` for Windows and Python source for Linux.

### 🚀 How to Use (Tool)
1.  **Backup your save folder manually.**
2.  Download the executable from the **[Releases Page](../../releases)**.
3.  **Run the tool** (`ReChunk.exe` on Windows).
4.  **Select your World** and add your base coordinates.
5.  **Set Radius:** Default is `1` (Keeps your region + 1 neighbor region buffer).
6.  **Execute:** Run a Simulation first, then uncheck "Simulation" to apply changes.

---

## 💻 For Developers (Source Code)

This repository is a **monorepo** containing both projects:
1.  **Clone the repo:**
    ```bash
    git clone https://github.com/renancmd/ReChunk.git
    cd ReChunk
    ```

### 📂 Structure
* `/external-tool` - Python source code for the Desktop App.
* `/server-plugin` - Java source code for the Hytale Mod.

### Building the Mod (Java)
You need a JDK 25 or newer and a copy of the game, because the mod compiles against the `HytaleServer.jar` that ships with it.

1. Tell Gradle where that jar is, in `~/.gradle/gradle.properties`:
    ```properties
    hytaleServerJar=/path/to/Hytale/install/release/package/game/latest/Server/HytaleServer.jar
    # Optional: lets "./gradlew deployMod" copy the jar into your mods folder
    hytaleModsDir=/path/to/Hytale/UserData/Mods
    ```
2. Build:
    ```bash
    cd server-plugin
    ./gradlew build
    # The compiled .jar will be in server-plugin/build/libs
    ```

### Building the tool (Python)
```bash
cd external-tool
pip install pyinstaller
# Build standalone exe:
pyinstaller --noconsole --onefile --icon="icon.ico" gui.py --name "ReChunkTool"
```

### ⚠️ Disclaimer

This tool modifies/deletes files from your save directory. While it includes backup features, the author is not responsible for any data loss. Always make a manual copy of your Saves folder before editing world files.

### 📄 License

This project is licensed under the GNU GPLv3 License.

**Created by Teyuz/Renan**
