package dev.hytalemodding.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RCPage extends InteractiveCustomUIPage<RCPage.SaveCoordsEventData> {

    public static class SaveCoordsEventData {
        public String xCoord;
        public String zCoord;
        public String buffer;
        public String action;
        public String index;
        public String mode;

        // Get all interactive UI elements from RCPage.ui
        public static final BuilderCodec<SaveCoordsEventData> CODEC =
                BuilderCodec.builder(SaveCoordsEventData.class, SaveCoordsEventData::new)
                        .append(
                                new KeyedCodec<>("Action", Codec.STRING),
                                (SaveCoordsEventData obj, String val) -> obj.action = val,
                                (SaveCoordsEventData obj) -> obj.action
                        )
                        .add()
                        .append(
                                new KeyedCodec<>("Index", Codec.STRING),
                                (SaveCoordsEventData obj, String val) -> obj.index = val,
                                (SaveCoordsEventData obj) -> obj.index
                        )
                        .add()
                        .append(
                                new KeyedCodec<>("@XCoord", Codec.STRING),
                                (SaveCoordsEventData obj, String val) -> obj.xCoord = val,
                                (SaveCoordsEventData obj) -> obj.xCoord
                        )
                        .add()
                        .append(
                                new KeyedCodec<>("@ZCoord", Codec.STRING),
                                (SaveCoordsEventData obj, String val) -> obj.zCoord = val,
                                (SaveCoordsEventData obj) -> obj.zCoord
                        )
                        .add()
                        .append(
                                new KeyedCodec<>("@BufferInput", Codec.STRING),
                                (SaveCoordsEventData obj, String val) -> obj.buffer = val,
                                (SaveCoordsEventData obj) -> obj.buffer
                        )
                        .add()
                        .append(
                                new KeyedCodec<>("@Mode", Codec.STRING),
                                (SaveCoordsEventData obj, String val) -> obj.mode = val,
                                (SaveCoordsEventData obj) -> obj.mode
                        )
                        .add()
                        .build();
    }

    private static final int MAX_BUFFER = 9;

    private static final String MODE_PROTECT = "Protect";
    private static final String MODE_RESET = "Reset";

    private static final String COLOR_ENTRY = "#141c26";
    private static final String COLOR_ORIGIN_ENTRY = "#6b5220";

    // Validation message shown inside the page; null hides it
    private String statusText;

    public RCPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, SaveCoordsEventData.CODEC);
    }

    private static void bindSave(UIEventBuilder evt, String selector) {
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                new EventData()
                        .append("Action", "Save")
                        .append("@XCoord", "#XInput.Value")
                        .append("@ZCoord", "#ZInput.Value")
                        .append("@BufferInput", "#BufferInput.Value")

        );
    }

    private static EventData actionOnly(String action) {
        return new EventData().append("Action", action);
    }

    private void refreshPage(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent != null) {
            List<BlockPos> pos = SelectionManager.getSelections(uuidComponent.getUuid());
            buildCoordinatesList(cmd, evt, pos, uuidComponent.getUuid());
        }

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SaveButton",
                new EventData()
                        .append("Action", "Save")
                        .append("@XCoord", "#XInput.Value")
                        .append("@ZCoord", "#ZInput.Value")
                        .append("@BufferInput", "#BufferInput.Value")
        );

        cmd.set("#StatusText.Text", statusText != null ? statusText : "");
        cmd.set("#StatusText.Visible", statusText != null);

        sendUpdate(cmd, evt, false);
        cmd.set("#XInput.Value", "");
        cmd.set("#ZInput.Value", "");
    }

    // Build the UI field that displays the selected coordinates
    public void buildCoordinatesList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, List<BlockPos> pos, UUID playerId) {
        commandBuilder.clear("#CoordinatesList");
        commandBuilder.set("#SelectionCount.Text", "Selected chunks (" + (pos == null ? 0 : pos.size()) + ")");

        if (pos == null || pos.isEmpty()) return;

        for (int i = 0; i < pos.size(); i++) {
            BlockPos p = pos.get(i);
            String selector = "#CoordinatesList[" + i + "]";

            commandBuilder.append("#CoordinatesList", "Pages/CoordEntry.ui");

            commandBuilder.set(selector + " #ChunkCoordinates.Text", "Chunk: " + p.chunkX() + ", " + p.chunkZ());
            commandBuilder.set(selector + " #BlockCoordinates.Text", "Block: " + p.x() + ", " + p.z());

            boolean isOrigin = SelectionManager.isOrigin(playerId, p);

            if (isOrigin) {
                commandBuilder.set(selector + " #BackgroundChunkEntry.Background", COLOR_ORIGIN_ENTRY);
            } else {
                commandBuilder.set(selector + " #BackgroundChunkEntry.Background", COLOR_ENTRY);
            }

            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector + " #RemoveButton",
                    new EventData()
                            .append("Action", "Remove")
                            .append("Index", String.valueOf(i)),
                    false
            );
        }
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append("Pages/RCPage.ui");

        List<DropdownEntryInfo> bufferEntries = new ArrayList<>();
        for (int i = 0; i <= MAX_BUFFER; i++) {
            bufferEntries.add(new DropdownEntryInfo(LocalizableString.fromString(String.valueOf(i)), String.valueOf(i)));
        }
        cmd.set("#BufferInput.Entries", bufferEntries);
        cmd.set("#BufferInput.Value", "0");

        cmd.set("#ModeInput.Entries", List.of(
                new DropdownEntryInfo(LocalizableString.fromString("Protect selection"), MODE_PROTECT),
                new DropdownEntryInfo(LocalizableString.fromString("Reset selection"), MODE_RESET)
        ));
        cmd.set("#ModeInput.Value", MODE_PROTECT);

        List<BlockPos> pos = null;
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent != null) {
            pos = SelectionManager.getSelections(uuidComponent.getUuid());
        }

        if (pos != null) buildCoordinatesList(cmd, evt, pos, uuidComponent.getUuid());

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#StartButton",
                new EventData()
                        .append("Action", "StartRegen")
                        .append("@Mode", "#ModeInput.Value")
        );

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SaveButton",
                new EventData()
                        .append("Action", "Save")
                        .append("@XCoord", "#XInput.Value")
                        .append("@ZCoord", "#ZInput.Value")
                        .append("@BufferInput", "#BufferInput.Value")
        );

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ClearButton",
                new EventData().append("Action", "Clear")
        );

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                new EventData().append("Action", "Close")
        );
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull SaveCoordsEventData data
    ) {

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType()); // Get the player reference

        statusText = null;

        switch (data.action) {
            case "StartRegen":
                // Validation: Check Checkbox States
                boolean reset = MODE_RESET.equals(data.mode);
                boolean protect = MODE_PROTECT.equals(data.mode);
                if (!reset && !protect) {
                    statusText = "Select a mode: Protect or Reset.";
                    break;
                }

                // Get the current Selection List
                List<BlockPos> selectedChunks = SelectionManager.getSelections(uuidComponent.getUuid());
                if (selectedChunks == null) selectedChunks = new ArrayList<>();

                // Determine which chunks to regenerate
                List<BlockPos> chunksToRegen = new ArrayList<>();

                if (reset) {
                    // RESET MODE: Regenerate ONLY the selected chunks
                    chunksToRegen.addAll(selectedChunks);
                }
                else {
                    // PROTECT MODE: Regenerate area around player EXCEPT selected chunks
                    // We define a "Work Area" (e.g., 5 chunks radius around player)
                    int workRadius = 32;

                    // Simple implementation: Regenerate a 10x10 area around the first selected chunk, excluding the list
                    if (!selectedChunks.isEmpty()) {
                        BlockPos center = selectedChunks.get(0);
                        for (int x = -workRadius; x <= workRadius; x++) {
                            for (int z = -workRadius; z <= workRadius; z++) {
                                int cx = center.chunkX() + x;
                                int cz = center.chunkZ() + z;

                                // Check if this chunk is in the "Protected" list
                                boolean isProtected = false;
                                for (BlockPos safe : selectedChunks) {
                                    if (safe.chunkX() == cx && safe.chunkZ() == cz) {
                                        isProtected = true;
                                        break;
                                    }
                                }

                                if (!isProtected) {
                                    // It's not protected, so we regenerate it
                                     chunksToRegen.add(new BlockPos(cx * 32, cz * 32));
                                }
                            }
                        }
                    } else {
                        statusText = "Protect mode needs at least one selected chunk to define the center.";
                        break;
                    }
                }

                // Send to Manager
                if (!chunksToRegen.isEmpty()) {
                    Player playerEntity = store.getComponent(ref, Player.getComponentType());

                    if (playerEntity != null) {
                        World world = playerEntity.getWorld();

                        // The job keeps running on the world thread and reports through chat,
                        // so the page is closed and the selection reset before it starts.
                        SelectionManager.clearSelections(uuidComponent.getUuid());
                        this.close();

                        ChunkRegenManager.startRegeneration(world, playerRef, chunksToRegen);
                        return;
                    } else {
                        playerRef.sendMessage(Message.raw("Error: Could not find player entity."));
                    }
                } else {
                    statusText = "No chunks selected to regenerate.";
                }
                break;
            case "Save":
                String x = data.xCoord != null ? data.xCoord.trim() : "";
                String z = data.zCoord != null ? data.zCoord.trim() : "";
                if (x.isEmpty() || z.isEmpty()) {
                    statusText = "Enter both X and Z before adding.";
                    break;
                }
                String buff = (data.buffer != null && !data.buffer.isEmpty()) ? data.buffer : "0";

                int blockX;
                int blockZ;
                int radius;
                try {
                    blockX = Integer.parseInt(x);
                    blockZ = Integer.parseInt(z);
                    // The dropdown only offers 0..MAX_BUFFER, but the client value is not trusted
                    radius = Math.clamp(Integer.parseInt(buff.trim()), 0, MAX_BUFFER);
                } catch (NumberFormatException e) {
                    statusText = "X and Z must be whole numbers.";
                    break;
                }

                int chunkSize = 32;

                for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        int targetBlockX = blockX + (xOffset * chunkSize);
                        int targetBlockZ = blockZ + (zOffset * chunkSize);

                        boolean isOrigin = (xOffset == 0 && zOffset == 0);

                        SelectionManager.addSelection(uuidComponent.getUuid(), targetBlockX, targetBlockZ, isOrigin);
                    }
                }
                break;

            case "Remove":
                if (data.index != null) {
                    int indexToRemove = Integer.parseInt(data.index);
                    SelectionManager.removeSelection(uuidComponent.getUuid(), indexToRemove);
                }
                break;

            case "Clear":
                SelectionManager.clearSelections(uuidComponent.getUuid());
                break;

            case "Close":
                this.close();
                return;
        }

        refreshPage(ref, store);
    }
}
