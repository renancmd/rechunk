package dev.hytalemodding.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Reads and writes one JSON file per player, so selections survive a server restart.
 */
public class SelectionStore {

    // Shape of the file on disk
    public static class SavedSelection {
        public List<SavedChunk> chunks = new ArrayList<>();
    }

    public static class SavedChunk {
        public int x;
        public int z;
        public boolean origin;

        public SavedChunk() {}

        public SavedChunk(int x, int z, boolean origin) {
            this.x = x;
            this.z = z;
            this.origin = origin;
        }
    }

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path directory;

    public SelectionStore(Path dataDirectory) {
        this.directory = dataDirectory.resolve("selections");
    }

    // Returns null when the player has nothing saved or the file cannot be read
    public SavedSelection load(UUID playerId) {
        Path file = fileOf(playerId);
        if (!Files.isRegularFile(file)) return null;

        try {
            return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), SavedSelection.class);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Could not read saved selection %s", file);
            return null;
        }
    }

    public void save(UUID playerId, SavedSelection selection) {
        Path file = fileOf(playerId);

        try {
            Files.createDirectories(directory);

            // Written to a temporary file first so a crash never leaves a half-written selection
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(selection), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Could not save selection %s", file);
        }
    }

    private Path fileOf(UUID playerId) {
        return directory.resolve(playerId + ".json");
    }
}
