package dev.roanoke.trivia.Commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.roanoke.trivia.Config;
import dev.roanoke.trivia.Quiz.QuizManager;
import dev.roanoke.trivia.Trivia;
import dev.roanoke.trivia.Utils.Messages;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class QuizCommands {
    public QuizCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("trivia")
                            .then(
                                    literal("interval")
                                            .requires(Permissions.require("trivia.interval", 4))
                                            .then(argument("intervalSeconds", IntegerArgumentType.integer(1, 999999))
                                                    .executes(this::executeQuizInterval))
                            )
                            .then(
                                    literal("start").requires(Permissions.require("trivia.start", 4))
                                            .executes(this::executeStartQuiz)
                            )
                            .then(
                                    literal("reload").requires(Permissions.require("trivia.reload", 4))
                                            .executes(this::executeReloadQuiz)
                            )
                            .then(
                                    literal("timeout").requires(Permissions.require("trivia.timeout", 4))
                                            .then(argument("timeoutSeconds", IntegerArgumentType.integer(1, 999999))
                                                    .executes(this::executeQuizTimeout))
                            )
            );
        });
    }

    private int executeQuizTimeout(CommandContext<ServerCommandSource> ctx) {
        int seconds = ctx.getArgument("timeoutSeconds", Integer.class);
        Trivia.getInstance().config.setQuizTimeOut(seconds);
        ctx.getSource().sendMessage(Text.literal("Updated Quiz Timeout to " + seconds + " seconds."));
        return 1;
    }

    private int executeQuizInterval(CommandContext<ServerCommandSource> ctx) {
        int seconds = ctx.getArgument("intervalSeconds", Integer.class);
        Trivia.getInstance().config.setQuizInterval(seconds);
        ctx.getSource().sendMessage(Text.literal("Updated Quiz Interval to " + seconds + " seconds."));
        return 1;
    }

    private int executeStartQuiz(CommandContext<ServerCommandSource> ctx) {
        Trivia trivia = Trivia.getInstance();
        if (trivia.quiz.quizInProgress()) {
            ctx.getSource().sendMessage(Text.literal("A quiz is already in progress."));
            return 0;
        }
        trivia.quizIntervalCounter = 0;
        trivia.quizTimeOutCounter = 0;
        trivia.quiz.startQuiz(ctx.getSource().getServer());
        return 1;
    }

    private int executeReloadQuiz(CommandContext<ServerCommandSource> ctx) {
        Trivia trivia = Trivia.getInstance();
        trivia.quizIntervalCounter = 0;
        trivia.quizTimeOutCounter = 0;
        trivia.config = new Config();
        Trivia.messages = new Messages(FabricLoader.getInstance().getConfigDir().resolve("Trivia/messages.json"));
        if (Trivia.translations != null) {
            Trivia.translations.shutdown();
        }
        Trivia.translations = Trivia.buildTranslationManager(trivia.config);
        trivia.quiz = new QuizManager();
        ctx.getSource().sendMessage(Text.literal("Trivia configuration reloaded."));
        return 1;
    }
}
