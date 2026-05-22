package dev.roanoke.trivia.Translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class ClaudeProvider implements TranslationProvider {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private final String apiKey;
    private final String model;
    private final HttpClient http;

    public ClaudeProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String translate(String text, String targetLang) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 512);

        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content",
                "Translate this English Pokemon trivia question to " + targetLang + ".\n" +
                "Rules:\n" +
                "- Keep Pokemon names (e.g. Bulbasaur, Charizard) in English.\n" +
                "- Keep Pokemon type names (Fire, Water, Grass, Fairy, Electric, Ice, Fighting, Poison, " +
                "Ground, Flying, Psychic, Bug, Rock, Ghost, Dragon, Dark, Steel, Normal) in English.\n" +
                "- Keep ability names and move names in English.\n" +
                "- Respond with ONLY the translated text, no quotes, no explanation, no prefix.\n\n" +
                "Text: " + text);
        messages.add(msg);
        body.add("messages", messages);

        HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(20))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("Claude API HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 200));
        }

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (!root.has("content")) {
            throw new IOException("Claude response missing content field");
        }
        JsonArray content = root.getAsJsonArray("content");
        if (content.isEmpty()) {
            throw new IOException("Claude response had empty content array");
        }
        String translated = content.get(0).getAsJsonObject().get("text").getAsString().trim();
        if (translated.isBlank()) {
            throw new IOException("Claude returned empty translation");
        }
        return translated;
    }

    @Override
    public String getName() {
        return "claude";
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
