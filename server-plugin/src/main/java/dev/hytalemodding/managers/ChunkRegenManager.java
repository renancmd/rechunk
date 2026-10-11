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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class ChunkRegenManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int CHUNKS_PER_BATCH = 5; // Higher = Faster but more Lag
    private static final int BATCH_TIMEOUT_SECONDS = 60;
    private static final int PROGRESS_STEP_PERCENT = 10;
    private static final int MAX_FAILURES_LISTED = 5;

    private static final String PREFIX = "[ReChunk] ";
    private static final String COLOR_ERROR = "#FF5555";
    private static final String COLOR_SUCCESS = "#55FF55";

    // Worlds with a run in progress; only one run per world is allowed
    private static final Set<World> RUNNING_WORLDS = ConcurrentHashMap.newKeySet();

    // State of one regeneration run; only touched from the world thread
    private static final class Job {
        final World world;
        final PlayerRef player;
        final Queue<BlockPos> queue;
        final int total;
        final List<BlockPos> failed = new ArrayList<>();
        final Set<Long> removedChunks = new HashSet<>();
        int done = 0;
        int lastReportedStep = 0;

        Job(World world, PlayerRef player, List<BlockPos> targetChunks) {
            this.world = world;
            this.player = player;
            this.queue = new LinkedList<>(targetChunks);
            this.total = targetChunks.size();
        }
    }

    // A chunk whose disk removal has been requested but may not have finished yet
    private record PendingRemoval(BlockPos pos, boolean unloaded, CompletableFuture<Void> removal) {}

    public static boolean isRunning(World world) {
        return RUNNING_WORLDS.contains(world);
    }

    public static void startRegeneration(World world, PlayerRef player, List<BlockPos> targetChunks) {
        if (world.getChunkStore().getSaver() == null) {
            notify(player, "Error: this world has no chunk storage, nothing was regenerated.", COLOR_ERROR);
            return;
        }

        // add() is atomic, so two simultaneous starts cannot both pass
        if (!RUNNING_WORLDS.add(world)) {
            notify(player, "Error: a regeneration is already running in this world. Wait for it to finish.", COLOR_ERROR);
            return;
        }

        Job job = new Job(world, player, targetChunks);
        notify(player, "Starting regeneration of " + job.total + " chunks...", null);

        processBatch(job);
    }

    // Runs on the world thread: unloads a batch and requests its removal from disk
    private static void processBatch(Job job) {
        try {
            List<PendingRemoval> batch = new ArrayList<>();
            for (int i = 0; i < CHUNKS_PER_BATCH && !job.queue.isEmpty(); i++) {
                batch.add(removeChunk(job.world, job.queue.poll()));
            }

            CompletableFuture<?>[] removals = batch.stream().map(PendingRemoval::removal).toArray(CompletableFuture[]::new);

            // The disk work happens on the storage threads. Instead of blocking the world
            // thread until it is done, the next step is scheduled back on the world thread.
            CompletableFuture.allOf(removals)
                    .orTimeout(BATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .handleAsync((ignored, error) -> {
                        completeBatch(job, batch);
                        return null;
                    }, job.world)
                    .exceptionally(error -> {
                        abort(job, error);
                        return null;
                    });
        } catch (Exception e) {
            abort(job, e);
        }
    }

    // Runs on the world thread once the removals of a batch finished (or timed out)
    private static void completeBatch(Job job, List<PendingRemoval> batch) {
        for (PendingRemoval pending : batch) {
            BlockPos pos = pending.pos();
            boolean removed = false;

            if (!pending.removal().isDone()) {
                LOGGER.at(Level.WARNING).log("Timed out deleting chunk %d, %d from disk", pos.chunkX(), pos.chunkZ());
            } else {
                try {
                    pending.removal().join(); // Already done: returns or throws immediately
                    removed = true;
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).withCause(e).log("Could not delete chunk %d, %d from disk", pos.chunkX(), pos.chunkZ());
                }
            }

            if (!removed || !pending.unloaded()) {
                job.failed.add(pos);
            } else {
                job.removedChunks.add(ChunkUtil.indexChunk(pos.chunkX(), pos.chunkZ()));
            }
            job.done++;
        }

        if (job.queue.isEmpty()) {
            finish(job);
            return;
        }

        reportProgress(job);
        processBatch(job);
    }

    private static PendingRemoval removeChunk(World world, BlockPos pos) {
        int chunkX = pos.chunkX();
        int chunkZ = pos.chunkZ();
        ChunkStore chunkStore = world.getChunkStore();
        boolean unloaded = true;

        // Unload from RAM (only if the chunk is currently loaded)
        try {
            Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunk(chunkX, chunkZ));
            if (chunkRef != null && chunkRef.isValid()) {
                chunkStore.remove(chunkRef, RemoveReason.UNLOAD);
            }
        } catch (Exception e) {
            unloaded = false;
            LOGGER.at(Level.WARNING).withCause(e).log("Could not unload chunk %d, %d", chunkX, chunkZ);
        }

        // Request the delete from disk; the saver runs it on its own I/O thread
        CompletableFuture<Void> removal;
        try {
            removal = chunkStore.getSaver().removeHolder(chunkX, chunkZ);
        } catch (Exception e) {
            removal = CompletableFuture.failedFuture(e);
        }

        return new PendingRemoval(pos, unloaded, removal);
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
        RUNNING_WORLDS.remove(job.world);

        // One flush for the whole run instead of one per chunk
        boolean flushed = true;
        try {
            job.world.getChunkStore().getSaver().flush();
        } catch (Exception e) {
            flushed = false;
            LOGGER.at(Level.WARNING).withCause(e).log("Could not flush chunk storage after regeneration");
        }

        List<String> removedWarps = TeleporterCleanup.removeTeleporterWarps(job.world, job.removedChunks);

        // Refresh client chunks once at the end to ensure they see the changes
        refreshClientChunks(job.world, job.player);

        if (!removedWarps.isEmpty()) {
            notify(job.player, "Removed " + removedWarps.size() + " teleporter destination(s) from the reset area: "
                    + String.join(", ", removedWarps), null);
        }

        if (!flushed) {
            notify(job.player, "Warning: the chunk storage could not be flushed to disk. Check the server log.", COLOR_ERROR);
        }

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

    private static void abort(Job job, Throwable error) {
        RUNNING_WORLDS.remove(job.world);
        LOGGER.at(Level.SEVERE).withCause(error).log("Regeneration aborted at %d/%d chunks", job.done, job.total);
        notify(job.player, "Error: regeneration stopped at " + progress(job) + ". Reason: " + error
                + ". Check the server log.", COLOR_ERROR);
        refreshClientChunks(job.world, job.player);
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
