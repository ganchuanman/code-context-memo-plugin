package com.github.aaronoho.codememo;

final class CodeMemoAiConfig {
    final String endpoint;
    final String model;
    final String apiKey;
    final CodeMemoPromptLanguage promptLanguage;
    final String organizePromptZh;
    final String organizePromptEn;
    final String optimizeBackgroundPromptZh;
    final String optimizeBackgroundPromptEn;

    CodeMemoAiConfig(
            String endpoint,
            String model,
            String apiKey,
            CodeMemoPromptLanguage promptLanguage,
            String organizePromptZh,
            String organizePromptEn,
            String optimizeBackgroundPromptZh,
            String optimizeBackgroundPromptEn
    ) {
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKey;
        this.promptLanguage = promptLanguage;
        this.organizePromptZh = organizePromptZh;
        this.organizePromptEn = organizePromptEn;
        this.optimizeBackgroundPromptZh = optimizeBackgroundPromptZh;
        this.optimizeBackgroundPromptEn = optimizeBackgroundPromptEn;
    }

    boolean isComplete() {
        return !endpoint.isBlank() && !model.isBlank() && !apiKey.isBlank();
    }

    String getSystemPrompt(CodeMemoAiOperation operation) {
        return switch (operation) {
            case ORGANIZE_PROMPT -> promptLanguage == CodeMemoPromptLanguage.EN ? organizePromptEn : organizePromptZh;
            case OPTIMIZE_BACKGROUND -> promptLanguage == CodeMemoPromptLanguage.EN
                    ? optimizeBackgroundPromptEn
                    : optimizeBackgroundPromptZh;
        };
    }
}
