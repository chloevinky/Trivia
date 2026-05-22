package dev.roanoke.trivia.Translation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.roanoke.trivia.Trivia;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class TranslationManager {

    private final Path cacheFile;
    private final TranslationProvider provider;
    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();
    private final Set<String> failedKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Object saveLock = new Object();

    public TranslationManager(Path cacheFile, TranslationProvider provider) {
        this.cacheFile = cacheFile;
        this.provider = provider;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Trivia-Translator");
            t.setDaemon(true);
            return t;
        });
        load();
    }

    public boolean isEnabled() {
        return provider != null && provider.isAvailable();
    }

    public String getCached(String text, String targetLang) {
        Map<String, String> langCache = cache.get(targetLang);
        return langCache == null ? null : langCache.get(text);
    }

    public String getOrFetch(String text, String targetLang, long timeoutMs) {
        String cached = getCached(text, targetLang);
        if (cached != null) {
            return cached;
        }
        if (!isEnabled()) {
            return null;
        }
        String key = key(text, targetLang);
        if (failedKeys.contains(key)) {
            return null;
        }
        if (timeoutMs <= 0) {
            prefetch(text, targetLang);
            return null;
        }

        Future<String> future;
        try {
            future = executor.submit(() -> doTranslate(text, targetLang));
        } catch (RejectedExecutionException e) {
            return null;
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            return null;
        } catch (Exception e) {
            Trivia.LOGGER.warn("Translation failed for [" + targetLang + "] '" + text + "': " + e.getMessage());
            failedKeys.add(key);
            return null;
        }
    }

    public void prefetch(String text, String targetLang) {
        if (!isEnabled()) return;
        if (getCached(text, targetLang) != null) return;
        String key = key(text, targetLang);
        if (failedKeys.contains(key)) return;
        if (!inFlight.add(key)) return;

        try {
            executor.submit(() -> {
                try {
                    doTranslate(text, targetLang);
                } catch (Exception e) {
                    Trivia.LOGGER.warn("Prefetch translation failed for [" + targetLang + "] '" + text + "': " + e.getMessage());
                    failedKeys.add(key);
                } finally {
                    inFlight.remove(key);
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.remove(key);
        }
    }

    private String doTranslate(String text, String targetLang) throws Exception {
        String cached = getCached(text, targetLang);
        if (cached != null) return cached;

        String translated = provider.translate(text, targetLang);
        if (translated == null || translated.isBlank()) {
            throw new IllegalStateException("Empty translation");
        }
        Map<String, String> langCache = cache.computeIfAbsent(targetLang, k -> new ConcurrentHashMap<>());
        langCache.put(text, translated);
        save();
        return translated;
    }

    private static String key(String text, String lang) {
        return lang + "|" + text;
    }

    public void load() {
        if (!Files.exists(cacheFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (String lang : root.keySet()) {
                JsonObject langObj = root.getAsJsonObject(lang);
                Map<String, String> langCache = cache.computeIfAbsent(lang, k -> new ConcurrentHashMap<>());
                for (String en : langObj.keySet()) {
                    langCache.put(en, langObj.get(en).getAsString());
                }
            }
            int total = cache.values().stream().mapToInt(Map::size).sum();
            Trivia.LOGGER.info("Loaded " + total + " cached translations from " + cacheFile);
        } catch (Exception e) {
            Trivia.LOGGER.warn("Failed to load translation cache (" + cacheFile + "): " + e.getMessage());
        }
    }

    public void save() {
        synchronized (saveLock) {
            try {
                Files.createDirectories(cacheFile.getParent());
                JsonObject root = new JsonObject();
                for (Map.Entry<String, Map<String, String>> entry : cache.entrySet()) {
                    JsonObject langObj = new JsonObject();
                    Map<String, String> sorted = new HashMap<>(entry.getValue());
                    for (Map.Entry<String, String> pair : sorted.entrySet()) {
                        langObj.addProperty(pair.getKey(), pair.getValue());
                    }
                    root.add(entry.getKey(), langObj);
                }
                Files.writeString(cacheFile, gson.toJson(root), StandardCharsets.UTF_8);
            } catch (Exception e) {
                Trivia.LOGGER.warn("Failed to save translation cache: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        save();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public String providerName() {
        return provider == null ? "null" : provider.getName();
    }
}
