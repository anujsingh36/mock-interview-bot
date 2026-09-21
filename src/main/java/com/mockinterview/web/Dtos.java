package com.mockinterview.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class Dtos {

    public record RegisterRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, message = "Password must be at least 6 characters") String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record AuthResponse(String token, String name, String email) {}

    public record StartRequest(@NotBlank String topic, @NotBlank String level) {}

    public record StartResponse(Long sessionId, String question, int questionNumber, int totalQuestions) {}

    public record AnswerRequest(@NotBlank String answer) {}

    public record AnswerResponse(
            int score,
            String feedback,
            String modelAnswer,
            String nextQuestion,
            int questionNumber,
            int totalQuestions,
            boolean finished,
            Integer averageScore,
            String summary) {}

    public record HistoryItem(
            Long sessionId,
            String topic,
            String level,
            int answered,
            int averageScore,
            boolean finished,
            String startedAt,
            String summary) {}

    public record TranscriptItem(String question, String answer, Integer score, String feedback) {}

    public record SessionDetail(HistoryItem session, List<TranscriptItem> transcript) {}

    public record ErrorResponse(String message) {}
}
