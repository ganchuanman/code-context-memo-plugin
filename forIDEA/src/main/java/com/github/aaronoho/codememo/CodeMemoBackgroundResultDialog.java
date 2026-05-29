package com.github.aaronoho.codememo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.awt.Dimension;

final class CodeMemoBackgroundResultDialog extends DialogWrapper {
    private final JBTextArea backgroundArea;

    CodeMemoBackgroundResultDialog(Project project, String backgroundText) {
        super(project);
        setTitle("Optimized Task Background");
        setOKButtonText("Use (editable)");

        backgroundArea = new JBTextArea(backgroundText, 16, 80);
        backgroundArea.setLineWrap(true);
        backgroundArea.setWrapStyleWord(true);
        backgroundArea.setCaretPosition(0);

        init();
    }

    String getBackgroundText() {
        return backgroundArea.getText();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBScrollPane scrollPane = new JBScrollPane(backgroundArea);
        scrollPane.setPreferredSize(new Dimension(720, 420));
        return scrollPane;
    }
}
