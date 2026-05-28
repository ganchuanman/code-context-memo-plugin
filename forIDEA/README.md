# Code Context Memo for JetBrains IDEs

JetBrains IDE plugin for collecting selected code snippets and notes into a project-level memo.

## Behavior

- Adds a `Code Memo` tool window with one editable memo text area.
- Adds `Record code context` to the editor context menu when code is selected.
- Shows a dialog with the project-relative file path, selected code, and a `What to do` input.
- Appends the saved entry to the memo text area.
- Persists memo content per project.

## Run

Open this `forIDEA` directory as a Gradle project in IntelliJ IDEA and use the `runIde` Gradle task.
