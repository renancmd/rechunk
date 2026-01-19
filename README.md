# 🦖 Hytale: The Chunker

![GitHub all releases](https://img.shields.io/github/downloads/renancmd/the-chunker/total?style=for-the-badge&color=blue)
![License](https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge)
![Platform](https://img.shields.io/badge/Platform-Windows-lightgrey?style=for-the-badge)

**Safely reset old terrain in Hytale to generate new updates (like the Zone 4 Underground Jungle) without losing your base.**

![Screenshot of The Chunker](screenshot.png)

## 📖 The Problem
Hytale just released its first update adding new mobs such as Cave Rex. However, if you have already explored your world, the new chunks won't load in areas you've visited.
To see the new content, you usually have to start a new world or walk thousands of blocks away.

## 🛠 The Solution
**The Chunker** calculates exactly which "Region Files" (1024x1024 blocks) contain your bases and deletes everything else around them. This forces the game to re-generate the terrain with the new update features while keeping your buildings safe.

### Key Features
* **Multi-Base Support:** Protect your main house, your village, and your distant outpost simultaneously.
* **Smart Protection:** Uses square radius (Chebyshev distance) to ensure corners are not cut off.
* **Auto-Backup:** Automatically zips your region files before deleting anything.
* **Simulation Mode:** Dry-run to see exactly what will happen before you commit.
* **No Python Required:** Comes as a standalone `.exe`.

---

## 🚀 How to Use (For Players)

### Prerequisites
* Windows 10/11
* **Always backup your save folder manually before using any tool.**

### Steps
1.  Download the latest `The Chunker.exe` from the **[Releases Page](../../releases)**.
2.  Run the program (Admin rights not required, but recommended).
3.  **Select your World** from the dropdown list.
4.  **Add your Bases:**
    * Go into Hytale, press `F7` **(or check your map)** to get your X and Z coordinates.
    * Enter them in the tool and click "Add Base".
    * Repeat for all locations you want to save.
5.  **Set Radius:** Default is `1`. This keeps the region your base is in, plus 1 region in every direction (Safe buffer).
6.  **Simulation:** Keep "Simulation (Dry Run)" checked and click **Execute**. Check the report.
7.  **Clean Up:** Uncheck "Simulation" and click **Execute**.
8.  Open Hytale and enjoy the new terrain!

---

## 💻 For Developers (Running from Source)

If you want to modify the code or build it yourself:

1.  **Clone the repo:**
    ```bash
    git clone https://github.com/renancmd/the-chunker.git
    cd the-chunker
    ```

2.  **Install requirements:**
    ```bash
    pip install pyinstaller
    ```
    *(Note: The tool uses standard libraries like `tkinter`, `os`, `zipfile`. PyInstaller is only for building).*

3.  **Run the GUI:**
    ```bash
    python gui.py
    ```

4.  **Build the .exe:**
    ```bash
    pyinstaller --noconsole --onefile --icon="icon.ico" --add-data "icon.ico;." gui.py --name "HytaleChunker"
    ```

---

## ⚠️ Disclaimer
This tool deletes files from your save directory. While it includes an automatic backup feature, **the author is not responsible for any data loss**. Always make a manual copy of your `Saves` folder before editing world files.

## 📄 License
This project is licensed under the **GNU GPLv3 License**.
* You are free to use, modify, and distribute this software.
* If you modify and distribute it, you **must** keep it open-source under the same license.
* **Credits to the original author must be preserved.**

---

**Created by Renan/Teyuz**
