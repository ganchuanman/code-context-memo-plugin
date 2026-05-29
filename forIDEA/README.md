# Code Context Memo

JetBrains IDE plugin for collecting selected code snippets and notes into a project-level memo.

## Features

- Adds a `Code Memo` tool window with memo editing, copy, history, task background, and AI settings.
- Adds `Record code context` to the editor context menu when code is selected.
- Shows a dialog with the project-relative file path, line number, code location, selected code, and a `What to do` input.
- Appends the saved entry to the memo text area.
- Persists task background, memo text, and history per project.
- Keeps at most 20 history snapshots and supports deleting a single snapshot.
- Supports DeepSeek/OpenAI-compatible AI calls for memo organization and task background optimization.
- Stores API keys through JetBrains Password Safe.

## Data and privacy

- Memo content is stored in the project workspace file.
- AI settings are stored at IDE application level.
- API keys are stored through JetBrains Password Safe.
- The plugin does not include telemetry.
- If you use AI features, the current memo/task background is sent to the endpoint you configure.

## License

MIT License.

## Run

Open this `forIDEA` directory as a Gradle project in IntelliJ IDEA and use the `runIde` Gradle task.
