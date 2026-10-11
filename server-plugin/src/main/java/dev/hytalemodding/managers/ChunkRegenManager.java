package dev.hytalemodding.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.world.ClearChunks;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import dev.hytalemodding.world.pos.BlockPos;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;

public class ChunkRegenManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int CHUNKS_PER_TICK = 5; // Higher = Faster but more Lag
    private static final int PROGRESS_STEP_PERCENT = 10;
    private static final int MAX_FAILURES_LISTED = 5;

    private static final String PREFIX = "[ReChunk] ";
    private static final String COLOR_ERROR = "#FF5555";
    private static final String COLOR_SUCCESS = "#55FF55";

    // State of one regeneration run, carried from tick to tick
    private static final class Job {
        final World world;
        final PlayerRef player;
        final Queue<BlockPos> queue;
        final int total;
        final List<BlockPos> failed = new ArrayList<>();
        int done = 0;
        int lastReportedStep = 0;

        Job(World world, PlayerRef player, List<BlockPos> targetChunks) {
            this.world = world;
            this.player = player;
            this.queue = new LinkedList<>(targetChunks);
            this.total = targetChunks.size();
        }
    }

    public static void startRegeneration(World world, PlayerRef player, List<BlockPos> targetChunks) {
        if (world.getChunkStore().getSaver() == null) {
            notify(player, "Error: this world has no chunk storage, nothing was regenerated.", COLOR_ERROR);
            return;
        }

        Job job = new Job(world, player, targetChunks);
        notify(player, "Starting regeneration of " + job.total + " chunks...", null);

        processQueue(job);
    }

    private static void processQueue(Job job) {
        try {
            // Process a batch of chunks
            for (int i = 0; i < CHUNKS_PER_TICK && !job.queue.isEmpty(); i++) {
                BlockPos pos = job.queue.poll();
                if (!regenerateSingleChunk(job.world, pos.chunkX(), pos.chunkZ())) {
                    job.failed.add(pos);
                }
                job.done++;
            }
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Regeneration aborted at %d/%d chunks", job.done, job.total);
            notify(job.player, "Error: regeneration stopped at " + progress(job) + ". Reason: " + e
                    + ". Check the server log.", COLOR_ERROR);
            refreshClientChunks(job.world, job.player);
            return;
        }

        if (job.queue.isEmpty()) {
            finish(job);
            return;
        }

        reportProgress(job);

        // Schedule the next batch for the next server tick
        job.world.execute(() -> processQueue(job));
    }

    private static void reportProgress(Job job) {
        int step = (job.done * 100 / job.total) / PROGRESS_STEP_PERCENT;
        if (step > job.lastReportedStep) {
            job.lastReportedStep = step;
            notify(job.player, progress(job), null);
        }
    }

    private static String progress(Job job) {
        return (job.done * 100 / job.total) + "% (" + job.done + "/" + job.total + " chunks)";
    }

    private static void finish(Job job) {
        // Refresh client chunks once at the end to ensure they see the changes
        refreshClientChunks(job.world, job.player);

        if (job.failed.isEmpty()) {
            notify(job.player, progress(job) + " - regeneration complete!", COLOR_SUCCESS);
            return;
        }

        StringBuilder coords = new StringBuilder();
        for (int i = 0; i < job.failed.size() && i < MAX_FAILURES_LISTED; i++) {
            BlockPos pos = job.failed.get(i);
            if (i > 0) coords.append("; ");
            coords.append(pos.chunkX()).append(", ").append(pos.chunkZ());
        }
        if (job.failed.size() > MAX_FAILURES_LISTED) coords.append("; ...");

        notify(job.player, "Finished with errors: " + job.failed.size() + " of " + job.total
                + " chunks could not be regenerated (" + coords + "). Check the server log.", COLOR_ERROR);
    }

    // Returns false if the chunk could not be fully removed
    private static boolean regenerateSingleChunk(World world, int chunkX, int chunkZ) {
        boolean ok = true;
        ChunkStore chunkStore = world.getChunkStore();
        long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);

        // Unload from RAM (only if the chunk is currently loaded)
        try {
            Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
            if (chunkRef != null && chunkRef.isValid()) {
                chunkStore.remove(chunkRef, RemoveReason.UNLOAD);
            }
        } catch (Exception e) {
            ok = false;
            LOGGER.at(Level.WARNING).withCause(e).log("Could not unload chunk %d, %d", chunkX, chunkZ);
        }

        // Delete from Disk (This is usually blocking, be careful)
        try {
            IChunkSaver saver = chunkStore.getSaver();
            saver.removeHolder(chunkX, chunkZ).join();
            saver.flush();
        } catch (Exception e) {
            ok = false;
            LOGGER.at(Level.WARNING).withCause(e).log("Could not delete chunk %d, %d from disk", chunkX, chunkZ);
        }

        return ok;
    }

    private static void refreshClientChunks(World world, PlayerRef player) {
        try {
            ChunkTracker tracker = world.getEntityStore().getStore()
                    .getComponent(player.getReference(), ChunkTracker.getComponentType());
            if (tracker != null) {
                // Same sequence the built-in "/chunk resend" command uses
                tracker.clear();
                player.getPacketHandler().writeNoCache(new ClearChunks());
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Could not refresh chunks for the player");
        }
    }

    // A failed chat message (e.g. the player left) must never interrupt the job
    private static void notify(PlayerRef player, String text, String color) {
        try {
            Message message = Message.raw(PREFIX + text);
            if (color != null) message = message.color(color);
            player.sendMessage(message);
        } catch (Exception e) {
            LOGGER.at(Level.FINE).withCause(e).log("Could not send message to the player: %s", text);
        }
    }
}
