import core
import os
import json
import shutil

def test_config():
    print("Testing Config...")
    # Setup
    config_path = core.get_config_path()
    if os.path.exists(config_path):
        os.remove(config_path)
    
    # Test Save
    data = {"custom_saves_path": "TEST_PATH"}
    core.save_config(data)
    
    if not os.path.exists(config_path):
        print("FAIL: Config file not created")
        return
    
    # Test Load
    loaded = core.load_config()
    if loaded.get("custom_saves_path") == "TEST_PATH":
        print("PASS: Config saved and loaded correctly")
    else:
        print(f"FAIL: Loaded config mismatch: {loaded}")

    # Cleanup
    if os.path.exists(config_path):
        os.remove(config_path)

def test_path_resolution():
    print("\nTesting Path Resolution...")
    # Mock APPDATA if needed? core.py relies on os.getenv("APPDATA")
    # Let's mock a config file
    
    # Case 1: Custom Path provided
    res = core.get_hytale_saves_path("CUSTOM_ARG_PATH")
    # Note: The function checks if os.path.isdir(custom_path). So we need a real path.
    # Let's use the current directory as a dummy "custom path"
    current_dir = os.getcwd()
    res = core.get_hytale_saves_path(current_dir)
    
    if res == current_dir:
        print("PASS: Argument path respected")
    else:
        print(f"FAIL: Argument path ignored. Got: {res}")

    # Case 2: Config Path
    # Create dummy config
    core.save_config({"custom_saves_path": current_dir})
    res_config = core.get_hytale_saves_path()
    if res_config == current_dir:
        print("PASS: Config path respected")
    else:
        print(f"FAIL: Config path ignored. Got: {res_config}")

    # Cleanup
    config_path = core.get_config_path()
    if os.path.exists(config_path):
        os.remove(config_path)

if __name__ == "__main__":
    try:
        test_config()
        test_path_resolution()
    except Exception as e:
        print(f"ERROR: {e}")
