package com.github.aaronoho.codememo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;

final class CodeMemoBackgroundDialog extends DialogWrapper {
    private final Project project;
    private final JBTextArea backgroundArea;
    private final JPanel panel = new JPanel(new BorderLayout(0, 8));
    private Action optimizeAction;

    CodeMemoBackgroundDialog(Project project, String backgroundText) {
        super(project);
        this.project = project;
        setTitle("Task Background");
        setOKButtonText("Save");

        backgroundArea = new JBTextArea(backgroundText, 16, 80);
        backgroundArea.setLineWrap(true);
        backgroundArea.setWrapStyleWord(true);
        backgroundArea.setCaretPosition(0);

        JBScrollPane scrollPane = new JBScrollPane(backgroundArea);
        scrollPane.setPreferredSize(new Dimension(720, 420));
        panel.add(scrollPane, BorderLayout.CENTER);

        init();
    }

    String getBackgroundText() {
        return backgroundArea.getText();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return panel;
    }

    @Override
    protected Action @NotNull [] createLeftSideActions() {
        optimizeAction = new DialogWrapperAction(CodeMemoAiOperation.OPTIMIZE_BACKGROUND.buttonText) {
            @Override
            protected void doAction(ActionEvent event) {
                optimizeBackground();
            }
        };
        return new Action[]{optimizeAction};
    }

    private void optimizeBackground() {
        String backgroundText = backgroundArea.getText();
        if (backgroundText.isBlank()) {
            Messages.showInfoMessage(project, "Enter a task background first.", CodeMemoAiOperation.OPTIMIZE_BACKGROUND.buttonText);
            return;
        }

        CodeMemoAiSettingsService settingsService = ApplicationManager.getApplication()
                .getService(CodeMemoAiSettingsService.class);
        if (!CodeMemoToolWindowFactory.ensureAiConfigured(project, settingsService)) {
            return;
        }

        CodeMemoAiConfig config = settingsService.getConfigSnapshot();
        setOptimizeActionEnabled(false);
        new Task.Backgroundable(project, CodeMemoAiOperation.OPTIMIZE_BACKGROUND.progressTitle, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    String result = CodeMemoAiClient.runBackgroundOptimization(config, backgroundText);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        setOptimizeActionEnabled(true);
                        showOptimizedBackground(result);
                    });
                } catch (Exception exception) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        setOptimizeActionEnabled(true);
                        Messages.showErrorDialog(project, exception.getMessage(),
                                CodeMemoAiOperation.OPTIMIZE_BACKGROUND.buttonText + " Failed");
                    });
                }
            }
        }.queue();
    }

    private void showOptimizedBackground(String result) {
        CodeMemoBackgroundResultDialog dialog = new CodeMemoBackgroundResultDialog(project, result);
        if (!dialog.showAndGet()) {
            return;
        }
        backgroundArea.setText(dialog.getBackgroundText());
        backgroundArea.setCaretPosition(0);
    }

    private void setOptimizeActionEnabled(boolean enabled) {
        if (optimizeAction != null) {
            optimizeAction.setEnabled(enabled);
            optimizeAction.putValue(Action.NAME, enabled
                    ? CodeMemoAiOperation.OPTIMIZE_BACKGROUND.buttonText
                    : "Optimizing...");
        }
    }
}
