package com.github.ganchuanman.codememo;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;

public final class RecordCodeContextAction extends AnAction implements DumbAware {
    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        Presentation presentation = event.getPresentation();
        presentation.setEnabledAndVisible(project != null
                && editor != null
                && editor.getSelectionModel().hasSelection());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        VirtualFile virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || editor == null || virtualFile == null) {
            return;
        }

        String selectedCode = editor.getSelectionModel().getSelectedText();
        if (selectedCode == null || selectedCode.isBlank()) {
            return;
        }

        String relativePath = getRelativePath(project, virtualFile);
        String lineRange = getLineRange(editor);
        String codeLocation = getCodeLocation(project, editor);
        CodeMemoPromptLanguage promptLanguage = ApplicationManager.getApplication()
                .getService(CodeMemoAiSettingsService.class)
                .getPromptLanguage();
        RecordCodeContextDialog dialog = new RecordCodeContextDialog(project, relativePath, selectedCode);
        if (!dialog.showAndGet()) {
            return;
        }

        String entry = CodeMemoFormatter.formatEntry(
                relativePath,
                lineRange,
                codeLocation,
                selectedCode,
                dialog.getNoteText(),
                virtualFile.getExtension(),
                promptLanguage
        );
        project.getService(CodeMemoStateService.class).appendEntry(entry);

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Code Memo");
        if (toolWindow != null) {
            toolWindow.activate(null);
        }
    }

    private static String getRelativePath(Project project, VirtualFile virtualFile) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return virtualFile.getPath();
        }

        try {
            Path base = Path.of(basePath).toAbsolutePath().normalize();
            Path file = Path.of(virtualFile.getPath()).toAbsolutePath().normalize();
            if (file.startsWith(base)) {
                return base.relativize(file).toString().replace(File.separatorChar, '/');
            }
        } catch (IllegalArgumentException ignored) {
        }
        return virtualFile.getPath();
    }

    private static String getLineRange(Editor editor) {
        Document document = editor.getDocument();
        int selectionStart = editor.getSelectionModel().getSelectionStart();
        int selectionEnd = editor.getSelectionModel().getSelectionEnd();
        int effectiveEnd = Math.max(selectionStart, selectionEnd - 1);
        int startLine = document.getLineNumber(selectionStart) + 1;
        int endLine = document.getLineNumber(effectiveEnd) + 1;
        return startLine == endLine ? String.valueOf(startLine) : startLine + "-" + endLine;
    }

    private static String getCodeLocation(Project project, Editor editor) {
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return "";
        }

        int textLength = editor.getDocument().getTextLength();
        if (textLength == 0) {
            return "";
        }

        int offset = Math.min(editor.getSelectionModel().getSelectionStart(), textLength - 1);
        PsiElement element = psiFile.findElementAt(offset);
        if (element == null && offset > 0) {
            element = psiFile.findElementAt(offset - 1);
        }

        String functionName = "";
        String className = "";
        for (PsiElement current = element; current != null && !(current instanceof PsiFile); current = current.getParent()) {
            if (!(current instanceof PsiNamedElement namedElement)) {
                continue;
            }

            String name = namedElement.getName();
            if (name == null || name.isBlank()) {
                continue;
            }

            String psiType = current.getClass().getSimpleName();
            if (functionName.isEmpty() && isFunctionLike(psiType)) {
                functionName = name;
            } else if (className.isEmpty() && isClassLike(psiType)) {
                className = name;
            }
        }

        if (!className.isEmpty() && !functionName.isEmpty()) {
            return className + "#" + functionName;
        }
        return !functionName.isEmpty() ? functionName : className;
    }

    private static boolean isFunctionLike(String psiType) {
        return psiType.contains("Method")
                || psiType.contains("Function")
                || psiType.contains("Constructor");
    }

    private static boolean isClassLike(String psiType) {
        return psiType.contains("Class")
                || psiType.contains("Object")
                || psiType.contains("Interface")
                || psiType.contains("Enum");
    }
}
