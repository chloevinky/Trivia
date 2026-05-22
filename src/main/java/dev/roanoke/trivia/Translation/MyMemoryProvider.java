package dev.roanoke.trivia.Translation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class MyMemoryProvider implements TranslationProvider {

    private static final String ENDPOINT = "https://api.mymemory.translated.net/get";
    private final String email;
    private final HttpClient http;

    public MyMemoryProvider(String email) {
        this.email = email;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    @Override
    public String translate(String text, String targetLang) throws Exception {
        String langPair = "en|" + targetLang;
        StringBuilder url = new StringBuilder(ENDPOINT)
                .append("?q=").append(URLEncoder.encode(text, StandardCharsets.UTF_8))
                .append("&langpair=").append(URLEncoder.encode(langPair, StandardCharsets.UTF_8));
        if (email != null && !email.isBlank()) {
            url.append("&de=").append(URLEncoder.encode(email, StandardCharsets.UTF_8));
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Trivia-Fabric-Mod")
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("MyMemory HTTP " + resp.statusCode());
        }

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (root.has("responseStatus")) {
            int status = root.get("responseStatus").getAsInt();
            if (status != 200) {
                String details = root.has("responseDetails") ? root.get("responseDetails").getAsString() : "";
                throw new IOException("MyMemory returned status " + status + ": " + details);
            }
        }
        if (!root.has("responseData")) {
            throw new IOException("MyMemory response missing responseData");
        }
        String translated = root.getAsJsonObject("responseData").get("translatedText").getAsString();
        if (translated == null || translated.isBlank()) {
            throw new IOException("MyMemory returned empty translation");
        }
        return translated;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getName() {
        return "mymemory";
    }
}
