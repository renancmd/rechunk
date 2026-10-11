package dev.hytalemodding.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.managers.ChunkRegenManager;
import dev.hytalemodding.managers.SelectionManager;
import dev.hytalemodding.world.pos.BlockPos;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Last step before chunks are deleted: shows what will be lost and asks the player to confirm.
 */
public class RCConfirmPage extends InteractiveCustomUIPage<RCConfirmPage.ConfirmEventData> {

    public static class ConfirmEventData {
        public String action;

        public static final BuilderCodec<ConfirmEventData> CODEC =
                BuilderCodec.builder(ConfirmEventData.class, ConfirmEventData::new)
                        .append(
                                new KeyedCodec<>("Action", Codec.STRING),
                                (ConfirmEventData obj, String val) -> obj.action = val,
                                (ConfirmEventData obj) -> obj.action
                        )
                        .add()
                        .build();
    }

    private final List<BlockPos> chunksToRegen;
    private final List<String> teleporters;
    private final String mode;

    public RCConfirmPage(@Nonnull PlayerRef playerRef, List<BlockPos> chunksToRegen, List<String> teleporters, String mode) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ConfirmEventData.CODEC);
        this.chunksToRegen = chunksToRegen;
        this.teleporters = teleporters;
        this.mode = mode;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append("Pages/RCConfirm.ui");

        cmd.set("#ConfirmSummary.Text", chunksToRegen.size() + " chunk(s) will be permanently deleted and regenerated. "
                + "Everything built in them will be lost.");

        if (!teleporters.isEmpty()) {
            cmd.set("#ConfirmTeleporters.Text", teleporters.size()
                    + " teleporter(s) in this area will be removed, along with their destination: "
                    + String.join(", ", teleporters) + ".");
            cmd.set("#ConfirmTeleporters.Visible", true);
        }

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton", new EventData().append("Action", "Confirm"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", new EventData().append("Action", "Back"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ManualButton", new EventData().append("Action", "Manual"));
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull ConfirmEventData data
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (player == null || uuidComponent == null || data.action == null) return;

        switch (data.action) {
            case "Confirm":
                World world = player.getWorld();

                // Checked before closing so the selection is kept for a later retry
                if (ChunkRegenManager.isRunning(world)) {
                    UICommandBuilder cmd = new UICommandBuilder();
                    cmd.set("#StatusText.Text", "A regeneration is already running in this world. Wait for it to finish.");
                    cmd.set("#StatusText.Visible", true);
                    sendUpdate(cmd, false);
                    return;
                }

                // The job keeps running on the world thread and reports through chat,
                // so the page is closed and the selection reset before it starts.
                SelectionManager.clearSelections(uuidComponent.getUuid());
                this.close();

                ChunkRegenManager.startRegeneration(world, playerRef, chunksToRegen);
                return;

            case "Back":
                player.getPageManager().openCustomPage(ref, store, new RCPage(playerRef, mode));
                return;

            case "Manual":
                player.getPageManager().openCustomPage(ref, store, new RCManualPage(playerRef));
                return;
        }
    }
}
