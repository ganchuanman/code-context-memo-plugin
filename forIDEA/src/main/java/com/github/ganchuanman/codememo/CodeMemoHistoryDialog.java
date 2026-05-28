package com.github.ganchuanman.codememo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class CodeMemoHistoryDialog extends DialogWrapper {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final Project project;
    private final CodeMemoStateService stateService;
    private final DefaultListModel<SnapshotItem> model = new DefaultListModel<>();
    private final JList<SnapshotItem> snapshotList;
    private final JBTextArea backgroundPreview = new JBTextArea(6, 48);
    private final CodeMemoTextPane memoPreview = new CodeMemoTextPane("");
    private Action deleteAction;

    CodeMemoHistoryDialog(Project project, CodeMemoStateService stateService) {
        super(project);
        this.project = project;
        this.stateService = stateService;
        setTitle("History Snapshots");
        setOKButtonText("Restore");

        loadSnapshots();

        snapshotList = new JList<>(model);
        snapshotList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        snapshotList.addListSelectionListener(event -> {
            updatePreview();
            updateActions();
        });

        backgroundPreview.setEditable(false);
        backgroundPreview.setLineWrap(true);
        backgroundPreview.setWrapStyleWord(true);
        backgroundPreview.setBorder(JBUI.Borders.empty(6));
        memoPreview.setEditable(false);
        memoPreview.setFocusable(false);

        init();
        if (!model.isEmpty()) {
            snapshotList.setSelectedIndex(0);
        }
        setOKActionEnabled(!model.isEmpty());
    }

    CodeMemoStateService.MemoSnapshot getSelectedSnapshot() {
        SnapshotItem item = snapshotList.getSelectedValue();
        return item == null ? null : item.snapshot.copy();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        JBScrollPane listScrollPane = new JBScrollPane(snapshotList);
        listScrollPane.setPreferredSize(new Dimension(220, 460));
        panel.add(listScrollPane, BorderLayout.WEST);

        JPanel previewPanel = new JPanel(new BorderLayout(0, 8));
        previewPanel.add(wrapPreview("Task Background", new JBScrollPane(backgroundPreview), 140), BorderLayout.NORTH);
        previewPanel.add(wrapPreview("Memo", new JBScrollPane(memoPreview), 312), BorderLayout.CENTER);
        panel.add(previewPanel, BorderLayout.CENTER);
        return panel;
    }

    @Override
    protected Action @NotNull [] createLeftSideActions() {
        deleteAction = new DialogWrapperAction("Delete Record") {
            @Override
            protected void doAction(ActionEvent event) {
                deleteSelectedSnapshot();
            }
        };
        updateActions();
        return new Action[]{deleteAction};
    }

    private JPanel wrapPreview(String title, JBScrollPane scrollPane, int height) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JBLabel(title), BorderLayout.NORTH);
        scrollPane.setPreferredSize(new Dimension(560, height));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void updatePreview() {
        SnapshotItem item = snapshotList.getSelectedValue();
        if (item == null) {
            backgroundPreview.setText("");
            memoPreview.setText("");
            return;
        }

        backgroundPreview.setText(item.snapshot.getBackgroundText());
        backgroundPreview.setCaretPosition(0);
        memoPreview.setText(item.snapshot.getMemoText());
        memoPreview.applyMemoStyles();
        memoPreview.setCaretPosition(0);
    }

    private void deleteSelectedSnapshot() {
        int selectedIndex = snapshotList.getSelectedIndex();
        SnapshotItem item = snapshotList.getSelectedValue();
        if (selectedIndex < 0 || item == null) {
            return;
        }

        int result = Messages.showYesNoDialog(
                project,
                "Delete this history record?",
                "Delete History Record",
                "Delete",
                "Cancel",
                Messages.getWarningIcon()
        );
        if (result != Messages.YES || !stateService.deleteSnapshot(item.snapshot)) {
            return;
        }

        model.remove(selectedIndex);
        if (!model.isEmpty()) {
            snapshotList.setSelectedIndex(Math.min(selectedIndex, model.size() - 1));
        }
        updatePreview();
        updateActions();
    }

    private void loadSnapshots() {
        List<CodeMemoStateService.MemoSnapshot> snapshots = stateService.getHistory();
        for (int index = snapshots.size() - 1; index >= 0; index--) {
            model.addElement(new SnapshotItem(snapshots.get(index)));
        }
    }

    private void updateActions() {
        boolean hasSelection = snapshotList != null && snapshotList.getSelectedIndex() >= 0;
        setOKActionEnabled(hasSelection);
        if (deleteAction != null) {
            deleteAction.setEnabled(hasSelection);
        }
    }

    private static final class SnapshotItem {
        private final CodeMemoStateService.MemoSnapshot snapshot;

        private SnapshotItem(CodeMemoStateService.MemoSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public String toString() {
            String label = snapshot.label == null || snapshot.label.isBlank() ? "Snapshot" : snapshot.label;
            String time = TIME_FORMATTER.format(Instant.ofEpochMilli(snapshot.createdAtMillis)
                    .atZone(ZoneId.systemDefault()));
            return time + "  " + label;
        }
    }
}
