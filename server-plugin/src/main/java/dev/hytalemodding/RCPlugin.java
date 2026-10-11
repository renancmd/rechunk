package dev.hytalemodding;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.commands.RCCommand;
import dev.hytalemodding.managers.SelectionManager;

import javax.annotation.Nonnull;

public class RCPlugin extends JavaPlugin {

    public RCPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Selections are saved per player inside the plugin's data folder
        SelectionManager.init(getDataDirectory());

        // Register the follow commands: /rechunk
        this.getCommandRegistry().registerCommand(new RCCommand());
    }
}