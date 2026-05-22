package dev.roanoke.trivia;

import dev.roanoke.trivia.Commands.QuizCommands;
import dev.roanoke.trivia.Quiz.Question;
import dev.roanoke.trivia.Quiz.QuizManager;
import dev.roanoke.trivia.Translation.ClaudeProvider;
import dev.roanoke.trivia.Translation.MyMemoryProvider;
import dev.roanoke.trivia.Translation.NoopProvider;
import dev.roanoke.trivia.Translation.TranslationManager;
import dev.roanoke.trivia.Translation.TranslationProvider;
import dev.roanoke.trivia.Utils.Messages;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.platform.fabric.FabricServerAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Trivia implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("Trivia");
    public static FabricServerAudiences adventure;
    public static MiniMessage mm = MiniMessage.miniMessage();

    public static Messages messages = new Messages(FabricLoader.getInstance().getConfigDir().resolve("Trivia/messages.json"));
    public static TranslationManager translations;
    public static Trivia instance;

    public QuizManager quiz;
    public Config config = new Config();
    public Integer quizIntervalCounter = 0;
    public Integer quizTimeOutCounter = 0;

    @Override
    public void onInitialize() {
        instance = this;
        translations = buildTranslationManager(config);
        quiz = new QuizManager();

        new QuizCommands();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            adventure = FabricServerAudiences.of(server);
            LOGGER.info("Trivia ready - bilingual mode: " + config.isBilingualMode()
                    + ", secondary language: " + config.getSecondaryLanguage()
                    + ", interval: " + (config.getQuizInterval() / 20) + "s"
                    + ", timeout: " + (config.getQuizTimeOut() / 20) + "s");
            prefetchUpcomingTranslations();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (translations != null) {
                translations.shutdown();
            }
            if (adventure != null) {
                adventure.close();
                adventure = null;
            }
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            boolean hasPlayers = !server.getPlayerManager().getPlayerList().isEmpty();
            boolean inProgress = quiz.quizInProgress();

            if (inProgress) {
                if (quizTimeOutCounter >= config.getQuizTimeOut()) {
                    quizTimeOutCounter = 0;
                    quizIntervalCounter = 0;
                    quiz.timeOutQuiz(server);
                } else {
                    quizTimeOutCounter++;
                }
            } else if (hasPlayers) {
                if (quizIntervalCounter >= config.getQuizInterval()) {
                    quizIntervalCounter = 0;
                    quiz.startQuiz(server);
                } else {
                    quizIntervalCounter++;
                }
            } else {
                quizIntervalCounter = 0;
                quizTimeOutCounter = 0;
            }
        });

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (quiz.quizInProgress() && quiz.isRightAnswer(message.getContent().getString())) {
                LOGGER.info("Trivia question was answered correctly by " + sender.getGameProfile().getName());
                quiz.processQuizWinner(sender, sender.server);
            }
        });
    }

    public static TranslationManager buildTranslationManager(Config cfg) {
        TranslationProvider provider;
        String name = cfg.getTranslationProvider();
        if (!cfg.isBilingualMode() || "disabled".equals(name) || "none".equals(name) || "off".equals(name)) {
            provider = new NoopProvider();
        } else if ("claude".equals(name)) {
            String key = cfg.getClaudeApiKey();
            if (key == null || key.isBlank()) {
                LOGGER.warn("Claude translation provider selected but claudeApiKey is empty - translations disabled");
                provider = new NoopProvider();
            } else {
                provider = new ClaudeProvider(key, cfg.getClaudeModel());
            }
        } else {
            if (!"mymemory".equals(name)) {
                LOGGER.warn("Unknown translationProvider '" + name + "', falling back to mymemory");
            }
            provider = new MyMemoryProvider(cfg.getMyMemoryEmail());
        }
        LOGGER.info("Trivia translation provider: " + provider.getName());
        return new TranslationManager(
                FabricLoader.getInstance().getConfigDir().resolve("Trivia/translations.json"),
                provider);
    }

    private void prefetchUpcomingTranslations() {
        if (translations == null || !translations.isEnabled() || !config.isBilingualMode()) return;
        String target = config.getSecondaryLanguage();
        int max = 25;
        int submitted = 0;
        for (Question q : quiz.questionPoolCopy()) {
            if (submitted >= max) break;
            if (translations.getCached(q.question, target) == null) {
                translations.prefetch(q.question, target);
                submitted++;
            }
        }
        if (submitted > 0) {
            LOGGER.info("Queued " + submitted + " translation prefetches for " + target);
        }
    }

    public static Trivia getInstance() {
        return instance;
    }
}
