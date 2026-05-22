package dev.roanoke.trivia;

import net.fabricmc.loader.api.FabricLoader;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Config {
    private static final Path CONFIG_FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("Trivia/config.properties");
    private final Properties properties;

    public Config() {
        properties = new Properties();
        boolean dirty = false;
        try {
            if (Files.exists(CONFIG_FILE_PATH)) {
                try (FileInputStream file = new FileInputStream(CONFIG_FILE_PATH.toFile())) {
                    properties.load(file);
                }
                dirty = applyDefaults();
            } else {
                applyDefaults();
                dirty = true;
            }
            if (dirty) {
                save();
            }
        } catch (IOException e) {
            Trivia.LOGGER.error("Failed to load Trivia config", e);
        }
    }

    private boolean applyDefaults() {
        boolean changed = false;
        changed |= setIfMissing("quizInterval", "600");
        changed |= setIfMissing("quizTimeOut", "120");
        changed |= setIfMissing("bilingualMode", "true");
        changed |= setIfMissing("secondaryLanguage", "pt-BR");
        changed |= setIfMissing("translationProvider", "mymemory");
        changed |= setIfMissing("translationTimeoutMs", "3000");
        changed |= setIfMissing("mymemoryEmail", "");
        changed |= setIfMissing("claudeApiKey", "");
        changed |= setIfMissing("claudeModel", "claude-haiku-4-5-20251001");
        return changed;
    }

    private boolean setIfMissing(String key, String value) {
        if (!properties.containsKey(key)) {
            properties.setProperty(key, value);
            return true;
        }
        return false;
    }

    public int getQuizTimeOut() {
        return parseInt("quizTimeOut", 120) * 20;
    }

    public void setQuizTimeOut(int timeout) {
        properties.setProperty("quizTimeOut", String.valueOf(timeout));
        save();
    }

    public int getQuizInterval() {
        return parseInt("quizInterval", 600) * 20;
    }

    public void setQuizInterval(int interval) {
        properties.setProperty("quizInterval", String.valueOf(interval));
        save();
    }

    public boolean isBilingualMode() {
        return Boolean.parseBoolean(properties.getProperty("bilingualMode", "true"));
    }

    public void setBilingualMode(boolean enabled) {
        properties.setProperty("bilingualMode", String.valueOf(enabled));
        save();
    }

    public String getSecondaryLanguage() {
        return properties.getProperty("secondaryLanguage", "pt-BR");
    }

    public String getTranslationProvider() {
        return properties.getProperty("translationProvider", "mymemory").toLowerCase();
    }

    public long getTranslationTimeoutMs() {
        return parseLong("translationTimeoutMs", 3000L);
    }

    public String getMyMemoryEmail() {
        return properties.getProperty("mymemoryEmail", "");
    }

    public String getClaudeApiKey() {
        return properties.getProperty("claudeApiKey", "");
    }

    public String getClaudeModel() {
        return properties.getProperty("claudeModel", "claude-haiku-4-5-20251001");
    }

    private int parseInt(String key, int fallback) {
        String raw = properties.getProperty(key);
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            Trivia.LOGGER.warn("Invalid integer for config '" + key + "': " + raw + ", using " + fallback);
            return fallback;
        }
    }

    private long parseLong(String key, long fallback) {
        String raw = properties.getProperty(key);
        if (raw == null) return fallback;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            Trivia.LOGGER.warn("Invalid long for config '" + key + "': " + raw + ", using " + fallback);
            return fallback;
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_FILE_PATH.getParent());
            try (FileOutputStream file = new FileOutputStream(CONFIG_FILE_PATH.toFile())) {
                properties.store(file, "Trivia mod configuration");
            }
        } catch (IOException e) {
            Trivia.LOGGER.error("Failed to save Trivia config", e);
        }
    }
}
