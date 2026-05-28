package com.github.ganchuanman.codememo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.UIUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

final class RecordCodeContextDialog extends DialogWrapper {
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final JBTextArea noteArea = new JBTextArea(9, 80);

    RecordCodeContextDialog(Project project, String relativePath, String selectedCode) {
        super(project);
        setTitle("Record code context");
        setOKButtonText("Record");

        JBTextField pathField = new JBTextField(relativePath);
        pathField.setEditable(false);
        pathField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JBTextArea codeArea = new JBTextArea(selectedCode, 6, 80);
        codeArea.setEditable(false);
        codeArea.setLineWrap(false);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        noteArea.setFont(UIUtil.getLabelFont().deriveFont(14f));
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);

        JPanel pathPanel = new JPanel(new BorderLayout(0, 4));
        pathPanel.add(new JBLabel("File Path"), BorderLayout.NORTH);
        pathPanel.add(pathField, BorderLayout.CENTER);

        JPanel codePanel = new JPanel(new BorderLayout(0, 4));
        codePanel.add(new JBLabel("Selected Code"), BorderLayout.NORTH);
        codePanel.add(new JBScrollPane(codeArea), BorderLayout.CENTER);

        JPanel notePanel = new JPanel(new BorderLayout(0, 4));
        notePanel.add(new JBLabel("What to do"), BorderLayout.NORTH);
        notePanel.add(new JBScrollPane(noteArea), BorderLayout.CENTER);

        GridBagConstraints pathConstraints = new GridBagConstraints();
        pathConstraints.gridx = 0;
        pathConstraints.gridy = 0;
        pathConstraints.weightx = 1;
        pathConstraints.fill = GridBagConstraints.HORIZONTAL;
        pathConstraints.insets = new Insets(0, 0, 8, 0);
        panel.add(pathPanel, pathConstraints);

        GridBagConstraints codeConstraints = new GridBagConstraints();
        codeConstraints.gridx = 0;
        codeConstraints.gridy = 1;
        codeConstraints.weightx = 1;
        codeConstraints.weighty = 0.35;
        codeConstraints.fill = GridBagConstraints.BOTH;
        codeConstraints.insets = new Insets(0, 0, 8, 0);
        panel.add(codePanel, codeConstraints);

        GridBagConstraints noteConstraints = new GridBagConstraints();
        noteConstraints.gridx = 0;
        noteConstraints.gridy = 2;
        noteConstraints.weightx = 1;
        noteConstraints.weighty = 0.65;
        noteConstraints.fill = GridBagConstraints.BOTH;
        panel.add(notePanel, noteConstraints);

        panel.setPreferredSize(new Dimension(720, 460));

        init();
    }

    String getNoteText() {
        return noteArea.getText();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return panel;
    }
}
