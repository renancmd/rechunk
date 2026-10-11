package dev.hytalemodding.managers;

import com.hypixel.hytale.builtin.adventure.teleporter.component.Teleporter;
import com.hypixel.hytale.builtin.teleport.TeleportPlugin;
import com.hypixel.hytale.builtin.teleport.Warp;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.interaction.blocktrack.BlockCounter;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * The server only forgets a teleporter when its block is broken. Chunks deleted by
 * ReChunk never go through that path, so the teleporter's warp and its placement
 * count would stay behind and point to a block that no longer exists.
 */
public class TeleporterCleanup {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Key the server uses in BlockCounter for placed teleporters
    private static final String TELEPORTER_BLOCK_ID = "Teleporter";

    /**
     * Removes the warps owned by teleporters that stood in the given chunks.
     * Must run on the world thread. Returns the names of the removed warps.
     */
    public static List<String> removeTeleporterWarps(World world, Set<Long> chunkIndexes) {
        List<String> removed = new ArrayList<>();

        try {
            TeleportPlugin teleportPlugin = TeleportPlugin.get();
            if (teleportPlugin == null || !teleportPlugin.isWarpsLoaded()) return removed;

            // Copy: removeWarp changes the map being read
            for (Warp warp : new ArrayList<>(teleportPlugin.getWarps().values())) {
                // Warps made with "/warp set" are just positions and stay valid after a reset
                if (!Teleporter.CREATOR_IDENTIFIER.equals(warp.getCreator())) continue;
                if (!world.getName().equals(warp.getWorld())) continue;

                // A teleporter's warp sits in the same block column as the teleporter itself
                Vector3d position = warp.getTransform().getPosition();
                long chunkIndex = ChunkUtil.indexChunkFromBlock(position.x(), position.z());
                if (!chunkIndexes.contains(chunkIndex)) continue;

                if (teleportPlugin.removeWarp(warp.getId())) {
                    removed.add(warp.getId());
                }
            }
        } catch (Exception | LinkageError e) {
            // LinkageError: the built-in teleport plugin is missing or changed in this server version
            LOGGER.at(Level.WARNING).withCause(e).log("Could not clean up teleporter warps");
        }

        releasePlacementCount(world, removed.size());
        return removed;
    }

    // Gives back the placement slots of the deleted teleporters, as breaking the blocks would
    private static void releasePlacementCount(World world, int teleporters) {
        if (teleporters == 0) return;

        try {
            BlockCounter counter = world.getChunkStore().getStore().getResource(BlockCounter.getResourceType());
            if (counter == null) return;

            // untrackBlock does not stop at zero, so the count is checked first
            for (int i = 0; i < teleporters && counter.getBlockPlacementCount(TELEPORTER_BLOCK_ID) > 0; i++) {
                counter.untrackBlock(TELEPORTER_BLOCK_ID);
            }
        } catch (Exception | LinkageError e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Could not update the teleporter placement count");
        }
    }
}
