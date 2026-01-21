import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import threading
import sys
import os
import core

def resource_path(relative_path):
    """ Get absolute path to resource, works for dev and for PyInstaller """
    try:
        base_path = sys._MEIPASS
    except Exception:
        base_path = os.path.abspath(".")

    return os.path.join(base_path, relative_path)

class HytaleChunkerGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("Hytale - The Chunker v1.0")
        self.root.geometry("450x650")
        self.root.resizable(True, True)

        try:
            self.root.iconbitmap(resource_path("icon.ico"))
        except:
            pass

        # Data storage for bases [(x, z), (x, z)]
        self.bases_data = []

        # Load Config
        self.config = core.load_config()
        self.current_saves_path = self.config.get("custom_saves_path", "")

        # Integer Validator
        self.int_validator = self.root.register(self.validate_integer)

        self.create_widgets()
        self.load_worlds()

    def validate_integer(self, value):
        if value == "" or value == "-":
            return True
        try:
            int(value)
            return True
        except ValueError:
            return False

    def create_widgets(self):
        # --- Title ---
        ttk.Label(
            self.root, 
            text="Hytale - The Chunker", 
            font=("Segoe UI", 14, "bold")
        ).pack(pady=10)

        main_frame = ttk.Frame(self.root, padding=15)
        main_frame.pack(fill="both", expand=True)

        # --- Hytale Path Selection ---
        ttk.Label(main_frame, text="Hytale Saves Path:").pack(anchor="w")
        
        path_frame = ttk.Frame(main_frame)
        path_frame.pack(fill="x", pady=(0, 10))
        
        self.path_var = tk.StringVar(value=self.current_saves_path)
        self.path_entry = ttk.Entry(path_frame, textvariable=self.path_var)
        self.path_entry.pack(side="left", fill="x", expand=True)
        
        # Bind FocusOut to reload worlds when user types manually
        self.path_entry.bind("<FocusOut>", lambda e: self.load_worlds())
        
        ttk.Button(path_frame, text="Browse", command=self.browse_path).pack(side="right", padx=(5, 0))

        # --- World Selection ---
        ttk.Label(main_frame, text="Select World:").pack(anchor="w")
        self.world_var = tk.StringVar()
        self.world_combo = ttk.Combobox(
            main_frame, 
            textvariable=self.world_var, 
            state="readonly"
        )
        self.world_combo.pack(fill="x", pady=(5, 10))

        # --- Base Manager Frame ---
        base_frame = ttk.LabelFrame(main_frame, text="Bases to Protect (Block Coords)", padding=10)
        base_frame.pack(fill="both", expand=True, pady=5)

        # Inputs
        input_frame = ttk.Frame(base_frame)
        input_frame.pack(fill="x", pady=5)

        ttk.Label(input_frame, text="X:").pack(side="left")
        self.input_x = ttk.Entry(
            input_frame, width=8, validate="key", validatecommand=(self.int_validator, "%P")
        )
        self.input_x.pack(side="left", padx=5)

        ttk.Label(input_frame, text="Z:").pack(side="left")
        self.input_z = ttk.Entry(
            input_frame, width=8, validate="key", validatecommand=(self.int_validator, "%P")
        )
        self.input_z.pack(side="left", padx=5)

        ttk.Button(input_frame, text="Add Base", command=self.add_base).pack(side="left", padx=10)

        # Listbox
        list_frame = ttk.Frame(base_frame)
        list_frame.pack(fill="both", expand=True)
        
        self.base_listbox = tk.Listbox(list_frame, height=5)
        self.base_listbox.pack(side="left", fill="both", expand=True)
        
        scrollbar = ttk.Scrollbar(list_frame, orient="vertical", command=self.base_listbox.yview)
        scrollbar.pack(side="right", fill="y")
        self.base_listbox.config(yscrollcommand=scrollbar.set)

        # Action Buttons (Import/Export/Remove)
        btn_frame = ttk.Frame(base_frame)
        btn_frame.pack(fill="x", pady=5)

        ttk.Button(btn_frame, text="Import List", command=self.import_bases).pack(side="left")
        ttk.Button(btn_frame, text="Export List", command=self.export_bases).pack(side="left", padx=5)
        ttk.Button(btn_frame, text="Remove Selected", command=self.remove_base).pack(side="right")

        # --- Radius ---
        ttk.Label(main_frame, text="Protection Radius (Chunks):").pack(anchor="w", pady=(10, 0))
        ttk.Label(main_frame, text="(Applies to ALL bases)", font=("Arial", 8), foreground="gray").pack(anchor="w")
        
        self.radius = tk.StringVar(value="1")
        ttk.Entry(
            main_frame, 
            textvariable=self.radius,
            validate="key", 
            validatecommand=(self.int_validator, "%P")
        ).pack(fill="x", pady=5)

        # --- Options ---
        self.dry_run = tk.BooleanVar(value=False)
        self.backup = tk.BooleanVar(value=True)

        ttk.Checkbutton(main_frame, text="Simulation (Dry Run)", variable=self.dry_run).pack(anchor="w")
        ttk.Checkbutton(main_frame, text="Automatic Backup", variable=self.backup).pack(anchor="w")

        # --- Progress ---
        self.progress = ttk.Progressbar(main_frame, orient="horizontal", mode="determinate")
        self.progress.pack(fill="x", pady=15)

        # --- Execute Button ---
        self.btn_execute = ttk.Button(main_frame, text="EXECUTE", command=self.start_execution)
        self.btn_execute.pack(fill="x", ipady=5)

    def browse_path(self):
        directory = filedialog.askdirectory()
        if directory:
            self.path_var.set(directory)
            self.save_path_config()
            self.load_worlds()

    def save_path_config(self):
        path = self.path_var.get().strip()
        if path and os.path.isdir(path):
            self.config["custom_saves_path"] = path
            core.save_config(self.config)

    def load_worlds(self):
        try:
            # Use path from input if available, else default (via core logic)
            custom_path = self.path_var.get().strip()
            # If empty, core.get_hytale_saves_path(None) returns default/config path
            # But we want to respect the Entry if it has something (valid or not)
            # If Entry is empty, we fetch default and populate it
            
            real_path = core.get_hytale_saves_path(custom_path if custom_path else None)
            
            # If the entry was empty and we found a path, show it
            if not custom_path and real_path:
                 self.path_var.set(real_path)

            worlds = core.list_worlds(real_path)
            self.world_combo["values"] = worlds
            
            if worlds:
                # If current selection is still valid, keep it, else select first
                current = self.world_combo.get()
                if current in worlds:
                    self.world_combo.set(current)
                else:
                    self.world_combo.current(0)
            else:
                self.world_combo.set("")
                
        except Exception as e:
            # path might be invalid/not found
            self.world_combo["values"] = []
            self.world_combo.set("")
            # Optional: print(e) or similar, but avoid popup loop on FocusOut

    def add_base(self):
        val_x = self.input_x.get()
        val_z = self.input_z.get()

        if not val_x or not val_z:
            return

        # Add to data list
        coords = (int(val_x), int(val_z))
        self.bases_data.append(coords)

        # Add to UI listbox
        self.base_listbox.insert(tk.END, f"Base: X={coords[0]}, Z={coords[1]}")
        
        # Clear inputs
        self.input_x.delete(0, tk.END)
        self.input_z.delete(0, tk.END)
        self.input_x.focus()

    def remove_base(self):
        selection = self.base_listbox.curselection()
        if not selection:
            return
        
        index = selection[0]
        self.base_listbox.delete(index)
        del self.bases_data[index]

    def import_bases(self):
        file_path = filedialog.askopenfilename(
            title="Import Coordinates",
            filetypes=[("Text Files", "*.txt"), ("All Files", "*.*")]
        )
        if not file_path:
            return

        try:
            with open(file_path, "r") as f:
                lines = f.readlines()

            count = 0
            for line in lines:
                # Regex to find X and Z. Flexible format.
                # Looks for X=<val> and Z=<val> anywhere in the line.
                import re
                match = re.search(r"X\s*=\s*(-?\d+).*Z\s*=\s*(-?\d+)", line, re.IGNORECASE)
                if match:
                    x, z = int(match.group(1)), int(match.group(2))
                    self.bases_data.append((x, z))
                    self.base_listbox.insert(tk.END, f"Base: X={x}, Z={z}")
                    count += 1
            
            messagebox.showinfo("Import", f"Successfully imported {count} bases.")
            
        except Exception as e:
            messagebox.showerror("Import Error", f"Failed to import:\n{e}")

    def export_bases(self):
        if not self.bases_data:
            messagebox.showwarning("Export", "No bases to export.")
            return

        file_path = filedialog.asksaveasfilename(
            title="Export Coordinates",
            defaultextension=".txt",
            filetypes=[("Text Files", "*.txt")]
        )
        if not file_path:
            return

        try:
            with open(file_path, "w") as f:
                for x, z in self.bases_data:
                    f.write(f"Base: X={x}, Z={z}\n")
            
            messagebox.showinfo("Export", "Export successful!")

        except Exception as e:
            messagebox.showerror("Export Error", f"Failed to export:\n{e}")

    def start_execution(self):
        # Validation
        if not self.world_var.get():
            messagebox.showwarning("Warning", "Please select a world.")
            return
        if not self.bases_data:
            messagebox.showwarning("Warning", "Please add at least one base to protect.")
            return

        # Auto-save config on execute
        self.save_path_config()

        # Lock UI
        self.btn_execute.config(state="disabled")
        self.progress["value"] = 0
        
        # Run in Thread
        thread = threading.Thread(target=self.run_logic, daemon=True)
        thread.start()

    def run_logic(self):
        try:
            # Gather data
            w_name = self.world_var.get()
            bases = self.bases_data # List of tuples
            rad = int(self.radius.get()) if self.radius.get() else 0
            is_dry = self.dry_run.get()
            do_bkp = self.backup.get()

            # Run Core
            custom_path = self.path_var.get().strip()
            result = core.run_processing(
                w_name, bases, rad, is_dry, do_bkp, 
                progress_callback=self.update_progress,
                custom_saves_path=custom_path
            )

            # Report
            base_msg = ", ".join([str(c) for c in result['base_regions']])
            msg = (
                f"Bases detected in chunks: {base_msg}\n"
                f"-----------------------------------\n"
                f"Protected Chunks: {result['protected']}\n"
                f"Reset Chunks: {result['reset']}\n"
            )
            if result['dry_run']:
                msg += "\n[SIMULATION MODE] No files were touched."
            else:
                msg += "\n[SUCCESS] World cleanup complete!"

            self.root.after(0, lambda: messagebox.showinfo("Report", msg))

        except Exception as e:
            err_msg = str(e)
            self.root.after(0, lambda: messagebox.showerror("Critical Error", err_msg))
        
        finally:
            self.root.after(0, self.reset_ui)

    def update_progress(self, current, total):
        pct = (current / total) * 100
        self.root.after(0, lambda: self.progress.configure(value=pct))

    def reset_ui(self):
        self.btn_execute.config(state="normal")

def main():
    root = tk.Tk()
    app = HytaleChunkerGUI(root)
    root.mainloop()

if __name__ == "__main__":
    main()