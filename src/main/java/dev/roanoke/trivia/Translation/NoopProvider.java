package dev.roanoke.trivia.Translation;

public class NoopProvider implements TranslationProvider {
    @Override
    public String translate(String text, String targetLang) {
        return null;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getName() {
        return "disabled";
    }
}
