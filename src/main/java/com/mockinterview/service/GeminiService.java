package com.mockinterview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Thin wrapper around the Google Gemini REST API.
 *
 * Docs: https://ai.google.dev/gemini-api/docs
 * Endpoint: POST {base}/models/{model}:generateContent
 * The API key is sent in the "x-goog-api-key" header (NOT in the URL), which
 * is what the new AQ.-style authorization keys need, and it also keeps the
 * key out of logs and error messages.
 */
@Service
public class GeminiService {

    private static final int MAX_ATTEMPTS = 3;

    private final RestClient client = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public GeminiService(@Value("${gemini.api-key}") String apiKey,
                         @Value("${gemini.model}") String model,
                         @Value("${gemini.base-url}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    /** Sends a prompt to Gemini and returns the plain text reply. */
    public String ask(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is not set. Get a key at https://aistudio.google.com/apikey");
        }

        String url = baseUrl + "/models/" + model + ":generateContent";

        Map<String, Object> body = Map.of(
                "contents", new Object[]{ Map.of("parts", new Object[]{ Map.of("text", prompt) }) },
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        // Newer models spend part of this budget on "thinking",
                        // so keep it generous or the visible answer can get cut off.
                        "maxOutputTokens", 2048,
                        "responseMimeType", "application/json")
        );

        String raw = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                raw = client.post()
                        .uri(url)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                break;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean retryable = status == 429 || status == 500 || status == 503;
                if (retryable && attempt < MAX_ATTEMPTS) {
                    sleep(1500L * attempt);
                    continue;
                }
                // Don't include the raw response/URL in the message.
                throw new IllegalStateException(friendlyMessage(status), e);
            }
        }

        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            StringBuilder text = new StringBuilder();
            for (JsonNode part : parts) {
                if (part.path("thought").asBoolean(false)) continue; // skip reasoning parts
                text.append(part.path("text").asText(""));
            }
            if (text.toString().isBlank()) {
                throw new IllegalStateException("Gemini returned an empty reply. Please try again.");
            }
            return text.toString();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not read Gemini reply: " + e.getMessage(), e);
        }
    }

    /** Sends a prompt and parses the JSON object Gemini replies with. */
    public JsonNode askForJson(String prompt) {
        String text = ask(prompt).trim();
        // Gemini sometimes wraps JSON in ```json ... ``` fences — strip them.
        if (text.startsWith("```")) {
            text = text.replaceAll("^```[a-zA-Z]*", "").replaceAll("```$", "").trim();
        }
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("Gemini did not return valid JSON. Please try again.", e);
        }
    }

    private static String friendlyMessage(int status) {
        return switch (status) {
            case 400 -> "Gemini rejected the request (400). Check the model name in application.properties.";
            case 401, 403 -> "Gemini rejected the API key (" + status + "). Check GEMINI_API_KEY.";
            case 404 -> "Gemini model not found (404). Change gemini.model in application.properties.";
            case 429 -> "Gemini rate limit hit (429). Wait a minute and try again.";
            case 500, 503 -> "Gemini is busy right now (" + status + "). Please try again in a moment.";
            default -> "Gemini returned an error (HTTP " + status + ").";
        };
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}