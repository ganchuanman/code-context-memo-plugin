# Privacy

Code Context Memo does not include telemetry.

## Local storage

- JetBrains IDE: memo data is stored in the project workspace file.
- VS Code: memo data is stored in VS Code workspace state.
- AI settings are stored at IDE application/global level.
- API keys are stored through JetBrains Password Safe or VS Code SecretStorage.

## AI requests

AI features are optional. If you use them, the current memo and task background are sent to the OpenAI-compatible endpoint you configure.

The default endpoint is:

```text
https://api.deepseek.com/chat/completions
```
