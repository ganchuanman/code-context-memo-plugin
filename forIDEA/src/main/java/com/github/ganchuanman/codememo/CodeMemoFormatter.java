package com.github.ganchuanman.codememo;

final class CodeMemoFormatter {
    private CodeMemoFormatter() {
    }

    static String formatEntry(
            String relativePath,
            String lineRange,
            String codeLocation,
            String selectedCode,
            String note,
            String extension,
            CodeMemoPromptLanguage promptLanguage
    ) {
        String language = extension == null || extension.isBlank() ? "" : extension;
        String fence = selectedCode.contains("```") ? "````" : "```";
        String codeBlock = selectedCode.endsWith("\n") ? selectedCode : selectedCode + "\n";
        boolean english = promptLanguage == CodeMemoPromptLanguage.EN;
        String fileLabel = english ? "File" : "文件";
        String lineLabel = english ? "Line" : "行号";
        String locationLabel = english ? "Location" : "位置";
        String keyCodeLabel = english ? "Key Code" : "关键代码";
        String taskLabel = english ? "What to do" : "要做什么";

        StringBuilder builder = new StringBuilder();
        builder.append(fileLabel).append(": ").append(relativePath).append("\n")
                .append(lineLabel).append(": ").append(lineRange).append("\n");
        if (codeLocation != null && !codeLocation.isBlank()) {
            builder.append(locationLabel).append(": ").append(codeLocation).append("\n");
        }
        return builder.append(keyCodeLabel).append(":\n")
                .append(fence).append(language).append("\n")
                .append(codeBlock)
                .append(fence).append("\n")
                .append(taskLabel).append(":\n")
                .append(note).append("\n")
                .toString();
    }
}
