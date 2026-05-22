package dev.roanoke.trivia.Quiz;

import java.util.List;

public class Question {

    public String question;
    public List<String> answers;
    public String difficulty;

    public Question(String question, List<String> answers, String difficulty) {
        this.question = question;
        this.answers = answers;
        this.difficulty = difficulty;
    }
}
