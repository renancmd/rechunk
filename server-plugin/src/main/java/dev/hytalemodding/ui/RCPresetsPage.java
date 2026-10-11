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
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.managers.SelectionManager;
import dev.hytalemodding.managers.SelectionManager.PresetResult;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where a player turns the current selection into a named preset, and renames or deletes presets.
 */
public class RCPresetsPage extends InteractiveCustomUIPage<RCPresetsPage.PresetsEventData> {

    public static class PresetsEventData {
        public String action;
        public String name;
        public String preset;
        public String newName;

        public static final BuilderCodec<PresetsEventData> CODEC =
                BuilderCodec.builder(PresetsEventData.class, PresetsEventData::new)
                        .append(
                                new KeyedCodec<>("Action", Codec.STRING),
                                (PresetsEventData obj, String val) -> obj.action = val,
                                (PresetsEventData obj) -> obj.action
                        )
                        .add()
                        .append(
                                new KeyedCodec<>("@Name", Codec.STRING),
                                (PresetsEventData obj, String val) -> obj.name = val,
                                (PresetsEventData obj) -> obj.name
                        )
                        .add()
                        .append(
                                new KeyedCodec<>("@Preset", Codec.STRING),
                                (PresetsEventData obj, String val) -> obj.preset = val,
                                (PresetsEventData obj) -> obj.preset
                        )
                        .add()
                        .append(
                                new KeyedCodec<>("@NewName", Codec.STRING),
                                (PresetsEventData obj, String val) -> obj.newName = val,
                                (PresetsEventData obj) -> obj.newName
                        )
                        .add()
                        .build();
    }

    // Shown inside the page; null hides each of them
    private String statusText;
    private String infoText;

    public RCPresetsPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PresetsEventData.CODEC);
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append("Pages/RCPresets.ui");

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent != null) applyState(cmd, uuidComponent.getUuid(), null);

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SavePresetButton",
                new EventData()
                        .append("Action", "Save")
                        .append("@Name", "#NameInput.Value")
        );

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RenameButton",
                new EventData()
                        .append("Action", "Rename")
                        .append("@Preset", "#PresetInput.Value")
                        .append("@NewName", "#RenameInput.Value")
        );

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#DeleteButton",
                new EventData()
                        .append("Action", "Delete")
                        .append("@Preset", "#PresetInput.Value")
        );

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenMainButton", new EventData().append("Action", "OpenMain"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", new EventData().append("Action", "Close"));
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull PresetsEventData data
    ) {
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null || data.action == null) return;
        UUID playerId = uuidComponent.getUuid();

        statusText = null;
        infoText = null;
        // Preset the dropdown should point at after the update; null keeps the first one
        String selectedPreset = data.preset;
        boolean clearName = false;
        boolean clearRename = false;

        switch (data.action) {
            case "Save":
                PresetResult saved = SelectionManager.savePreset(playerId, data.name);
                if (saved == PresetResult.OK) {
                    infoText = "Preset \"" + data.name.trim() + "\" saved.";
                    selectedPreset = data.name.trim();
                    clearName = true;
                } else {
                    statusText = describe(saved);
                }
                break;

            case "Rename":
                if (data.preset == null || data.preset.isEmpty()) {
                    statusText = "Choose a preset to rename.";
                    break;
                }

                PresetResult renamed = SelectionManager.renamePreset(playerId, data.preset, data.newName);
                if (renamed == PresetResult.OK) {
                    infoText = "Preset \"" + data.preset + "\" is now \"" + data.newName.trim() + "\".";
                    selectedPreset = data.newName.trim();
                    clearRename = true;
                } else {
                    statusText = describe(renamed);
                }
                break;

            case "Delete":
                if (data.preset == null || data.preset.isEmpty()) {
                    statusText = "Choose a preset to delete.";
                    break;
                }

                if (SelectionManager.deletePreset(playerId, data.preset)) {
                    infoText = "Preset \"" + data.preset + "\" deleted.";
                    selectedPreset = null;
                } else {
                    statusText = describe(PresetResult.NOT_FOUND);
                }
                break;

            case "OpenMain":
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) return;

                player.getPageManager().openCustomPage(ref, store, new RCPage(playerRef));
                return;

            case "Close":
                this.close();
                return;

            default:
                return;
        }

        UICommandBuilder cmd = new UICommandBuilder();
        applyState(cmd, playerId, selectedPreset);
        if (clearName) cmd.set("#NameInput.Value", "");
        if (clearRename) cmd.set("#RenameInput.Value", "");
        sendUpdate(cmd, false);
    }

    // Writes everything that depends on the player's data: counters, dropdown, list and messages
    private void applyState(UICommandBuilder cmd, UUID playerId, String selectedPreset) {
        Map<String, Integer> presets = SelectionManager.getPresets(playerId);
        int selectionSize = SelectionManager.getSelections(playerId).size();

        cmd.set("#SelectionInfo.Text", "Your current selection has " + selectionSize + " chunk(s).");
        cmd.set("#PresetCount.Text", "Saved presets (" + presets.size() + "/" + SelectionManager.MAX_PRESETS + ")");

        List<DropdownEntryInfo> entries = new ArrayList<>();
        cmd.clear("#PresetList");
        int index = 0;
        for (Map.Entry<String, Integer> preset : presets.entrySet()) {
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(preset.getKey()), preset.getKey()));

            String selector = "#PresetList[" + index++ + "]";
            cmd.append("#PresetList", "Pages/RCPresetEntry.ui");
            cmd.set(selector + " #PresetName.Text", preset.getKey());
            cmd.set(selector + " #PresetSize.Text", preset.getValue() + " chunk(s)");
        }

        cmd.set("#PresetInput.Entries", entries);
        if (!entries.isEmpty()) {
            boolean stillExists = selectedPreset != null && presets.containsKey(selectedPreset);
            cmd.set("#PresetInput.Value", stillExists ? selectedPreset : entries.get(0).value());
        }

        cmd.set("#StatusText.Text", statusText != null ? statusText : "");
        cmd.set("#StatusText.Visible", statusText != null);
        cmd.set("#InfoText.Text", infoText != null ? infoText : "");
        cmd.set("#InfoText.Visible", infoText != null);
    }

    private static String describe(PresetResult result) {
        return switch (result) {
            case EMPTY_SELECTION -> "Your selection is empty. Add chunks in /rechunk before saving a preset.";
            case INVALID_NAME -> "Enter a name with 1 to " + SelectionManager.MAX_PRESET_NAME_LENGTH + " characters.";
            case NAME_TAKEN -> "You already have a preset with that name. Names must be unique.";
            case LIMIT_REACHED -> "You reached the limit of " + SelectionManager.MAX_PRESETS + " presets. Delete one first.";
            case NOT_FOUND -> "That preset no longer exists.";
            case OK -> "";
        };
    }
}
