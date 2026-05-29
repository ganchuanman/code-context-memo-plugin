# Publishing Materials

This document collects the materials needed to publish Code Context Memo to Visual Studio Marketplace and JetBrains Marketplace.

## Assumptions

- Publisher/vendor: `AaronOhO`
- Support email: `aaron_oh@163.com`
- Repository: `https://github.com/AaronOhO/code-context-memo-plugin`
- Version: `0.7.5`
- Pricing: free
- License/EULA: MIT License

## Required accounts and permissions

### Visual Studio Marketplace

- Create or use the `AaronOhO` publisher.
- Create an Azure DevOps Personal Access Token with Marketplace `Manage` scope.
- Run `vsce login AaronOhO` before publishing from the CLI, or upload the `.vsix` manually from the publisher portal.

### JetBrains Marketplace

- Create or use the `AaronOhO` vendor profile.
- Accept the JetBrains Marketplace Developer Agreement.
- Select MIT License in the license/EULA section of the upload form.
- Upload the JetBrains plugin ZIP from the Marketplace upload page.

## Shared listing copy

### Short description

Collect selected code context and notes into an editable memo for coding agents.

### Long description

Code Context Memo helps developers collect the code context needed by coding agents without switching away from the editor.

It records the selected code, project-relative file path, line number, best-effort code location, and a `What to do` note into an editable project or workspace memo. The memo can be copied directly after the user finishes collecting context.

Key features:

- Record selected code from the editor context menu.
- Keep an editable Code Memo side panel.
- Capture file path, line number, code location, selected code, and task notes.
- Persist memo text, task background, and history per project or workspace.
- Keep up to 20 memo history snapshots.
- Copy only the memo body.
- Organize the memo with optional DeepSeek/OpenAI-compatible AI calls.
- Optimize task background text before organizing memo content.
- Store API keys through the IDE secret storage mechanism.

Privacy and data handling:

- The plugin does not include telemetry.
- Memo data is stored locally in the current IDE project or VS Code workspace.
- API keys are stored through JetBrains Password Safe or VS Code SecretStorage.
- AI calls are optional and only run when the user triggers an AI action.
- When AI is used, the current memo/task background is sent to the OpenAI-compatible endpoint configured by the user.

## VS Code Marketplace fields

- Extension name: `code-context-memo`
- Display name: `Code Context Memo`
- Extension ID after publish: `AaronOhO.code-context-memo`
- Publisher: `AaronOhO`
- Category: `Other`
- Keywords: `code`, `context`, `memo`, `notes`, `ai`, `agent`, `prompt`, `snippet`, `workspace`, `deepseek`, `openai`
- Gallery banner color: `#073B3A`
- Icon: `forVSCode/media/icon.png`
- README: `forVSCode/README.md`
- Changelog: `forVSCode/CHANGELOG.md`
- Support: `forVSCode/SUPPORT.md`
- Privacy: `forVSCode/PRIVACY.md`
- License: `MIT`
- Screenshot: `images/vscode.png`

Recommended CLI flow:

```bash
cd forVSCode
npm run check
npx --yes @vscode/vsce package --out build/distributions/code-context-memo-vscode-0.7.5.vsix
npx @vscode/vsce login AaronOhO
npx @vscode/vsce publish
```

Manual upload alternative:

```bash
cd forVSCode
npm run check
npx --yes @vscode/vsce package --out build/distributions/code-context-memo-vscode-0.7.5.vsix
```

Then upload `build/distributions/code-context-memo-vscode-0.7.5.vsix` or the official `vsce package` output from the Visual Studio Marketplace publisher portal.

`npm run prepare-vsix` is kept as a local install-from-disk fallback, but the official `vsce package` output is preferred for Marketplace submission.

## JetBrains Marketplace fields

- Plugin ID: `com.github.aaronoho.code-context-memo`
- Name: `Code Context Memo`
- Vendor: `AaronOhO`
- Vendor email: `aaron_oh@163.com`
- Vendor URL: `https://github.com/AaronOhO`
- Website/source: `https://github.com/AaronOhO/code-context-memo-plugin`
- Since build: `243`
- Plugin logo: `forIDEA/src/main/resources/META-INF/pluginIcon.svg`
- Dark logo: `forIDEA/src/main/resources/META-INF/pluginIcon_dark.svg`
- Description and change notes: `forIDEA/src/main/resources/META-INF/plugin.xml`
- Changelog: `forIDEA/CHANGELOG.md`
- Privacy: `forIDEA/PRIVACY.md`
- License/EULA: `MIT License`
- Screenshot: `images/idea.png`

Build and upload flow:

```bash
cd forIDEA
gradle buildPlugin
```

Then upload `build/distributions/ide-code-context-memo-plugin-0.7.5.zip` from JetBrains Marketplace. The repository-level release artifact is copied to `dist/code-context-memo-0.7.5.zip`.

## Pre-submit checklist

- Confirm MIT License is selected on both marketplace submission forms.
- Confirm repository is public.
- Confirm screenshots are current and optimized.
- Confirm VS Code publisher `AaronOhO` exists.
- Confirm JetBrains vendor `AaronOhO` exists.
- Build both packages from a clean worktree.
- Install both packages locally once before submitting.
- For JetBrains, run Plugin Verifier if you want to reduce Marketplace review risk.

## Official references

- VS Code publishing: https://code.visualstudio.com/api/working-with-extensions/publishing-extension
- JetBrains listing best practices: https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html
- JetBrains upload flow: https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html
- JetBrains plugin logo: https://plugins.jetbrains.com/docs/intellij/plugin-icon-file.html
