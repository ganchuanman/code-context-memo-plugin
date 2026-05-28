package com.github.ganchuanman.codememo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;

final class CodeMemoAiSettingsDialog extends DialogWrapper {
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final JBTextField endpointField = new JBTextField();
    private final JBTextField modelField = new JBTextField();
    private final JBTextField apiKeyField = new JBTextField();
    private final JComboBox<CodeMemoPromptLanguage> promptLanguageBox = new JComboBox<>(CodeMemoPromptLanguage.values());
    private final JBTextArea organizePromptArea = new JBTextArea(7, 80);
    private final JBTextArea optimizeBackgroundPromptArea = new JBTextArea(7, 80);
    private final String maskedApiKey;
    private CodeMemoPromptLanguage currentLanguage;
    private String organizePromptZh;
    private String organizePromptEn;
    private String optimizeBackgroundPromptZh;
    private String optimizeBackgroundPromptEn;

    CodeMemoAiSettingsDialog(Project project, CodeMemoAiSettingsService settingsService) {
        super(project);
        setTitle("AI Settings");
        setOKButtonText("Save");

        String apiKey = settingsService.getApiKey();
        maskedApiKey = maskApiKey(apiKey);
        endpointField.setText(settingsService.getEndpoint());
        modelField.setText(settingsService.getModel());
        apiKeyField.setText(maskedApiKey);
        currentLanguage = settingsService.getPromptLanguage();
        organizePromptZh = settingsService.getOrganizePromptZh();
        organizePromptEn = settingsService.getOrganizePromptEn();
        optimizeBackgroundPromptZh = settingsService.getOptimizeBackgroundPromptZh();
        optimizeBackgroundPromptEn = settingsService.getOptimizeBackgroundPromptEn();
        promptLanguageBox.setSelectedItem(currentLanguage);
        endpointField.setPreferredSize(new Dimension(560, endpointField.getPreferredSize().height));
        configurePromptArea(organizePromptArea);
        configurePromptArea(optimizeBackgroundPromptArea);
        loadPromptFields(currentLanguage);

        promptLanguageBox.addItemListener(event -> {
            if (event.getStateChange() != ItemEvent.SELECTED) {
                return;
            }
            savePromptFields();
            currentLanguage = (CodeMemoPromptLanguage) event.getItem();
            loadPromptFields(currentLanguage);
        });

        addRow(0, "Endpoint (chat/completions)", endpointField);
        addRow(1, "Model", modelField);
        addRow(2, "API Key", apiKeyField);
        addRow(3, "Prompt Language", promptLanguageBox);
        addPromptRow(4, "Organize Memo Prompt", organizePromptArea, CodeMemoAiOperation.ORGANIZE_PROMPT);
        addPromptRow(5, "Optimize Task Background Prompt", optimizeBackgroundPromptArea,
                CodeMemoAiOperation.OPTIMIZE_BACKGROUND);

        init();
    }

    String getEndpoint() {
        return endpointField.getText();
    }

    String getModel() {
        return modelField.getText();
    }

    String getApiKey() {
        return apiKeyField.getText().trim();
    }

    boolean isApiKeyChanged() {
        return !apiKeyField.getText().equals(maskedApiKey);
    }

    CodeMemoPromptLanguage getPromptLanguage() {
        savePromptFields();
        return currentLanguage;
    }

    String getOrganizePromptZh() {
        savePromptFields();
        return organizePromptZh;
    }

    String getOrganizePromptEn() {
        savePromptFields();
        return organizePromptEn;
    }

    String getOptimizeBackgroundPromptZh() {
        savePromptFields();
        return optimizeBackgroundPromptZh;
    }

    String getOptimizeBackgroundPromptEn() {
        savePromptFields();
        return optimizeBackgroundPromptEn;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return panel;
    }

    private void addRow(int row, String label, JComponent component) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, 0, 8, 8);
        panel.add(new JBLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(0, 0, 8, 0);
        panel.add(component, fieldConstraints);
    }

    private void addPromptRow(int row, String label, JBTextArea promptArea, CodeMemoAiOperation operation) {
        JButton resetButton = new JButton("Reset Default");
        resetButton.addActionListener(event ->
                promptArea.setText(CodeMemoAiOperation.defaultPrompt(operation, currentLanguage)));

        JPanel labelPanel = new JPanel(new BorderLayout(0, 4));
        labelPanel.add(new JBLabel(label), BorderLayout.NORTH);
        labelPanel.add(resetButton, BorderLayout.CENTER);

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.NORTHWEST;
        labelConstraints.insets = new Insets(0, 0, 8, 8);
        panel.add(labelPanel, labelConstraints);

        JBScrollPane scrollPane = new JBScrollPane(promptArea);
        scrollPane.setPreferredSize(new Dimension(560, 150));
        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.weighty = 1;
        fieldConstraints.fill = GridBagConstraints.BOTH;
        fieldConstraints.insets = new Insets(0, 0, 8, 0);
        panel.add(scrollPane, fieldConstraints);
    }

    private static void configurePromptArea(JBTextArea promptArea) {
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
    }

    private void savePromptFields() {
        if (currentLanguage == CodeMemoPromptLanguage.EN) {
            organizePromptEn = organizePromptArea.getText();
            optimizeBackgroundPromptEn = optimizeBackgroundPromptArea.getText();
        } else {
            organizePromptZh = organizePromptArea.getText();
            optimizeBackgroundPromptZh = optimizeBackgroundPromptArea.getText();
        }
    }

    private void loadPromptFields(CodeMemoPromptLanguage language) {
        if (language == CodeMemoPromptLanguage.EN) {
            organizePromptArea.setText(organizePromptEn);
            optimizeBackgroundPromptArea.setText(optimizeBackgroundPromptEn);
        } else {
            organizePromptArea.setText(organizePromptZh);
            optimizeBackgroundPromptArea.setText(optimizeBackgroundPromptZh);
        }
        organizePromptArea.setCaretPosition(0);
        optimizeBackgroundPromptArea.setCaretPosition(0);
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        String trimmed = apiKey.trim();
        int prefixLength = Math.min(8, Math.max(2, trimmed.length() / 3));
        int suffixLength = Math.min(4, Math.max(0, trimmed.length() - prefixLength));
        if (trimmed.length() <= prefixLength + suffixLength) {
            return trimmed;
        }
        return trimmed.substring(0, prefixLength)
                + "*".repeat(trimmed.length() - prefixLength - suffixLength)
                + trimmed.substring(trimmed.length() - suffixLength);
    }
}
