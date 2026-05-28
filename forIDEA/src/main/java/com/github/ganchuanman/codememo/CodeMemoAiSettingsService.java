package com.github.ganchuanman.codememo;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
@State(name = "CodeMemoAiSettings", storages = @Storage("codeMemoAiSettings.xml"))
public final class CodeMemoAiSettingsService implements PersistentStateComponent<CodeMemoAiSettingsService.State> {
    private static final String LEGACY_OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_ENDPOINT = "https://api.deepseek.com/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-v4-pro";
    private static final CredentialAttributes API_KEY_ATTRIBUTES =
            new CredentialAttributes("CodeContextMemo.AiApiKey", "default");

    private State state = new State();

    public static final class State {
        public String endpoint = DEFAULT_ENDPOINT;
        public String model = DEFAULT_MODEL;
        public String promptLanguage = CodeMemoPromptLanguage.ZH.id;
        public String organizePrompt = CodeMemoAiOperation.ORGANIZE_PROMPT_ZH;
        public String optimizeBackgroundPrompt = CodeMemoAiOperation.OPTIMIZE_BACKGROUND_PROMPT_ZH;
        public String organizePromptZh = CodeMemoAiOperation.ORGANIZE_PROMPT_ZH;
        public String organizePromptEn = CodeMemoAiOperation.ORGANIZE_PROMPT_EN;
        public String optimizeBackgroundPromptZh = CodeMemoAiOperation.OPTIMIZE_BACKGROUND_PROMPT_ZH;
        public String optimizeBackgroundPromptEn = CodeMemoAiOperation.OPTIMIZE_BACKGROUND_PROMPT_EN;
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    CodeMemoAiConfig getConfigSnapshot() {
        return new CodeMemoAiConfig(
                getEndpoint(),
                getModel(),
                getApiKey(),
                getPromptLanguage(),
                getOrganizePromptZh(),
                getOrganizePromptEn(),
                getOptimizeBackgroundPromptZh(),
                getOptimizeBackgroundPromptEn()
        );
    }

    String getEndpoint() {
        if (state.endpoint == null || state.endpoint.isBlank()) {
            return DEFAULT_ENDPOINT;
        }
        String endpoint = state.endpoint.trim();
        return LEGACY_OPENAI_ENDPOINT.equals(endpoint) ? DEFAULT_ENDPOINT : endpoint;
    }

    String getModel() {
        return state.model == null || state.model.isBlank() ? DEFAULT_MODEL : state.model.trim();
    }

    String getApiKey() {
        Credentials credentials = PasswordSafe.getInstance().get(API_KEY_ATTRIBUTES);
        if (credentials == null || credentials.getPasswordAsString() == null) {
            return "";
        }
        return credentials.getPasswordAsString();
    }

    CodeMemoPromptLanguage getPromptLanguage() {
        return CodeMemoPromptLanguage.fromId(state.promptLanguage);
    }

    String getOrganizePromptZh() {
        return normalizePrompt(
                state.organizePromptZh,
                normalizePrompt(state.organizePrompt, CodeMemoAiOperation.ORGANIZE_PROMPT_ZH)
        );
    }

    String getOrganizePromptEn() {
        return normalizePrompt(state.organizePromptEn, CodeMemoAiOperation.ORGANIZE_PROMPT_EN);
    }

    String getOptimizeBackgroundPromptZh() {
        return normalizePrompt(
                state.optimizeBackgroundPromptZh,
                normalizePrompt(state.optimizeBackgroundPrompt, CodeMemoAiOperation.OPTIMIZE_BACKGROUND_PROMPT_ZH)
        );
    }

    String getOptimizeBackgroundPromptEn() {
        return normalizePrompt(state.optimizeBackgroundPromptEn, CodeMemoAiOperation.OPTIMIZE_BACKGROUND_PROMPT_EN);
    }

    void update(
            String endpoint,
            String model,
            String apiKey,
            boolean updateApiKey,
            CodeMemoPromptLanguage promptLanguage,
            String organizePromptZh,
            String organizePromptEn,
            String optimizeBackgroundPromptZh,
            String optimizeBackgroundPromptEn
    ) {
        state.endpoint = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint.trim();
        state.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        state.promptLanguage = (promptLanguage == null ? CodeMemoPromptLanguage.ZH : promptLanguage).id;
        state.organizePromptZh = normalizePrompt(organizePromptZh, CodeMemoAiOperation.ORGANIZE_PROMPT_ZH);
        state.organizePromptEn = normalizePrompt(organizePromptEn, CodeMemoAiOperation.ORGANIZE_PROMPT_EN);
        state.optimizeBackgroundPromptZh = normalizePrompt(
                optimizeBackgroundPromptZh,
                CodeMemoAiOperation.OPTIMIZE_BACKGROUND_PROMPT_ZH
        );
        state.optimizeBackgroundPromptEn = normalizePrompt(
                optimizeBackgroundPromptEn,
                CodeMemoAiOperation.OPTIMIZE_BACKGROUND_PROMPT_EN
        );
        if (updateApiKey) {
            PasswordSafe.getInstance().set(API_KEY_ATTRIBUTES, new Credentials("default", apiKey == null ? "" : apiKey.trim()));
        }
    }

    private static String normalizePrompt(String prompt, String fallback) {
        return prompt == null || prompt.isBlank() ? fallback : prompt.strip();
    }
}
