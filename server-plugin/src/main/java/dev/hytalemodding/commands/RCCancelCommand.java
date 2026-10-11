package dev.hytalemodding.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.managers.ChunkRegenManager;

import javax.annotation.Nonnull;

public class RCCancelCommand extends AbstractPlayerCommand {
    public RCCancelCommand() {
        super("cancel", "Stop the regeneration running in this world.");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (ChunkRegenManager.cancel(world)) {
            // The run reports where it stopped once the current batch is done
            playerRef.sendMessage(Message.raw("[ReChunk] Cancelling the regeneration..."));
        } else {
            playerRef.sendMessage(Message.raw("[ReChunk] No regeneration is running in this world."));
        }
    }
}
