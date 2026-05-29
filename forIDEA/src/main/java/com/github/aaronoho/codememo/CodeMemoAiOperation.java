package com.github.aaronoho.codememo;

enum CodeMemoAiOperation {
    ORGANIZE_PROMPT(
            "Organize Memo",
            "Organizing memo",
            "Organized Memo"
    ),
    OPTIMIZE_BACKGROUND(
            "Optimize Task Background",
            "Optimizing task background",
            "Optimized Task Background"
    );

    static final String ORGANIZE_PROMPT_ZH =
            "你是面向 Code Agent 的备忘录整理器。用户输入包含“Task Background”和“Memo”。基于任务背景整理每条记录中的“要做什么”，让它成为可以直接给代码 agent 执行的中文提示词。必须使用中文备忘录字段名：每条记录都使用“文件:”、“行号:”、“关键代码:”、“要做什么:”字段；如果原记录包含“位置:”，必须保留在“行号:”之后、“关键代码:”之前。关键代码继续放在 Markdown fenced code block 中。字段之间不要插入空行；多条记录之间只保留一个空行，不要输出装饰性分割线。不要新增“Task Background”字段，要把必要背景融入每条记录的“要做什么”。保留文件路径、行号、位置和关键代码，不要编造不存在的信息。只输出整理后的备忘录正文，不要输出额外说明。";
    static final String ORGANIZE_PROMPT_EN =
            "You are a memo organizer for Code Agent. The user input contains \"Task Background\" and \"Memo\". Use the task background to refine each record's \"What to do\" section into an actionable English prompt for a code agent. Keep the English memo field names: every record must use the fields \"File:\", \"Line:\", \"Key Code:\", and \"What to do:\"; if the original record contains \"Location:\", keep it after \"Line:\" and before \"Key Code:\". Keep code in Markdown fenced code blocks. Do not insert blank lines between fields. Leave one blank line between records, and do not output decorative separator lines. Do not add a \"Task Background\" field; fold necessary background into each \"What to do\" section. Preserve file paths, line numbers, locations, and code. Do not invent missing information. Output only the organized memo body.";
    static final String OPTIMIZE_BACKGROUND_PROMPT_ZH =
            "你是代码任务背景整理助手。把用户输入的任务背景改写成更适合后续交给代码 agent 使用的中文描述。要求：保留事实，不编造信息；突出目标、现象、期望结果、约束和已知线索；语言简洁、结构清晰；只输出优化后的任务背景正文，不要输出额外说明。";
    static final String OPTIMIZE_BACKGROUND_PROMPT_EN =
            "You are a code task background editor. Rewrite the user input into a clearer English task background for later code-agent work. Preserve facts and do not invent information. Highlight goals, symptoms, expected results, constraints, and known clues. Keep it concise and structured. Output only the optimized task background body.";

    final String buttonText;
    final String progressTitle;
    final String resultTitle;

    CodeMemoAiOperation(String buttonText, String progressTitle, String resultTitle) {
        this.buttonText = buttonText;
        this.progressTitle = progressTitle;
        this.resultTitle = resultTitle;
    }

    String buildUserPrompt(String backgroundText, String memoText) {
        String background = backgroundText == null || backgroundText.isBlank() ? "None" : backgroundText.strip();
        return "Task Background:\n" + background + "\n\nMemo:\n" + memoText;
    }

    String buildBackgroundPrompt(String backgroundText) {
        return backgroundText == null ? "" : backgroundText.strip();
    }

    static String defaultPrompt(CodeMemoAiOperation operation, CodeMemoPromptLanguage language) {
        return switch (operation) {
            case ORGANIZE_PROMPT -> language == CodeMemoPromptLanguage.EN ? ORGANIZE_PROMPT_EN : ORGANIZE_PROMPT_ZH;
            case OPTIMIZE_BACKGROUND -> language == CodeMemoPromptLanguage.EN
                    ? OPTIMIZE_BACKGROUND_PROMPT_EN
                    : OPTIMIZE_BACKGROUND_PROMPT_ZH;
        };
    }
}
