package com.github.ganchuanman.codememo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service(Service.Level.PROJECT)
@State(name = "CodeMemoState", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class CodeMemoStateService implements PersistentStateComponent<CodeMemoStateService.MemoState> {
    private static final int MAX_HISTORY_SIZE = 20;

    private MemoState state = new MemoState();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public static final class MemoState {
        public String backgroundText = "";
        public String memoText = "";
        public List<MemoSnapshot> history = new ArrayList<>();
    }

    public static class MemoSnapshot {
        public String label = "";
        public long createdAtMillis;
        public String backgroundText = "";
        public String memoText = "";

        public MemoSnapshot() {
        }

        MemoSnapshot(String label, long createdAtMillis, String backgroundText, String memoText) {
            this.label = label == null ? "" : label;
            this.createdAtMillis = createdAtMillis;
            this.backgroundText = backgroundText == null ? "" : backgroundText;
            this.memoText = memoText == null ? "" : memoText;
        }

        String getBackgroundText() {
            return backgroundText == null ? "" : backgroundText;
        }

        String getMemoText() {
            return memoText == null ? "" : memoText;
        }

        MemoSnapshot copy() {
            return new MemoSnapshot(label, createdAtMillis, getBackgroundText(), getMemoText());
        }
    }

    @Override
    public @NotNull MemoState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull MemoState state) {
        this.state = state;
        history();
        trimHistory();
    }

    public String getMemoText() {
        return state.memoText == null ? "" : state.memoText;
    }

    public String getBackgroundText() {
        return state.backgroundText == null ? "" : state.backgroundText;
    }

    public void setBackgroundText(String backgroundText) {
        state.backgroundText = backgroundText == null ? "" : backgroundText;
    }

    public void setMemoText(String memoText) {
        state.memoText = memoText == null ? "" : memoText;
    }

    public void appendEntry(String entry) {
        String currentText = getMemoText();
        setMemoText(appendMemoEntry(currentText, entry));
        addSnapshot("Record code context", getBackgroundText(), getMemoText());
        notifyListeners();
    }

    public boolean hasHistory() {
        return !history().isEmpty();
    }

    public List<MemoSnapshot> getHistory() {
        trimHistory();
        List<MemoSnapshot> snapshots = new ArrayList<>();
        for (MemoSnapshot snapshot : history()) {
            snapshots.add(snapshot.copy());
        }
        return snapshots;
    }

    public void captureSnapshot(String label) {
        captureSnapshot(label, getBackgroundText(), getMemoText());
    }

    public void captureSnapshot(String label, String backgroundText, String memoText) {
        if (addSnapshot(label, backgroundText, memoText)) {
            notifyListeners();
        }
    }

    public void restoreSnapshot(MemoSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        setBackgroundText(snapshot.getBackgroundText());
        setMemoText(snapshot.getMemoText());
        notifyListeners();
    }

    public boolean deleteSnapshot(MemoSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }

        List<MemoSnapshot> history = history();
        for (int index = history.size() - 1; index >= 0; index--) {
            if (isSameSnapshot(history.get(index), snapshot)) {
                history.remove(index);
                notifyListeners();
                return true;
            }
        }
        return false;
    }

    public void addChangeListener(@NotNull Runnable listener, @NotNull Disposable parentDisposable) {
        listeners.add(listener);
        Disposer.register(parentDisposable, () -> listeners.remove(listener));
    }

    private void notifyListeners() {
        ApplicationManager.getApplication().invokeLater(() -> listeners.forEach(Runnable::run));
    }

    private List<MemoSnapshot> history() {
        if (state.history == null) {
            state.history = new ArrayList<>();
        }
        return state.history;
    }

    private static String appendMemoEntry(String currentText, String entry) {
        if (currentText == null || currentText.isBlank()) {
            return entry;
        }
        if (currentText.endsWith("\n\n")) {
            return currentText + entry;
        }
        return currentText + (currentText.endsWith("\n") ? "\n" : "\n\n") + entry;
    }

    private boolean addSnapshot(String label, String backgroundText, String memoText) {
        List<MemoSnapshot> history = history();
        String normalizedBackgroundText = backgroundText == null ? "" : backgroundText;
        String normalizedMemoText = memoText == null ? "" : memoText;
        if (normalizedBackgroundText.isBlank() && normalizedMemoText.isBlank()) {
            return false;
        }
        if (!history.isEmpty()) {
            MemoSnapshot lastSnapshot = history.get(history.size() - 1);
            if (normalizedBackgroundText.equals(lastSnapshot.getBackgroundText())
                    && normalizedMemoText.equals(lastSnapshot.getMemoText())) {
                return false;
            }
        }

        history.add(new MemoSnapshot(label, System.currentTimeMillis(), normalizedBackgroundText, normalizedMemoText));
        trimHistory();
        return true;
    }

    private void trimHistory() {
        List<MemoSnapshot> history = history();
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }
    }

    private static boolean isSameSnapshot(MemoSnapshot left, MemoSnapshot right) {
        return left.createdAtMillis == right.createdAtMillis
                && normalize(left.label).equals(normalize(right.label))
                && left.getBackgroundText().equals(right.getBackgroundText())
                && left.getMemoText().equals(right.getMemoText());
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
