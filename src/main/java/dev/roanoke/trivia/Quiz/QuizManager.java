package dev.roanoke.trivia.Quiz;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.roanoke.trivia.Reward.Reward;
import dev.roanoke.trivia.Reward.RewardManager;
import dev.roanoke.trivia.Trivia;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuizManager {

    private Question currentQuestion = null;
    private Long questionTime = System.currentTimeMillis();
    private final List<Question> questionPool = new ArrayList<>();
    private RewardManager rewardManager = null;

    public QuizManager() {
        try {
            ensureFilesExist();
        } catch (Exception e) {
            Trivia.LOGGER.error("Failed to copy default config files to Trivia config directory", e);
        }
        loadQuestions();
        loadRewards();
    }

    public void ensureFilesExist() throws Exception {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("Trivia");
        Files.createDirectories(configPath);

        copyDefaultIfMissing("questions.json", configPath.resolve("questions.json"));
        copyDefaultIfMissing("rewards.json", configPath.resolve("rewards.json"));
    }

    private void copyDefaultIfMissing(String resourceName, Path destination) throws Exception {
        if (Files.exists(destination)) {
            return;
        }
        try (InputStream in = Trivia.class.getResourceAsStream("/" + resourceName)) {
            if (in == null) {
                Trivia.LOGGER.error("Default resource /" + resourceName + " missing from mod jar");
                return;
            }
            Files.copy(in, destination);
        }
    }

    public void loadQuestions() {
        questionPool.clear();
        Path questionsPath = FabricLoader.getInstance().getConfigDir().resolve("Trivia/questions.json");
        File questionFile = questionsPath.toFile();

        if (!questionFile.exists()) {
            Trivia.LOGGER.error("Questions file not found at " + questionsPath);
            return;
        }

        JsonElement root;
        try (FileReader reader = new FileReader(questionFile, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader);
        } catch (Exception e) {
            Trivia.LOGGER.error("Failed to parse questions.json", e);
            return;
        }

        if (root == null || !root.isJsonObject()) {
            Trivia.LOGGER.error("questions.json root is not a JSON object");
            return;
        }

        JsonObject questionsObj = root.getAsJsonObject();
        Trivia.LOGGER.info("Loading questions...");

        for (String difficulty : questionsObj.keySet()) {
            JsonElement difficultyElem = questionsObj.get(difficulty);
            if (!difficultyElem.isJsonArray()) continue;
            JsonArray questionsArr = difficultyElem.getAsJsonArray();

            for (JsonElement questionElem : questionsArr) {
                if (!questionElem.isJsonObject()) continue;
                JsonObject questionObj = questionElem.getAsJsonObject();

                if (!questionObj.has("question") || !questionObj.has("answers")) {
                    Trivia.LOGGER.warn("Skipping malformed question entry in pool '" + difficulty + "'");
                    continue;
                }

                String questionText = questionObj.get("question").getAsString();
                JsonArray answersArr = questionObj.get("answers").getAsJsonArray();
                List<String> answers = new ArrayList<>();
                for (JsonElement answerElem : answersArr) {
                    answers.add(answerElem.getAsString().toLowerCase());
                }
                if (answers.isEmpty()) continue;

                questionPool.add(new Question(questionText, answers, difficulty));
            }
        }
        Trivia.LOGGER.info("Loaded " + questionPool.size() + " questions.");
    }

    public void loadRewards() {
        Path rewardsPath = FabricLoader.getInstance().getConfigDir().resolve("Trivia/rewards.json");
        File rewardsFile = rewardsPath.toFile();

        if (!rewardsFile.exists()) {
            Trivia.LOGGER.error("Rewards file not found at " + rewardsPath);
            return;
        }

        JsonElement root;
        try (FileReader reader = new FileReader(rewardsFile, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader);
        } catch (Exception e) {
            Trivia.LOGGER.error("Failed to parse rewards.json", e);
            return;
        }

        if (root == null || !root.isJsonObject()) {
            Trivia.LOGGER.error("rewards.json root is not a JSON object");
            return;
        }
        rewardManager = new RewardManager(root.getAsJsonObject());
    }

    public Boolean quizInProgress() {
        return currentQuestion != null;
    }

    public Boolean isRightAnswer(String guess) {
        if (currentQuestion == null) return false;
        for (String answer : currentQuestion.answers) {
            if (answer.equalsIgnoreCase(guess)) {
                return true;
            }
        }
        return false;
    }

    public void startQuiz(MinecraftServer server) {
        if (questionPool.isEmpty()) {
            Trivia.LOGGER.warn("Cannot start quiz - question pool is empty");
            return;
        }

        currentQuestion = questionPool.get((int) (Math.random() * questionPool.size()));
        questionTime = System.currentTimeMillis();

        String englishText = currentQuestion.question;
        String translatedText = null;
        boolean bilingual = Trivia.getInstance().config.isBilingualMode();
        String targetLang = Trivia.getInstance().config.getSecondaryLanguage();

        if (bilingual && Trivia.translations != null && Trivia.translations.isEnabled()) {
            long timeout = Trivia.getInstance().config.getTranslationTimeoutMs();
            translatedText = Trivia.translations.getOrFetch(englishText, targetLang, timeout);
        }

        String messageKey = (bilingual && translatedText != null)
                ? "trivia.ask_question_bilingual"
                : "trivia.ask_question";

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{question}", englishText);
        placeholders.put("{question_en}", englishText);
        placeholders.put("{question_pt}", translatedText == null ? englishText : translatedText);
        placeholders.put("{lang}", targetLang);

        broadcast(server, Trivia.messages.getMessage(messageKey, placeholders));
    }

    public void processQuizWinner(ServerPlayerEntity player, MinecraftServer server) {
        if (currentQuestion == null) return;

        Reward reward = rewardManager == null ? null : rewardManager.giveReward(player, currentQuestion);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{player}", player.getGameProfile().getName());
        placeholders.put("{reward}",
                (reward == null || reward.itemDisplayName == null) ? "REWARD_ERROR" : reward.itemDisplayName);
        placeholders.put("{time}", String.valueOf(((System.currentTimeMillis() - questionTime) / 1000)));
        placeholders.put("{answer}", String.join(", ", currentQuestion.answers));

        broadcast(server, Trivia.messages.getMessage("trivia.correct_answer", placeholders));
        currentQuestion = null;
    }

    public void timeOutQuiz(MinecraftServer server) {
        if (currentQuestion == null) return;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{answer}", String.join(", ", currentQuestion.answers));

        broadcast(server, Trivia.messages.getMessage("trivia.no_answer", placeholders));
        currentQuestion = null;
    }

    private void broadcast(MinecraftServer server, String miniMessage) {
        server.getPlayerManager().getPlayerList().forEach(player ->
                player.sendMessage(Trivia.messages.getDisplayText(miniMessage)));
    }

    public List<Question> questionPoolCopy() {
        return new ArrayList<>(questionPool);
    }
}
