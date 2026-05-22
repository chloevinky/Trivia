package dev.roanoke.trivia.Utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.roanoke.trivia.Trivia;
import net.minecraft.text.Text;
import org.apache.commons.io.IOUtils;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Messages {
    private static final String DEFAULT_RESOURCE = "/trivia-messages.json";

    private HashMap<String, String> messages = new HashMap<>();
    private String prefix = "";

    public Messages(Path filePath) {
        try {
            Files.createDirectories(filePath.getParent());

            if (!Files.exists(filePath)) {
                try (InputStream is = getClass().getResourceAsStream(DEFAULT_RESOURCE);
                     OutputStream os = new FileOutputStream(filePath.toFile())) {
                    if (is != null) {
                        IOUtils.copy(is, os);
                    } else {
                        Trivia.LOGGER.warn("Default messages resource not found in jar");
                    }
                }
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

            HashMap<String, String> userMessages = new HashMap<>();
            try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                HashMap<String, String> parsed = gson.fromJson(reader,
                        new TypeToken<HashMap<String, String>>() {}.getType());
                if (parsed != null) {
                    userMessages = parsed;
                }
            } catch (Exception e) {
                Trivia.LOGGER.warn("Failed to load Trivia messages.json - using defaults", e);
            }

            HashMap<String, String> defaults = loadDefaults();
            boolean addedAny = false;
            for (Map.Entry<String, String> entry : defaults.entrySet()) {
                if (!userMessages.containsKey(entry.getKey())) {
                    userMessages.put(entry.getKey(), entry.getValue());
                    addedAny = true;
                }
            }

            this.messages = userMessages;
            this.prefix = messages.getOrDefault("trivia.prefix", "");

            if (addedAny) {
                try {
                    Files.writeString(filePath, gson.toJson(messages), StandardCharsets.UTF_8);
                    Trivia.LOGGER.info("Merged new default keys into " + filePath.getFileName());
                } catch (Exception e) {
                    Trivia.LOGGER.warn("Failed to write updated messages.json", e);
                }
            }
        } catch (Exception e) {
            Trivia.LOGGER.error("Failed to initialize Trivia messages", e);
        }
    }

    private HashMap<String, String> loadDefaults() {
        try (InputStream is = getClass().getResourceAsStream(DEFAULT_RESOURCE)) {
            if (is == null) return new HashMap<>();
            Gson gson = new Gson();
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                HashMap<String, String> parsed = gson.fromJson(reader,
                        new TypeToken<HashMap<String, String>>() {}.getType());
                return parsed == null ? new HashMap<>() : parsed;
            }
        } catch (Exception e) {
            Trivia.LOGGER.warn("Failed to load bundled default messages", e);
            return new HashMap<>();
        }
    }

    public String getMessage(String key) {
        String message = messages.getOrDefault(key, "Placeholder message for missing key");
        message = message.replace("{prefix}", this.prefix == null ? "" : this.prefix);
        return message;
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        return message;
    }

    public Text getDisplayText(String message) {
        if (Trivia.adventure != null) {
            return Trivia.adventure.toNative(Trivia.mm.deserialize(message));
        }
        return Text.literal("Error converting MiniMessage format");
    }
}
