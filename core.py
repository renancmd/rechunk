import os
import re
import zipfile
import json
from datetime import datetime

# CONFIGURATION & PATTERNS
REGION_PATTERN = re.compile(r"(-?\d+)\.(-?\d+)\.region\.bin")
CHUNK_SIZE = 1024  # Hytale region size in blocks

# PATHS & DIRECTORIES
def get_app_data_dir():
    """Returns the base directory in User Documents."""
    path = os.path.join(os.path.expanduser("~"), "Documents", "Hytale-The-Chunker")
    os.makedirs(path, exist_ok=True)
    return path

def get_backups_dir():
    """Returns the backups directory."""
    return path

def get_config_path():
    """Returns the path to the config file."""
    return os.path.join(get_app_data_dir(), "config.json")

def load_config():
    """Loads the user configuration."""
    config_path = get_config_path()
    if os.path.exists(config_path):
        try:
            with open(config_path, "r") as f:
                return json.load(f)
        except:
            return {}
    return {}

def save_config(data):
    """Saves the user configuration."""
    config_path = get_config_path()
    with open(config_path, "w") as f:
        json.dump(data, f, indent=4)

def get_hytale_saves_path(custom_path=None):
    """Returns the Hytale saves directory (Windows)."""
    # 1. Prefer argument
    if custom_path and os.path.isdir(custom_path):
        return custom_path

    # 2. Check config
    config = load_config()
    if "custom_saves_path" in config:
        saved_path = config["custom_saves_path"]
        if saved_path and os.path.isdir(saved_path):
            return saved_path

    # 3. Fallback to default
    appdata = os.getenv("APPDATA")
    if not appdata:
        raise RuntimeError("APPDATA not found. Are you running on Windows?")
    return os.path.join(appdata, "Hytale", "UserData", "Saves")

def list_worlds(saves_path):
    """Lists available world folders."""
    worlds = []
    if os.path.exists(saves_path):
        for name in os.listdir(saves_path):
            world_path = os.path.join(saves_path, name)
            if os.path.isdir(world_path):
                worlds.append(name)
    return worlds

def get_chunks_path(saves_path, world_name):
    """Returns the exact path to the chunks folder."""
    return os.path.join(saves_path, world_name, "universe", "worlds", "default", "chunks")

# MATH & LOGIC
def block_to_region(block_x, block_z):
    """
    Converts In-Game Block coordinates to Region File coordinates.
    """
    # Floor division (//) handles negative coordinates correctly
    rx = int(block_x) // CHUNK_SIZE
    ry = int(block_z) // CHUNK_SIZE
    return rx, ry

def load_regions(chunks_path):
    """Loads all .bin files from the directory."""
    regions = []
    if not os.path.exists(chunks_path):
        return regions

    for filename in os.listdir(chunks_path):
        match = REGION_PATTERN.fullmatch(filename)
        if match:
            regions.append({
                "x": int(match.group(1)),
                "y": int(match.group(2)),
                "filename": filename
            })
    return regions

def classify_regions(regions, bases_list, radius):
    """
    Separates regions into Protected and Reset lists.
    A region is protected if it is within the radius of ANY of the provided bases.
    """
    protected = []
    reset = []

    # Convert all user block coordinates to region coordinates first
    # bases_list format: [(x1, z1), (x2, z2), ...]
    base_regions_coords = [block_to_region(bx, bz) for bx, bz in bases_list]

    for r in regions:
        is_protected = False
        
        # Check distance against ALL bases
        for bx, by in base_regions_coords:
            # Chebyshev Distance (Square shape)
            dist_x = abs(r["x"] - bx)
            dist_y = abs(r["y"] - by)
            distance = max(dist_x, dist_y)

            if distance <= radius:
                is_protected = True
                break # It's near one base, so it's safe. No need to check others.
        
        if is_protected:
            protected.append(r)
        else:
            reset.append(r)

    return protected, reset, base_regions_coords

# FILE OPERATIONS (BACKUP & DELETE)
def backup_chunks(chunks_path, world_name):
    """Creates a compressed ZIP backup."""
    backups_root = get_backups_dir()
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    
    world_backup_dir = os.path.join(backups_root, world_name)
    os.makedirs(world_backup_dir, exist_ok=True)
    
    zip_name = f"chunks_backup_{timestamp}.zip"
    zip_path = os.path.join(world_backup_dir, zip_name)

    files_to_zip = [f for f in os.listdir(chunks_path) if REGION_PATTERN.fullmatch(f)]

    if not files_to_zip:
        return None

    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zipf:
        for filename in files_to_zip:
            file_path = os.path.join(chunks_path, filename)
            zipf.write(file_path, arcname=filename)

    return zip_path

def delete_regions(regions, chunks_path, progress_callback=None):
    """Deletes the files in the reset list."""
    total = len(regions)
    for index, r in enumerate(regions, start=1):
        file_path = os.path.join(chunks_path, r["filename"])
        try:
            os.remove(file_path)
        except OSError:
            pass 

        if progress_callback:
            progress_callback(index, total)

# MAIN EXECUTION BRIDGE
def run_processing(world_name, bases_list, radius, dry_run, do_backup, progress_callback=None, custom_saves_path=None):
    saves_path = get_hytale_saves_path(custom_saves_path)
    chunks_path = get_chunks_path(saves_path, world_name)

    regions = load_regions(chunks_path)
    if not regions:
        raise RuntimeError("No regions found in this world.")

    # Classify
    protected, reset, converted_coords = classify_regions(regions, bases_list, radius)

    if dry_run:
        return {
            "protected": len(protected),
            "reset": len(reset),
            "dry_run": True,
            "base_regions": converted_coords
        }

    # Backup
    if do_backup:
        backup_chunks(chunks_path, world_name)

    # Delete
    delete_regions(reset, chunks_path, progress_callback)

    return {
        "protected": len(protected),
        "reset": len(reset),
        "dry_run": False,
        "base_regions": converted_coords
    }