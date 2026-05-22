package dev.roanoke.trivia.Translation;

public interface TranslationProvider {
    String translate(String text, String targetLang) throws Exception;
    boolean isAvailable();
    String getName();
}
