# Code Context Memo for VS Code

VS Code extension for collecting selected code context and notes into a workspace-level memo.

## Features

- Adds a `Code Memo` activity bar view.
- Adds `Record code context` to the editor context menu when code is selected.
- Records project-relative file path, line range, best-effort code location, selected code, and `What to do`.
- Opens a temporary right-side recording panel with code preview and a multiline `What to do` input.
- Uses bordered, theme-aware input fields across record, memo, history, background, and AI settings panels.
- Highlights memo sections with different colors for file metadata, code blocks, task text, and separators, with higher-contrast task text.
- Persists task background, memo text, and history per workspace.
- Keeps at most 20 history snapshots and supports restoring or deleting a single snapshot.
- Uses VS Code native confirmation before deleting a history snapshot.
- Copies only the memo body.
- Supports DeepSeek/OpenAI-compatible AI calls for memo organization and task background optimization.
- Keeps AI settings inside the `Code Memo` webview instead of opening a separate settings page.

## Install

Install the packaged `.vsix` from:

```text
build/distributions/
```
