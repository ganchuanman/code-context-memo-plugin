package com.github.ganchuanman.codememo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class CodeMemoAiClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private CodeMemoAiClient() {
    }

    static String run(CodeMemoAiConfig config, CodeMemoAiOperation operation, String backgroundText, String memoText)
            throws IOException, InterruptedException {
        String userPrompt = operation.buildUserPrompt(backgroundText, memoText);
        return send(config, config.getSystemPrompt(operation), userPrompt);
    }

    static String runBackgroundOptimization(CodeMemoAiConfig config, String backgroundText)
            throws IOException, InterruptedException {
        CodeMemoAiOperation operation = CodeMemoAiOperation.OPTIMIZE_BACKGROUND;
        return send(config, config.getSystemPrompt(operation), operation.buildBackgroundPrompt(backgroundText));
    }

    private static String send(CodeMemoAiConfig config, String systemPrompt, String userPrompt)
            throws IOException, InterruptedException {
        String requestBody = "{"
                + "\"model\":\"" + escapeJson(config.model) + "\","
                + "\"temperature\":0.2,"
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + escapeJson(userPrompt) + "\"}"
                + "]"
                + "}";

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.endpoint))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = extractJsonString(response.body(), "message");
            if (message == null || message.isBlank()) {
                message = response.body();
            }
            throw new IOException("AI request failed, HTTP " + response.statusCode() + ": " + truncate(message));
        }

        String content = extractJsonString(response.body(), "content");
        if (content == null || content.isBlank()) {
            throw new IOException("AI response does not contain a content field");
        }
        return content.strip();
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static String extractJsonString(String json, String key) {
        String quotedKey = "\"" + key + "\"";
        int searchOffset = 0;
        while (searchOffset < json.length()) {
            int keyOffset = json.indexOf(quotedKey, searchOffset);
            if (keyOffset < 0) {
                return null;
            }
            int colonOffset = json.indexOf(':', keyOffset + quotedKey.length());
            if (colonOffset < 0) {
                return null;
            }
            int valueOffset = skipWhitespace(json, colonOffset + 1);
            if (valueOffset < json.length() && json.charAt(valueOffset) == '"') {
                return readJsonString(json, valueOffset);
            }
            searchOffset = keyOffset + quotedKey.length();
        }
        return null;
    }

    private static int skipWhitespace(String value, int offset) {
        int current = offset;
        while (current < value.length() && Character.isWhitespace(value.charAt(current))) {
            current++;
        }
        return current;
    }

    private static String readJsonString(String json, int quoteOffset) {
        StringBuilder builder = new StringBuilder();
        for (int i = quoteOffset + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                return builder.toString();
            }
            if (c != '\\') {
                builder.append(c);
                continue;
            }
            if (i + 1 >= json.length()) {
                return builder.toString();
            }
            char escaped = json.charAt(++i);
            switch (escaped) {
                case '"', '\\', '/' -> builder.append(escaped);
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (i + 4 < json.length()) {
                        builder.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                }
                default -> builder.append(escaped);
            }
        }
        return builder.toString();
    }

    private static String truncate(String value) {
        String compact = value.replace('\n', ' ').replace('\r', ' ').strip();
        return compact.length() <= 300 ? compact : compact.substring(0, 300) + "...";
    }
}
