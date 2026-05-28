package com.github.ganchuanman.codememo;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

public final class CodeMemoToolWindowFactory implements ToolWindowFactory {
    private static final int BACKGROUND_COLLAPSED_HEIGHT = 116;
    private static final int BACKGROUND_EXPANDED_HEIGHT = 220;
    private static final String BACKGROUND_HINT = "Add goals, symptoms, expected results, and constraints so AI can organize the memo more accurately.";
    private static final String MEMO_HINT = "Select code, right-click \"Record code context\", add \"What to do\", then copy or organize the memo.";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        CodeMemoStateService stateService = project.getService(CodeMemoStateService.class);
        JBTextArea backgroundArea = new JBTextArea(5, 40);
        backgroundArea.setLineWrap(true);
        backgroundArea.setWrapStyleWord(true);
        backgroundArea.setEditable(false);
        backgroundArea.setFocusable(false);
        backgroundArea.setOpaque(false);
        backgroundArea.setBorder(JBUI.Borders.empty(6, 8));
        updateBackgroundDisplay(backgroundArea, stateService.getBackgroundText());
        CodeMemoTextPane memoArea = new CodeMemoTextPane(stateService.getMemoText());
        JButton organizeButton = new JButton(CodeMemoAiOperation.ORGANIZE_PROMPT.buttonText);
        organizeButton.putClientProperty("JButton.buttonType", "default");
        JButton copyButton = new JButton("Copy", AllIcons.General.Copy);
        JButton historyButton = new JButton("History", AllIcons.Vcs.History);
        JButton settingsButton = new JButton(AllIcons.General.Gear);
        settingsButton.setToolTipText("AI Settings");
        settingsButton.setPreferredSize(new Dimension(30, 28));
        JButton clearButton = new JButton("Clear", AllIcons.General.Delete);
        JButton editBackgroundButton = new JButton("Edit", AllIcons.General.Inline_edit);
        JButton toggleBackgroundButton = new JButton("Expand", AllIcons.General.ChevronDown);
        styleCompactButton(copyButton);
        styleCompactButton(historyButton);
        styleCompactButton(clearButton);
        styleCompactButton(editBackgroundButton);
        styleCompactButton(toggleBackgroundButton);
        clearButton.setEnabled(!stateService.getMemoText().isBlank());
        historyButton.setEnabled(stateService.hasHistory());

        JBLabel memoHintLabel = new JBLabel(MEMO_HINT);
        memoHintLabel.setForeground(UIUtil.getContextHelpForeground());
        memoHintLabel.setBorder(JBUI.Borders.empty(6, 8));
        memoHintLabel.setVisible(stateService.getMemoText().isBlank());

        memoArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                stateService.setMemoText(memoArea.getText());
                clearButton.setEnabled(!memoArea.getText().isBlank());
                memoHintLabel.setVisible(memoArea.getText().isBlank());
                SwingUtilities.invokeLater(memoArea::applyMemoStyles);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                stateService.setMemoText(memoArea.getText());
                clearButton.setEnabled(!memoArea.getText().isBlank());
                memoHintLabel.setVisible(memoArea.getText().isBlank());
                SwingUtilities.invokeLater(memoArea::applyMemoStyles);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        memoArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                stateService.captureSnapshot("Manual memo edit");
            }
        });

        organizeButton.addActionListener(event ->
                runAiOperation(project, stateService, memoArea, CodeMemoAiOperation.ORGANIZE_PROMPT,
                        organizeButton));
        copyButton.addActionListener(event -> copyMemo(project, stateService));
        historyButton.addActionListener(event -> showHistory(project, stateService));
        settingsButton.addActionListener(event -> showAiSettings(project));

        clearButton.addActionListener(event -> {
            int result = Messages.showYesNoDialog(
                    project,
                    "Clear all Code Memo content?",
                    "Clear Memo",
                    "Clear",
                    "Cancel",
                    Messages.getWarningIcon()
            );
            if (result == Messages.YES) {
                stateService.captureSnapshot("Before clear");
                memoArea.setText("");
                stateService.setMemoText("");
                clearButton.setEnabled(false);
            }
        });

        Disposable contentDisposable = Disposer.newDisposable("Code Memo ToolWindow");
        stateService.addChangeListener(() -> {
            String latestText = stateService.getMemoText();
            if (!latestText.equals(memoArea.getText())) {
                memoArea.setText(latestText);
                memoArea.applyMemoStyles();
                memoArea.setCaretPosition(memoArea.getDocument().getLength());
                clearButton.setEnabled(!latestText.isBlank());
                memoHintLabel.setVisible(latestText.isBlank());
            }
            updateBackgroundDisplay(backgroundArea, stateService.getBackgroundText());
            historyButton.setEnabled(stateService.hasHistory());
        }, contentDisposable);

        JPanel toolbar = new JBPanel<>(new CodeMemoWrapLayout(FlowLayout.RIGHT, 4, 4));
        toolbar.add(organizeButton);
        toolbar.add(copyButton);
        toolbar.add(historyButton);
        toolbar.add(settingsButton);
        toolbar.add(clearButton);

        JBScrollPane backgroundScrollPane = new JBScrollPane(backgroundArea);
        backgroundScrollPane.setBorder(JBUI.Borders.empty());
        backgroundScrollPane.setPreferredSize(new Dimension(480, BACKGROUND_COLLAPSED_HEIGHT));
        boolean[] backgroundExpanded = {false};

        editBackgroundButton.addActionListener(event -> {
            CodeMemoBackgroundDialog dialog = new CodeMemoBackgroundDialog(project, stateService.getBackgroundText());
            if (!dialog.showAndGet()) {
                return;
            }
            String backgroundText = dialog.getBackgroundText();
            if (backgroundText.equals(stateService.getBackgroundText())) {
                return;
            }
            stateService.captureSnapshot("Before saving task background");
            stateService.setBackgroundText(backgroundText);
            stateService.captureSnapshot("Save task background");
            updateBackgroundDisplay(backgroundArea, backgroundText);
        });
        toggleBackgroundButton.addActionListener(event -> {
            backgroundExpanded[0] = !backgroundExpanded[0];
            toggleBackgroundButton.setText(backgroundExpanded[0] ? "Collapse" : "Expand");
            toggleBackgroundButton.setIcon(backgroundExpanded[0] ? AllIcons.General.ChevronUp : AllIcons.General.ChevronDown);
            updateBackgroundHeight(backgroundScrollPane, backgroundExpanded[0]);
        });

        JPanel backgroundTitlePanel = new JBPanel<>(new BorderLayout());
        JPanel backgroundActions = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        backgroundTitlePanel.setOpaque(false);
        backgroundActions.setOpaque(false);
        backgroundActions.add(editBackgroundButton);
        backgroundActions.add(toggleBackgroundButton);
        backgroundTitlePanel.add(new JBLabel("Task Background"), BorderLayout.WEST);
        backgroundTitlePanel.add(backgroundActions, BorderLayout.EAST);

        JPanel backgroundPanel = new JBPanel<>(new BorderLayout(0, 4));
        backgroundPanel.setOpaque(true);
        backgroundPanel.setBackground(new JBColor(0xF7F8FA, 0x2B2D30));
        backgroundPanel.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(8)
        ));
        backgroundPanel.add(backgroundTitlePanel, BorderLayout.NORTH);
        backgroundPanel.add(backgroundScrollPane, BorderLayout.CENTER);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(JBUI.Borders.empty(4, 4, 6, 4));
        headerPanel.add(toolbar, BorderLayout.NORTH);
        headerPanel.add(backgroundPanel, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout());
        JBScrollPane memoScrollPane = new JBScrollPane(memoArea);
        memoScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        memoScrollPane.setBorder(JBUI.Borders.emptyTop(1));
        JPanel memoPanel = new JPanel(new BorderLayout());
        memoPanel.add(memoHintLabel, BorderLayout.NORTH);
        memoPanel.add(memoScrollPane, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(520, 500));
        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(memoPanel, BorderLayout.CENTER);
        toolWindow.getComponent().setPreferredSize(new Dimension(520, 500));

        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        content.setDisposer(contentDisposable);
        toolWindow.getContentManager().addContent(content);
    }

    private static void runAiOperation(
            Project project,
            CodeMemoStateService stateService,
            CodeMemoTextPane memoArea,
            CodeMemoAiOperation operation,
            JButton organizeButton
    ) {
        String memoText = stateService.getMemoText();
        String backgroundText = stateService.getBackgroundText();
        if (memoText.isBlank()) {
            Messages.showInfoMessage(project, "Memo is empty. Record some code context first.", operation.buttonText);
            return;
        }

        CodeMemoAiSettingsService settingsService = ApplicationManager.getApplication()
                .getService(CodeMemoAiSettingsService.class);
        if (!ensureAiConfigured(project, settingsService)) {
            return;
        }

        CodeMemoAiConfig config = settingsService.getConfigSnapshot();
        String originalText = organizeButton.getText();
        organizeButton.setText("Organizing...");
        organizeButton.setEnabled(false);
        new Task.Backgroundable(project, operation.progressTitle, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    String result = CodeMemoAiClient.run(config, operation, backgroundText, memoText);
                    ApplicationManager.getApplication().invokeLater(() ->
                            finishAiOperation(project, stateService, memoArea, operation, result,
                                    organizeButton));
                } catch (Exception exception) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        resetOrganizeButton(organizeButton, originalText);
                        Messages.showErrorDialog(project, exception.getMessage(), operation.buttonText + " Failed");
                    });
                }
            }

            @Override
            public void onCancel() {
                ApplicationManager.getApplication().invokeLater(() ->
                        resetOrganizeButton(organizeButton, originalText));
            }
        }.queue();
    }

    static boolean ensureAiConfigured(Project project, CodeMemoAiSettingsService settingsService) {
        if (settingsService.getConfigSnapshot().isComplete()) {
            return true;
        }
        return showAiSettings(project);
    }

    private static boolean showAiSettings(Project project) {
        CodeMemoAiSettingsService settingsService = ApplicationManager.getApplication()
                .getService(CodeMemoAiSettingsService.class);
        CodeMemoAiSettingsDialog dialog = new CodeMemoAiSettingsDialog(project, settingsService);
        if (!dialog.showAndGet()) {
            return false;
        }
        settingsService.update(
                dialog.getEndpoint(),
                dialog.getModel(),
                dialog.getApiKey(),
                dialog.isApiKeyChanged(),
                dialog.getPromptLanguage(),
                dialog.getOrganizePromptZh(),
                dialog.getOrganizePromptEn(),
                dialog.getOptimizeBackgroundPromptZh(),
                dialog.getOptimizeBackgroundPromptEn()
        );
        if (!settingsService.getConfigSnapshot().isComplete()) {
            Messages.showWarningDialog(project, "Endpoint, model, and API Key are required.", "AI Settings Incomplete");
            return false;
        }
        return true;
    }

    private static void showHistory(Project project, CodeMemoStateService stateService) {
        if (!stateService.hasHistory()) {
            Messages.showInfoMessage(project, "No history snapshots are available.", "History Snapshots");
            return;
        }

        CodeMemoHistoryDialog dialog = new CodeMemoHistoryDialog(project, stateService);
        if (!dialog.showAndGet()) {
            return;
        }
        stateService.restoreSnapshot(dialog.getSelectedSnapshot());
    }

    private static void showAiResult(
            Project project,
            CodeMemoStateService stateService,
            CodeMemoTextPane memoArea,
            CodeMemoAiOperation operation,
            String result
    ) {
        CodeMemoAiResultDialog dialog = new CodeMemoAiResultDialog(project, operation.resultTitle, result);
        if (!dialog.showAndGet()) {
            return;
        }
        String resultText = dialog.getResultText();
        stateService.captureSnapshot("Before AI organize");
        stateService.setMemoText(resultText);
        stateService.captureSnapshot("AI organize write-back");
        memoArea.setText(resultText);
        memoArea.applyMemoStyles();
        memoArea.setCaretPosition(0);
    }

    private static void finishAiOperation(
            Project project,
            CodeMemoStateService stateService,
            CodeMemoTextPane memoArea,
            CodeMemoAiOperation operation,
            String result,
            JButton organizeButton
    ) {
        resetOrganizeButton(organizeButton, operation.buttonText);
        showAiResult(project, stateService, memoArea, operation, result);
    }

    private static void copyMemo(Project project, CodeMemoStateService stateService) {
        String copyText = stateService.getMemoText();
        if (copyText.isBlank()) {
            Messages.showInfoMessage(project, "Nothing to copy.", "Copy");
            return;
        }
        StringSelection selection = new StringSelection(copyText);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        com.intellij.openapi.wm.StatusBar.Info.set("Code Memo copied", project);
    }

    private static void updateBackgroundHeight(JBScrollPane scrollPane, boolean expanded) {
        int height = expanded ? BACKGROUND_EXPANDED_HEIGHT : BACKGROUND_COLLAPSED_HEIGHT;
        scrollPane.setPreferredSize(new Dimension(scrollPane.getPreferredSize().width, height));
        scrollPane.revalidate();
        JComponent parent = (JComponent) scrollPane.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    private static void updateBackgroundDisplay(JBTextArea backgroundArea, String backgroundText) {
        if (backgroundText == null || backgroundText.isBlank()) {
            backgroundArea.setText(BACKGROUND_HINT);
            backgroundArea.setForeground(UIUtil.getContextHelpForeground());
        } else {
            backgroundArea.setText(backgroundText);
            backgroundArea.setForeground(UIUtil.getTextFieldForeground());
        }
        backgroundArea.setCaretPosition(0);
    }

    private static void styleCompactButton(JButton button) {
        button.setMargin(JBUI.insets(0, 8));
    }

    private static void resetOrganizeButton(JButton organizeButton, String text) {
        organizeButton.setText(text);
        organizeButton.setEnabled(true);
    }
}
