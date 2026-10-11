package dev.hytalemodding.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

public class RCManualPage extends InteractiveCustomUIPage<RCManualPage.ManualEventData> {

    public static class ManualEventData {
        public String action;
        public String language;

        public static final BuilderCodec<ManualEventData> CODEC =
                BuilderCodec.builder(ManualEventData.class, ManualEventData::new)
                        .append(
                                new KeyedCodec<>("Action", Codec.STRING),
                                (ManualEventData obj, String val) -> obj.action = val,
                                (ManualEventData obj) -> obj.action
                        )
                        .add()
                        .append(
                                new KeyedCodec<>("@Language", Codec.STRING),
                                (ManualEventData obj, String val) -> obj.language = val,
                                (ManualEventData obj) -> obj.language
                        )
                        .add()
                        .build();
    }

    // The texts live in Server/Languages/<language>/rechunk.lang
    private static final String KEY_PREFIX = "rechunk.manual.";
    private static final String DEFAULT_LANGUAGE = "en-US";
    private static final List<String> LANGUAGES = List.of(DEFAULT_LANGUAGE, "pt-BR");
    private static final int SECTION_COUNT = 7;

    public RCManualPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ManualEventData.CODEC);
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append("Pages/RCManual.ui");

        // Opens in the language of the player's game when the manual has it
        String language = LANGUAGES.contains(playerRef.getLanguage()) ? playerRef.getLanguage() : DEFAULT_LANGUAGE;

        cmd.set("#LanguageInput.Entries", List.of(
                new DropdownEntryInfo(LocalizableString.fromString("English"), DEFAULT_LANGUAGE),
                new DropdownEntryInfo(LocalizableString.fromString("Português (Brasil)"), "pt-BR")
        ));
        cmd.set("#LanguageInput.Value", language);
        applyTexts(cmd, language);

        evt.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#LanguageInput",
                new EventData()
                        .append("Action", "Language")
                        .append("@Language", "#LanguageInput.Value")
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
            @Nonnull ManualEventData data
    ) {
        if ("Close".equals(data.action)) {
            this.close();
            return;
        }

        if ("Language".equals(data.action) && LANGUAGES.contains(data.language)) {
            UICommandBuilder cmd = new UICommandBuilder();
            applyTexts(cmd, data.language);
            sendUpdate(cmd, false);
        }
    }

    private static void applyTexts(UICommandBuilder cmd, String language) {
        cmd.set("#ManualTitle.Text", text(language, "title"));
        cmd.set("#LanguageLabel.Text", text(language, "language"));
        cmd.set("#CloseButton.Text", text(language, "close"));

        for (int i = 1; i <= SECTION_COUNT; i++) {
            cmd.set("#S" + i + "Title.Text", text(language, "s" + i + ".title"));
            cmd.set("#S" + i + "Body.Text", text(language, "s" + i + ".body"));
        }
    }

    // Falls back to English, then to the key itself so a missing text is easy to spot
    private static String text(String language, String key) {
        String fullKey = KEY_PREFIX + key;

        String message = I18nModule.get().getMessage(language, fullKey);
        if (message == null) message = I18nModule.get().getMessage(DEFAULT_LANGUAGE, fullKey);
        return message != null ? message : fullKey;
    }
}
