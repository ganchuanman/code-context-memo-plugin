package com.github.aaronoho.codememo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;

final class CodeMemoAiResultDialog extends DialogWrapper {
    private final CodeMemoTextPane resultArea;

    CodeMemoAiResultDialog(Project project, String title, String resultText) {
        super(project);
        setTitle(title);
        setOKButtonText("Write Back Memo");
        resultArea = new CodeMemoTextPane(resultText);
        resultArea.setCaretPosition(0);
        resultArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(resultArea::applyMemoStyles);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(resultArea::applyMemoStyles);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        init();
    }

    String getResultText() {
        return resultArea.getText();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBScrollPane scrollPane = new JBScrollPane(resultArea);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(720, 500));
        return scrollPane;
    }
}
