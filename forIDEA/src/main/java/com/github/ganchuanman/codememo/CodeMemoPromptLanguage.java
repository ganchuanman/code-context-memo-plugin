package com.github.ganchuanman.codememo;

enum CodeMemoPromptLanguage {
    ZH("zh", "Chinese"),
    EN("en", "English");

    final String id;
    private final String displayName;

    CodeMemoPromptLanguage(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    static CodeMemoPromptLanguage fromId(String id) {
        return EN.id.equals(id) ? EN : ZH;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
