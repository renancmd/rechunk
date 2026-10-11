package dev.hytalemodding.managers;

import dev.hytalemodding.world.pos.BlockPos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SelectionManager {

    public static final int MAX_PRESETS = 20;
    public static final int MAX_PRESET_NAME_LENGTH = 24;

    public enum PresetResult { OK, EMPTY_SELECTION, INVALID_NAME, NAME_TAKEN, LIMIT_REACHED, NOT_FOUND }

    // A list of chunks that never holds the same chunk twice
    private static class ChunkList {
        // Main list for the UI (keeps the order)
        final List<BlockPos> chunks = new ArrayList<>();
        // A "Checklist" for coloring (Set is faster for checking "contains")
        final Set<BlockPos> origins = new HashSet<>();
        // The entry already listed for each chunk, to spot identical chunks
        final Map<Long, BlockPos> byChunk = new HashMap<>();

        // Returns false when that chunk was already listed
        boolean add(BlockPos pos, boolean isOrigin) {
            BlockPos existing = byChunk.putIfAbsent(chunkKey(pos), pos);
            if (existing != null) {
                // The chunk stays listed once, but keeps the origin highlight if either entry had it
                if (isOrigin) origins.add(existing);
                return false;
            }

            chunks.add(pos);
            if (isOrigin) origins.add(pos);
            return true;
        }

        BlockPos remove(int index) {
            BlockPos removed = chunks.remove(index);
            origins.remove(removed);
            byChunk.remove(chunkKey(removed));
            return removed;
        }

        void clear() {
            chunks.clear();
            origins.clear();
            byChunk.clear();
        }

        private static long chunkKey(BlockPos pos) {
            return ((long) pos.chunkX() << 32) | (pos.chunkZ() & 0xFFFFFFFFL);
        }
    }

    private static final class Preset extends ChunkList {
        String name;

        Preset(String name) {
            this.name = name;
        }
    }

    // Everything one player has selected and saved. Guarded by its own monitor, because
    // the pages and the saver thread read it from different threads.
    private static final class PlayerSelection extends ChunkList {
        // Keyed by the lower-case name, so names are unique regardless of case
        final Map<String, Preset> presets = new LinkedHashMap<>();
    }

    private static final Map<UUID, PlayerSelection> selections = new ConcurrentHashMap<>();

    // Disk writes happen here, one at a time, so they never stall the world thread
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ReChunk-SelectionSaver");
        thread.setDaemon(true);
        return thread;
    });
    private static final Set<UUID> pendingSaves = ConcurrentHashMap.newKeySet();

    // Null until the plugin starts; selections then live in memory only
    private static volatile SelectionStore store;

    public static void init(Path dataDirectory) {
        store = new SelectionStore(dataDirectory);
    }

    // Loads the player's saved selection from disk the first time it is needed
    private static PlayerSelection of(UUID playerId) {
        return selections.computeIfAbsent(playerId, id -> {
            PlayerSelection selection = new PlayerSelection();

            SelectionStore currentStore = store;
            SelectionStore.SavedSelection saved = currentStore != null ? currentStore.load(id) : null;
            if (saved != null) {
                fill(selection, saved.chunks);

                if (saved.presets != null) {
                    for (SelectionStore.SavedPreset savedPreset : saved.presets) {
                        String name = cleanName(savedPreset.name);
                        if (name == null || selection.presets.containsKey(key(name))) continue;

                        Preset preset = new Preset(name);
                        fill(preset, savedPreset.chunks);
                        selection.presets.put(key(name), preset);
                    }
                }
            }
            return selection;
        });
    }

    private static void fill(ChunkList list, List<SelectionStore.SavedChunk> savedChunks) {
        if (savedChunks == null) return;
        for (SelectionStore.SavedChunk chunk : savedChunks) {
            list.add(new BlockPos(chunk.x, chunk.z), chunk.origin);
        }
    }

    // Returns false when that chunk was already selected.
    // Does not write to disk: callers adding many chunks call save() once at the end.
    public static boolean addSelection(UUID playerId, int blockX, int blockZ, boolean isOrigin) {
        PlayerSelection selection = of(playerId);
        synchronized (selection) {
            return selection.add(new BlockPos(blockX, blockZ), isOrigin);
        }
    }

    public static List<BlockPos> getSelections(UUID playerId) {
        PlayerSelection selection = of(playerId);
        synchronized (selection) {
            return new ArrayList<>(selection.chunks);
        }
    }

    // Helper to check if a block should be colored
    public static boolean isOrigin(UUID playerId, BlockPos pos) {
        PlayerSelection selection = of(playerId);
        synchronized (selection) {
            return selection.origins.contains(pos);
        }
    }

    public static void clearSelections(UUID playerId) {
        PlayerSelection selection = of(playerId);
        synchronized (selection) {
            selection.clear();
        }
        save(playerId);
    }

    public static void removeSelection(UUID playerId, int index) {
        PlayerSelection selection = of(playerId);
        synchronized (selection) {
            if (index < 0 || index >= selection.chunks.size()) return;
            selection.remove(index);
        }
        save(playerId);
    }

    // Preset names with their chunk count, in the order they were created
    public static Map<String, Integer> getPresets(UUID playerId) {
        PlayerSelection selection = of(playerId);
        synchronized (selection) {
            Map<String, Integer> presets = new LinkedHashMap<>();
            for (Preset preset : selection.presets.values()) {
                presets.put(preset.name, preset.chunks.size());
            }
            return presets;
        }
    }

    // Stores a copy of the current selection under the given name
    public static PresetResult savePreset(UUID playerId, String rawName) {
        String name = cleanName(rawName);
        if (name == null) return PresetResult.INVALID_NAME;

        PlayerSelection selection = of(playerId);
        synchronized (selection) {
            if (selection.chunks.isEmpty()) return PresetResult.EMPTY_SELECTION;
            if (selection.presets.containsKey(key(name))) return PresetResult.NAME_TAKEN;
            if (selection.presets.size() >= MAX_PRESETS) return PresetResult.LIMIT_REACHED;

            Preset preset = new Preset(name);
            for (BlockPos pos : selection.chunks) {
                preset.add(pos, selection.origins.contains(pos));
            }
            selection.presets.put(key(name), preset);
        }
        save(playerId);
        return PresetResult.OK;
    }

    public static PresetResult renamePreset(UUID playerId, String oldName, String rawNewName) {
        String newName = cleanName(rawNewName);
        if (newName == null) return PresetResult.INVALID_NAME;
        if (oldName == null) return PresetResult.NOT_FOUND;

        PlayerSelection selection = of(playerId);
        synchronized (selection) {
            Preset preset = selection.presets.get(key(oldName));
            if (preset == null) return PresetResult.NOT_FOUND;

            // Changing only the letter case of a name is allowed
            boolean sameKey = key(oldName).equals(key(newName));
            if (!sameKey && selection.presets.containsKey(key(newName))) return PresetResult.NAME_TAKEN;

            // Rebuilt so the renamed preset keeps its place in the order
            Map<String, Preset> reordered = new LinkedHashMap<>();
            for (Map.Entry<String, Preset> entry : selection.presets.entrySet()) {
                reordered.put(entry.getValue() == preset ? key(newName) : entry.getKey(), entry.getValue());
            }
            preset.name = newName;
            selection.presets.clear();
            selection.presets.putAll(reordered);
        }
        save(playerId);
        return PresetResult.OK;
    }

    public static boolean deletePreset(UUID playerId, String name) {
        if (name == null) return false;

        PlayerSelection selection = of(playerId);
        synchronized (selection) {
            if (selection.presets.remove(key(name)) == null) return false;
        }
        save(playerId);
        return true;
    }

    // Adds the preset's chunks to the current selection, skipping the ones already selected.
    // Returns {added, already selected}, or null when the preset does not exist.
    public static int[] addPresetToSelection(UUID playerId, String name) {
        if (name == null) return null;

        int added = 0;
        int skipped = 0;
        PlayerSelection selection = of(playerId);
        synchronized (selection) {
            Preset preset = selection.presets.get(key(name));
            if (preset == null) return null;

            for (BlockPos pos : preset.chunks) {
                if (selection.add(pos, preset.origins.contains(pos))) added++;
                else skipped++;
            }
        }
        save(playerId);
        return new int[]{added, skipped};
    }

    // Queues a write of the player's current data. Several calls in a row become one write.
    public static void save(UUID playerId) {
        SelectionStore currentStore = store;
        if (currentStore == null || !pendingSaves.add(playerId)) return;

        SAVE_EXECUTOR.execute(() -> {
            pendingSaves.remove(playerId);

            SelectionStore.SavedSelection saved = new SelectionStore.SavedSelection();
            PlayerSelection selection = of(playerId);
            synchronized (selection) {
                saved.chunks = snapshot(selection);
                for (Preset preset : selection.presets.values()) {
                    saved.presets.add(new SelectionStore.SavedPreset(preset.name, snapshot(preset)));
                }
            }
            currentStore.save(playerId, saved);
        });
    }

    private static List<SelectionStore.SavedChunk> snapshot(ChunkList list) {
        List<SelectionStore.SavedChunk> savedChunks = new ArrayList<>();
        for (BlockPos pos : list.chunks) {
            savedChunks.add(new SelectionStore.SavedChunk(pos.x(), pos.z(), list.origins.contains(pos)));
        }
        return savedChunks;
    }

    // Trimmed name, or null when it is empty, too long or has control characters
    private static String cleanName(String rawName) {
        if (rawName == null) return null;

        String name = rawName.trim();
        if (name.isEmpty() || name.length() > MAX_PRESET_NAME_LENGTH) return null;
        if (name.chars().anyMatch(Character::isISOControl)) return null;
        return name;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
