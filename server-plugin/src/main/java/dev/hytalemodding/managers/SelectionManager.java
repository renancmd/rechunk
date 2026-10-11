package dev.hytalemodding.managers;

import dev.hytalemodding.world.pos.BlockPos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SelectionManager {

    // Everything one player has selected. Guarded by its own monitor, because the page
    // and the saver thread read it from different threads.
    private static final class PlayerSelection {
        // Main list for the UI (keeps the order)
        final List<BlockPos> chunks = new ArrayList<>();
        // A "Checklist" for coloring (Set is faster for checking "contains")
        final Set<BlockPos> origins = new HashSet<>();
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
                if (saved.chunks != null) {
                    for (SelectionStore.SavedChunk chunk : saved.chunks) {
                        BlockPos pos = new BlockPos(chunk.x, chunk.z);
                        selection.chunks.add(pos);
                        if (chunk.origin) selection.origins.add(pos);
                    }
                }
            }
            return selection;
        });
    }

    // Does not write to disk: callers adding many chunks call save() once at the end
    public static void addSelection(UUID playerId, int blockX, int blockZ, boolean isOrigin) {
        BlockPos pos = new BlockPos(blockX, blockZ);
        PlayerSelection selection = of(playerId);

        synchronized (selection) {
            // Add to the main list
            selection.chunks.add(pos);

            // If it is an origin, add to the "Checklist"
            if (isOrigin) selection.origins.add(pos);
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
            selection.chunks.clear();
            selection.origins.clear();
        }
        save(playerId);
    }

    public static void removeSelection(UUID playerId, int index) {
        PlayerSelection selection = of(playerId);
        synchronized (selection) {
            if (index < 0 || index >= selection.chunks.size()) return;

            BlockPos removedPos = selection.chunks.remove(index);

            // Also remove from the checklist if it exists there
            selection.origins.remove(removedPos);
        }
        save(playerId);
    }

    // Queues a write of the player's current selection. Several calls in a row become one write.
    public static void save(UUID playerId) {
        SelectionStore currentStore = store;
        if (currentStore == null || !pendingSaves.add(playerId)) return;

        SAVE_EXECUTOR.execute(() -> {
            pendingSaves.remove(playerId);

            SelectionStore.SavedSelection saved = new SelectionStore.SavedSelection();
            PlayerSelection selection = of(playerId);
            synchronized (selection) {
                for (BlockPos pos : selection.chunks) {
                    saved.chunks.add(new SelectionStore.SavedChunk(pos.x(), pos.z(), selection.origins.contains(pos)));
                }
            }
            currentStore.save(playerId, saved);
        });
    }
}
