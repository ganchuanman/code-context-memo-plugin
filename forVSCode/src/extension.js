const https = require('https');
const path = require('path');
const vscode = require('vscode');

const WORKSPACE_STATE_KEY = 'codeMemo.state';
const AI_SETTINGS_KEY = 'codeMemo.aiSettings';
const AI_SECRET_KEY = 'codeMemo.aiApiKey';
const MAX_HISTORY_SIZE = 20;
const MEMO_RECORD_GAP = '\n\n';
const DEFAULT_ENDPOINT = 'https://api.deepseek.com/chat/completions';
const DEFAULT_MODEL = 'deepseek-v4-pro';
const PROMPT_LANGUAGE_ZH = 'zh';
const PROMPT_LANGUAGE_EN = 'en';
const DEFAULT_PROMPT_LANGUAGE = PROMPT_LANGUAGE_ZH;
const ORGANIZE_PROMPT_ZH = `你是面向 Code Agent 的备忘录整理器。用户输入包含“Task Background”和“Memo”。基于任务背景整理每条记录中的“要做什么”，让它成为可以直接给代码 agent 执行的中文提示词。必须使用中文备忘录字段名：每条记录都使用“文件:”、“行号:”、“关键代码:”、“要做什么:”字段；如果原记录包含“位置:”，必须保留在“行号:”之后、“关键代码:”之前。关键代码继续放在 Markdown fenced code block 中。字段之间不要插入空行；多条记录之间只保留一个空行，不要输出装饰性分割线。不要新增“Task Background”字段，要把必要背景融入每条记录的“要做什么”。保留文件路径、行号、位置和关键代码，不要编造不存在的信息。只输出整理后的备忘录正文，不要输出额外说明。`;
const ORGANIZE_PROMPT_EN = `You are a memo organizer for Code Agent. The user input contains "Task Background" and "Memo". Use the task background to refine each record's "What to do" section into an actionable English prompt for a code agent. Keep the English memo field names: every record must use the fields "File:", "Line:", "Key Code:", and "What to do:"; if the original record contains "Location:", keep it after "Line:" and before "Key Code:". Keep code in Markdown fenced code blocks. Do not insert blank lines between fields. Leave one blank line between records, and do not output decorative separator lines. Do not add a "Task Background" field; fold necessary background into each "What to do" section. Preserve file paths, line numbers, locations, and code. Do not invent missing information. Output only the organized memo body.`;
const OPTIMIZE_BACKGROUND_PROMPT_ZH = `你是代码任务背景整理助手。把用户输入的任务背景改写成更适合后续交给代码 agent 使用的中文描述。要求：保留事实，不编造信息；突出目标、现象、期望结果、约束和已知线索；语言简洁、结构清晰；只输出优化后的任务背景正文，不要输出额外说明。`;
const OPTIMIZE_BACKGROUND_PROMPT_EN = `You are a code task background editor. Rewrite the user input into a clearer English task background for later code-agent work. Preserve facts and do not invent information. Highlight goals, symptoms, expected results, constraints, and known clues. Keep it concise and structured. Output only the optimized task background body.`;

function activate(context) {
    const stateStore = new CodeMemoStateStore(context);
    const viewProvider = new CodeMemoViewProvider(context, stateStore);

    context.subscriptions.push(vscode.window.registerWebviewViewProvider(
        CodeMemoViewProvider.viewType,
        viewProvider,
        { webviewOptions: { retainContextWhenHidden: true } }
    ));
    context.subscriptions.push(vscode.commands.registerCommand('codeMemo.recordContext', () =>
        recordSelectedContext(context, stateStore, viewProvider)
    ));
    context.subscriptions.push(vscode.commands.registerCommand('codeMemo.openAiSettings', () =>
        viewProvider.openAiSettings()
    ));
}

function deactivate() {
}

function appendMemoEntry(currentText, entry) {
    if (!currentText || !currentText.trim()) {
        return entry;
    }
    if (currentText.endsWith(MEMO_RECORD_GAP)) {
        return currentText + entry;
    }
    return currentText + (currentText.endsWith('\n') ? '\n' : MEMO_RECORD_GAP) + entry;
}

class CodeMemoStateStore {
    constructor(context) {
        this.context = context;
    }

    getState() {
        return normalizeState(this.context.workspaceState.get(WORKSPACE_STATE_KEY));
    }

    async saveState(state) {
        const normalized = normalizeState(state);
        trimHistory(normalized.history);
        await this.context.workspaceState.update(WORKSPACE_STATE_KEY, normalized);
        return normalized;
    }

    async setMemoText(memoText) {
        const state = this.getState();
        state.memoText = memoText || '';
        return this.saveState(state);
    }

    async setBackgroundText(backgroundText) {
        const state = this.getState();
        state.backgroundText = backgroundText || '';
        return this.saveState(state);
    }

    async appendEntry(entry) {
        const state = this.getState();
        state.memoText = appendMemoEntry(state.memoText, entry);
        addSnapshot(state, 'Record code context', state.backgroundText, state.memoText);
        return this.saveState(state);
    }

    async captureSnapshot(label, backgroundText, memoText) {
        const state = this.getState();
        if (addSnapshot(state, label, backgroundText ?? state.backgroundText, memoText ?? state.memoText)) {
            return this.saveState(state);
        }
        return state;
    }

    async restoreSnapshot(snapshot) {
        const state = this.getState();
        state.backgroundText = snapshot && snapshot.backgroundText ? snapshot.backgroundText : '';
        state.memoText = snapshot && snapshot.memoText ? snapshot.memoText : '';
        return this.saveState(state);
    }

    async deleteSnapshot(snapshot) {
        if (!snapshot) {
            return false;
        }
        const state = this.getState();
        const index = state.history.findIndex((item) => isSameSnapshot(item, snapshot));
        if (index < 0) {
            return false;
        }
        state.history.splice(index, 1);
        await this.saveState(state);
        return true;
    }
}

class CodeMemoViewProvider {
    static viewType = 'codeMemo.memoView';

    constructor(context, stateStore) {
        this.context = context;
        this.stateStore = stateStore;
        this.view = undefined;
        this.organizeRunning = false;
        this.optimizeRunning = false;
    }

    resolveWebviewView(webviewView) {
        this.view = webviewView;
        webviewView.webview.options = { enableScripts: true };
        webviewView.webview.html = getMemoViewHtml(webviewView.webview, this.context.extensionUri, this.stateStore.getState());
        webviewView.webview.onDidReceiveMessage((message) => this.handleMessage(message));
    }

    refresh() {
        this.post({ type: 'state', state: this.stateStore.getState() });
    }

    post(message) {
        if (this.view) {
            this.view.webview.postMessage(message);
        }
    }

    async handleMessage(message) {
        switch (message.type) {
            case 'memoChanged':
                await this.stateStore.setMemoText(message.memoText || '');
                break;
            case 'memoBlur':
                await this.stateStore.captureSnapshot('Manual memo edit', undefined, message.memoText || '');
                this.refresh();
                break;
            case 'saveBackground':
                await this.saveBackground(message.backgroundText || '');
                break;
            case 'copyMemo':
                await this.copyMemo(message.memoText || '');
                break;
            case 'clearMemo':
                await this.clearMemo();
                break;
            case 'restoreSnapshot':
                await this.stateStore.restoreSnapshot(message.snapshot);
                this.refresh();
                break;
            case 'deleteSnapshot':
                await this.deleteSnapshotWithConfirmation(message.snapshot);
                break;
            case 'openAiSettings':
                await this.openAiSettings();
                break;
            case 'saveAiSettings':
                await saveAiConfig(this.context, message.settings || {});
                vscode.window.showInformationMessage('AI settings saved.');
                break;
            case 'organizeMemo':
                await this.organizeMemo(message.memoText || '');
                break;
            case 'writeAiMemo':
                await this.writeAiMemo(message.memoText || '');
                break;
            case 'optimizeBackground':
                await this.optimizeBackground(message.backgroundText || '');
                break;
            default:
                break;
        }
    }

    async deleteSnapshotWithConfirmation(snapshot) {
        if (!snapshot) {
            return;
        }
        const result = await vscode.window.showWarningMessage(
            'Delete this history snapshot?',
            { modal: true },
            'Delete'
        );
        if (result !== 'Delete') {
            return;
        }
        const deleted = await this.stateStore.deleteSnapshot(snapshot);
        this.refresh();
        if (!deleted) {
            vscode.window.showInformationMessage('This history snapshot no longer exists.');
        }
    }

    async openAiSettings() {
        if (!this.view) {
            await vscode.commands.executeCommand('codeMemo.memoView.focus').then(undefined, () => undefined);
        }
        for (let attempt = 0; !this.view && attempt < 5; attempt++) {
            await new Promise((resolve) => setTimeout(resolve, 50));
        }
        if (!this.view) {
            vscode.window.showWarningMessage('The Code Memo view is not open yet. Open the sidebar before configuring AI.');
            return;
        }
        this.post({
            type: 'aiSettings',
            settings: await getAiSettingsForWebview(this.context)
        });
    }

    async saveBackground(backgroundText) {
        const current = this.stateStore.getState();
        if (backgroundText === current.backgroundText) {
            return;
        }
        await this.stateStore.captureSnapshot('Before saving task background');
        await this.stateStore.setBackgroundText(backgroundText);
        await this.stateStore.captureSnapshot('Save task background');
        this.refresh();
    }

    async copyMemo(memoText) {
        await this.stateStore.setMemoText(memoText);
        if (!memoText.trim()) {
            vscode.window.showInformationMessage('Nothing to copy.');
            return;
        }
        await vscode.env.clipboard.writeText(memoText);
        vscode.window.setStatusBarMessage('Code Memo copied', 1800);
    }

    async clearMemo() {
        const result = await vscode.window.showWarningMessage('Clear all Code Memo content?', { modal: true }, 'Clear');
        if (result !== 'Clear') {
            return;
        }
        await this.stateStore.captureSnapshot('Before clearing');
        await this.stateStore.setMemoText('');
        this.refresh();
    }

    async organizeMemo(memoText) {
        if (this.organizeRunning) {
            return;
        }
        await this.stateStore.setMemoText(memoText);
        const state = this.stateStore.getState();
        if (!state.memoText.trim()) {
            vscode.window.showInformationMessage('Memo is empty. Record some code context first.');
            return;
        }
        const config = await ensureAiConfigured(this.context, this);
        if (!config) {
            return;
        }

        this.organizeRunning = true;
        this.post({ type: 'organizeStatus', running: true });
        try {
            const result = await runChatCompletion(config, config.organizePrompt, buildOrganizeUserPrompt(state.backgroundText, state.memoText));
            this.post({ type: 'organizeResult', text: result });
        } catch (error) {
            vscode.window.showErrorMessage(`Organize memo failed: ${error.message}`);
        } finally {
            this.organizeRunning = false;
            this.post({ type: 'organizeStatus', running: false });
        }
    }

    async writeAiMemo(memoText) {
        await this.stateStore.captureSnapshot('Before AI organize');
        await this.stateStore.setMemoText(memoText);
        await this.stateStore.captureSnapshot('AI organize writeback');
        this.refresh();
    }

    async optimizeBackground(backgroundText) {
        if (this.optimizeRunning) {
            return;
        }
        if (!backgroundText.trim()) {
            vscode.window.showInformationMessage('Enter the task background first.');
            return;
        }
        const config = await ensureAiConfigured(this.context, this);
        if (!config) {
            return;
        }

        this.optimizeRunning = true;
        this.post({ type: 'optimizeStatus', running: true });
        try {
            const result = await runChatCompletion(config, config.optimizeBackgroundPrompt, backgroundText.trim());
            this.post({ type: 'optimizedBackgroundResult', text: result });
        } catch (error) {
            vscode.window.showErrorMessage(`Optimize task background failed: ${error.message}`);
        } finally {
            this.optimizeRunning = false;
            this.post({ type: 'optimizeStatus', running: false });
        }
    }
}

async function recordSelectedContext(context, stateStore, viewProvider) {
    const editor = vscode.window.activeTextEditor;
    if (!editor || editor.selection.isEmpty) {
        vscode.window.showInformationMessage('Select a code range first.');
        return;
    }

    const document = editor.document;
    const selectedCode = document.getText(editor.selection);
    if (!selectedCode.trim()) {
        return;
    }

    const relativePath = vscode.workspace.asRelativePath(document.uri, false);
    const lineRange = getLineRange(document, editor.selection);
    const codeLocation = await getCodeLocation(document.uri, editor.selection.active);
    const language = getLanguage(document);
    const promptLanguage = (await getAiConfig(context)).promptLanguage;
    const sourceViewColumn = editor.viewColumn || vscode.ViewColumn.One;

    const panel = vscode.window.createWebviewPanel(
        'codeMemoRecordContext',
        'Record code context',
        vscode.ViewColumn.Beside,
        { enableScripts: true }
    );
    panel.webview.html = getRecordContextHtml(panel.webview, {
        relativePath,
        lineRange,
        codeLocation,
        selectedCode,
        promptLanguage
    });
    panel.webview.onDidReceiveMessage(async (message) => {
        if (message.type === 'cancel') {
            panel.dispose();
            await vscode.window.showTextDocument(document, sourceViewColumn, false);
            return;
        }
        if (message.type !== 'save') {
            return;
        }
        const entry = formatEntry(relativePath, lineRange, codeLocation, selectedCode, message.note || '', language, promptLanguage);
        await stateStore.appendEntry(entry);
        viewProvider.refresh();
        panel.dispose();
        await vscode.window.showTextDocument(document, sourceViewColumn, false);
    });
}

function formatEntry(relativePath, lineRange, codeLocation, selectedCode, note, language, promptLanguage) {
    const fence = selectedCode.includes('```') ? '````' : '```';
    const codeBlock = selectedCode.endsWith('\n') ? selectedCode : selectedCode + '\n';
    const labels = getMemoLabels(promptLanguage);
    let text = `${labels.file}: ${relativePath}\n${labels.line}: ${lineRange}\n`;
    if (codeLocation) {
        text += `${labels.location}: ${codeLocation}\n`;
    }
    return `${text}${labels.keyCode}:\n${fence}${language || ''}\n${codeBlock}${fence}\n${labels.task}:\n${note}\n`;
}

function getMemoLabels(promptLanguage) {
    return normalizePromptLanguage(promptLanguage) === PROMPT_LANGUAGE_EN
        ? { file: 'File', line: 'Line', location: 'Location', keyCode: 'Key Code', task: 'What to do' }
        : { file: '文件', line: '行号', location: '位置', keyCode: '关键代码', task: '要做什么' };
}

function getLineRange(document, selection) {
    const startOffset = document.offsetAt(selection.start);
    const effectiveEndOffset = Math.max(startOffset, document.offsetAt(selection.end) - 1);
    const startLine = document.positionAt(startOffset).line + 1;
    const endLine = document.positionAt(effectiveEndOffset).line + 1;
    return startLine === endLine ? String(startLine) : `${startLine}-${endLine}`;
}

async function getCodeLocation(uri, position) {
    try {
        const symbols = await vscode.commands.executeCommand('vscode.executeDocumentSymbolProvider', uri);
        if (!Array.isArray(symbols) || symbols.length === 0) {
            return '';
        }
        const chain = findSymbolChain(symbols, position, []);
        const functionSymbol = lastSymbolOfKind(chain, [
            vscode.SymbolKind.Method,
            vscode.SymbolKind.Function,
            vscode.SymbolKind.Constructor
        ]);
        const classSymbol = lastSymbolOfKind(chain, [
            vscode.SymbolKind.Class,
            vscode.SymbolKind.Interface,
            vscode.SymbolKind.Enum,
            vscode.SymbolKind.Struct,
            vscode.SymbolKind.Object
        ]);
        if (classSymbol && functionSymbol) {
            return `${classSymbol.name}#${functionSymbol.name}`;
        }
        return functionSymbol ? functionSymbol.name : classSymbol ? classSymbol.name : '';
    } catch {
        return '';
    }
}

function findSymbolChain(symbols, position, parents) {
    for (const symbol of symbols) {
        const range = getSymbolRange(symbol);
        if (!range || !range.contains(position)) {
            continue;
        }
        const nextParents = parents.concat(symbol);
        if (Array.isArray(symbol.children) && symbol.children.length > 0) {
            return findSymbolChain(symbol.children, position, nextParents);
        }
        return nextParents;
    }
    return parents;
}

function getSymbolRange(symbol) {
    if (symbol.range) {
        return symbol.range;
    }
    if (symbol.location && symbol.location.range) {
        return symbol.location.range;
    }
    return undefined;
}

function lastSymbolOfKind(symbols, kinds) {
    for (let index = symbols.length - 1; index >= 0; index--) {
        if (kinds.includes(symbols[index].kind)) {
            return symbols[index];
        }
    }
    return undefined;
}

function getLanguage(document) {
    if (document.languageId && document.languageId !== 'plaintext') {
        return document.languageId;
    }
    const extension = path.extname(document.fileName);
    return extension ? extension.slice(1) : '';
}

async function ensureAiConfigured(context, viewProvider) {
    const config = await getAiConfig(context);
    if (config.endpoint && config.model && config.apiKey) {
        return config;
    }
    const action = await vscode.window.showWarningMessage('Endpoint, model, and API Key are required.', 'Open AI Settings');
    if (action === 'Open AI Settings') {
        await viewProvider.openAiSettings();
    }
    return undefined;
}

async function getAiConfig(context) {
    const saved = context.globalState.get(AI_SETTINGS_KEY) || {};
    const promptLanguage = normalizePromptLanguage(saved.promptLanguage);
    const organizePrompts = normalizePromptMap(saved.organizePrompts, saved.organizePrompt, ORGANIZE_PROMPT_ZH, ORGANIZE_PROMPT_EN);
    const optimizeBackgroundPrompts = normalizePromptMap(
        saved.optimizeBackgroundPrompts,
        saved.optimizeBackgroundPrompt,
        OPTIMIZE_BACKGROUND_PROMPT_ZH,
        OPTIMIZE_BACKGROUND_PROMPT_EN
    );
    return {
        endpoint: saved.endpoint || DEFAULT_ENDPOINT,
        model: saved.model || DEFAULT_MODEL,
        apiKey: await context.secrets.get(AI_SECRET_KEY) || '',
        promptLanguage,
        organizePrompt: organizePrompts[promptLanguage],
        optimizeBackgroundPrompt: optimizeBackgroundPrompts[promptLanguage],
        organizePrompts,
        optimizeBackgroundPrompts
    };
}

async function getAiSettingsForWebview(context) {
    const config = await getAiConfig(context);
    return {
        endpoint: config.endpoint,
        model: config.model,
        apiKeyMask: maskApiKey(config.apiKey),
        promptLanguage: config.promptLanguage,
        organizePrompts: config.organizePrompts,
        optimizeBackgroundPrompts: config.optimizeBackgroundPrompts
    };
}

async function saveAiConfig(context, settings) {
    const promptLanguage = normalizePromptLanguage(settings.promptLanguage);
    const organizePrompts = normalizePromptMap(settings.organizePrompts, undefined, ORGANIZE_PROMPT_ZH, ORGANIZE_PROMPT_EN);
    const optimizeBackgroundPrompts = normalizePromptMap(
        settings.optimizeBackgroundPrompts,
        undefined,
        OPTIMIZE_BACKGROUND_PROMPT_ZH,
        OPTIMIZE_BACKGROUND_PROMPT_EN
    );
    await context.globalState.update(AI_SETTINGS_KEY, {
        endpoint: settings.endpoint || DEFAULT_ENDPOINT,
        model: settings.model || DEFAULT_MODEL,
        promptLanguage,
        organizePrompts,
        optimizeBackgroundPrompts
    });
    if (settings.apiKeyChanged) {
        await context.secrets.store(AI_SECRET_KEY, settings.apiKey || '');
    }
}

function normalizePromptLanguage(value) {
    return value === PROMPT_LANGUAGE_EN ? PROMPT_LANGUAGE_EN : PROMPT_LANGUAGE_ZH;
}

function normalizePromptMap(value, legacyPrompt, zhDefault, enDefault) {
    return {
        [PROMPT_LANGUAGE_ZH]: normalizePromptValue(
            value && value[PROMPT_LANGUAGE_ZH],
            normalizePromptValue(legacyPrompt, zhDefault)
        ),
        [PROMPT_LANGUAGE_EN]: normalizePromptValue(value && value[PROMPT_LANGUAGE_EN], enDefault)
    };
}

function normalizePromptValue(value, fallback) {
    if (typeof value !== 'string' || !value.trim()) {
        return fallback;
    }
    return value;
}

function runChatCompletion(config, systemPrompt, userPrompt) {
    return new Promise((resolve, reject) => {
        let url;
        try {
            url = new URL(config.endpoint);
        } catch {
            reject(new Error('Endpoint URL is invalid.'));
            return;
        }

        const body = JSON.stringify({
            model: config.model,
            messages: [
                { role: 'system', content: systemPrompt },
                { role: 'user', content: userPrompt }
            ],
            stream: false
        });

        const request = https.request(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${config.apiKey}`,
                'Content-Length': Buffer.byteLength(body)
            }
        }, (response) => {
            let data = '';
            response.setEncoding('utf8');
            response.on('data', (chunk) => {
                data += chunk;
            });
            response.on('end', () => {
                if (!response.statusCode || response.statusCode < 200 || response.statusCode >= 300) {
                    reject(new Error(`HTTP ${response.statusCode}: ${data.slice(0, 500)}`));
                    return;
                }
                try {
                    const json = JSON.parse(data);
                    const text = json.choices && json.choices[0]
                        && json.choices[0].message
                        && json.choices[0].message.content;
                    if (!text || !text.trim()) {
                        reject(new Error('AI returned empty content.'));
                        return;
                    }
                    resolve(text.trim());
                } catch (error) {
                    reject(new Error(`Failed to parse AI response: ${error.message}`));
                }
            });
        });
        request.on('error', reject);
        request.write(body);
        request.end();
    });
}

function buildOrganizeUserPrompt(backgroundText, memoText) {
    const background = backgroundText && backgroundText.trim() ? backgroundText.trim() : 'None';
    return `Task Background:\n${background}\n\nMemo:\n${memoText}`;
}

function normalizeState(raw) {
    return {
        backgroundText: raw && typeof raw.backgroundText === 'string' ? raw.backgroundText : '',
        memoText: raw && typeof raw.memoText === 'string' ? raw.memoText : '',
        history: Array.isArray(raw && raw.history)
            ? raw.history.map(normalizeSnapshot).filter((snapshot) => snapshot.backgroundText.trim() || snapshot.memoText.trim()).slice(-MAX_HISTORY_SIZE)
            : []
    };
}

function normalizeSnapshot(snapshot) {
    return {
        label: snapshot && typeof snapshot.label === 'string' ? snapshot.label : '',
        createdAtMillis: snapshot && Number.isFinite(snapshot.createdAtMillis) ? snapshot.createdAtMillis : Date.now(),
        backgroundText: snapshot && typeof snapshot.backgroundText === 'string' ? snapshot.backgroundText : '',
        memoText: snapshot && typeof snapshot.memoText === 'string' ? snapshot.memoText : ''
    };
}

function addSnapshot(state, label, backgroundText, memoText) {
    const normalizedBackground = backgroundText || '';
    const normalizedMemo = memoText || '';
    if (!normalizedBackground.trim() && !normalizedMemo.trim()) {
        return false;
    }
    const last = state.history[state.history.length - 1];
    if (last && last.backgroundText === normalizedBackground && last.memoText === normalizedMemo) {
        return false;
    }
    state.history.push({
        label: label || 'Snapshot',
        createdAtMillis: Date.now(),
        backgroundText: normalizedBackground,
        memoText: normalizedMemo
    });
    trimHistory(state.history);
    return true;
}

function trimHistory(history) {
    while (history.length > MAX_HISTORY_SIZE) {
        history.shift();
    }
}

function isSameSnapshot(left, right) {
    return left.createdAtMillis === right.createdAtMillis
        && (left.label || '') === (right.label || '')
        && (left.backgroundText || '') === (right.backgroundText || '')
        && (left.memoText || '') === (right.memoText || '');
}

function getMemoViewHtml(webview, extensionUri, state) {
    const nonce = getNonce();
    const iconUri = webview.asWebviewUri(vscode.Uri.joinPath(extensionUri, 'media', 'memo.svg'));
    return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src ${webview.cspSource}; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Code Memo</title>
    <style>
        body { padding: 10px; color: var(--vscode-foreground); background: var(--vscode-sideBar-background); font-family: var(--vscode-font-family); }
        label { display: block; margin: 10px 0 5px; font-weight: 600; }
        button { border: 1px solid var(--vscode-button-border, transparent); background: var(--vscode-button-secondaryBackground); color: var(--vscode-button-secondaryForeground); padding: 4px 8px; border-radius: 3px; cursor: pointer; }
        button.primary { background: var(--vscode-button-background); color: var(--vscode-button-foreground); }
        button:disabled { opacity: 0.55; cursor: default; }
        textarea, input, select { box-sizing: border-box; width: 100%; color: var(--vscode-input-foreground); background: var(--vscode-input-background, var(--vscode-editor-background)); border: 1px solid var(--vscode-panel-border, #c8c8c8); border-radius: 4px; font-family: var(--vscode-font-family); padding: 7px 8px; box-shadow: inset 0 0 0 1px rgba(127,127,127,.08); }
        textarea:focus, input:focus, select:focus { outline: 1px solid var(--vscode-focusBorder); outline-offset: -1px; border-color: var(--vscode-focusBorder); }
        textarea[readonly], input[readonly] { background: var(--vscode-editorWidget-background, var(--vscode-input-background)); color: var(--vscode-descriptionForeground); }
        textarea { resize: vertical; }
        .toolbar { display: flex; gap: 6px; flex-wrap: wrap; justify-content: flex-end; margin-bottom: 10px; }
        .background { border: 1px solid var(--vscode-panel-border); background: var(--vscode-editorWidget-background); padding: 8px; border-radius: 6px; margin-bottom: 8px; }
        .section-title { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-weight: 600; margin-bottom: 6px; }
        .section-actions { display: flex; gap: 4px; flex-wrap: wrap; justify-content: flex-end; }
        .background-text { max-height: 96px; overflow: auto; white-space: pre-wrap; color: var(--vscode-descriptionForeground); font-size: 12px; line-height: 1.45; }
        .background-text.expanded { max-height: 220px; }
        .memo { min-height: 420px; height: calc(100vh - 220px); font-family: var(--vscode-editor-font-family); font-size: var(--vscode-editor-font-size); line-height: 1.45; }
        .memo-editor { box-sizing: border-box; width: 100%; overflow: auto; white-space: pre-wrap; word-break: break-word; color: var(--vscode-input-foreground); background: var(--vscode-input-background, var(--vscode-editor-background)); border: 1px solid var(--vscode-panel-border, #c8c8c8); border-radius: 4px; padding: 8px; box-shadow: inset 0 0 0 1px rgba(127,127,127,.08); }
        .memo-editor:focus { outline: 1px solid var(--vscode-focusBorder); outline-offset: -1px; border-color: var(--vscode-focusBorder); }
        .memo-body { color: var(--vscode-input-foreground); }
        .memo-file { color: var(--vscode-textLink-foreground); font-size: 12px; }
        .memo-meta { color: var(--vscode-descriptionForeground); font-size: 12px; }
        .memo-header { color: var(--vscode-foreground); font-weight: 600; font-size: 13px; }
        .memo-code { color: var(--vscode-gitDecoration-addedResourceForeground, #16825d); font-size: 12px; }
        .memo-task { color: #9f1d13; font-family: var(--vscode-font-family); font-weight: 600; }
        body.vscode-dark .memo-task { color: #ff7a5c; }
        body.vscode-high-contrast .memo-task { color: var(--vscode-errorForeground, #ff6b6b); font-weight: 700; }
        .hint { color: var(--vscode-descriptionForeground); font-size: 12px; margin: 6px 2px; }
        .modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,.38); display: none; align-items: center; justify-content: center; z-index: 10; }
        .modal-backdrop.open { display: flex; }
        .modal { width: min(880px, calc(100vw - 24px)); max-height: calc(100vh - 32px); overflow: auto; background: var(--vscode-editorWidget-background); border: 1px solid var(--vscode-panel-border); border-radius: 6px; padding: 14px; box-shadow: 0 8px 22px rgba(0,0,0,.35); }
        .modal h3 { margin: 0 0 10px; font-size: 14px; }
        .modal-actions { display: flex; gap: 8px; justify-content: flex-end; flex-wrap: wrap; margin-top: 10px; }
        .history-layout { display: grid; grid-template-columns: minmax(160px, 220px) 1fr; gap: 10px; min-height: 420px; }
        .history-list { border: 1px solid var(--vscode-panel-border); border-radius: 4px; overflow: auto; background: var(--vscode-input-background, var(--vscode-editor-background)); }
        .history-item { padding: 7px; border-bottom: 1px solid var(--vscode-panel-border); cursor: pointer; font-size: 12px; }
        .history-item.selected { background: var(--vscode-list-activeSelectionBackground); color: var(--vscode-list-activeSelectionForeground); }
        .preview-label { font-weight: 600; margin: 0 0 4px; }
        .preview { height: 130px; margin-bottom: 8px; font-family: var(--vscode-editor-font-family); }
        .preview.memo-preview { height: 260px; }
        .brand { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; font-weight: 600; }
        .brand img { width: 18px; height: 18px; }
    </style>
</head>
<body>
    <div class="brand"><img src="${iconUri}" alt="">Code Memo</div>
    <div class="toolbar">
        <button id="organizeButton" class="primary">Organize Memo</button>
        <button id="copyButton">Copy</button>
        <button id="historyButton">History</button>
        <button id="settingsButton">AI Settings</button>
        <button id="clearButton">Clear</button>
    </div>

    <section class="background">
        <div class="section-title">
            <span>Task Background</span>
            <span class="section-actions">
                <button id="editBackgroundButton">Edit</button>
                <button id="toggleBackgroundButton">Expand</button>
            </span>
        </div>
        <div id="backgroundText" class="background-text"></div>
    </section>

    <div id="memoHint" class="hint">Select code, right-click "Record code context", add "What to do", then copy or organize the memo.</div>
    <div id="memoText" class="memo memo-editor" contenteditable="true" spellcheck="false" role="textbox" aria-multiline="true"></div>

    <div id="backgroundModal" class="modal-backdrop">
        <div class="modal">
            <h3>Task Background</h3>
            <textarea id="backgroundEditText" rows="14"></textarea>
            <div class="modal-actions">
                <button id="optimizeBackgroundButton">Optimize Task Background</button>
                <button data-close="backgroundModal">Cancel</button>
                <button id="saveBackgroundButton" class="primary">Save</button>
            </div>
        </div>
    </div>

    <div id="backgroundResultModal" class="modal-backdrop">
        <div class="modal">
            <h3>Optimized Task Background</h3>
            <textarea id="backgroundResultText" rows="14"></textarea>
            <div class="modal-actions">
                <button data-close="backgroundResultModal">Cancel</button>
                <button id="useBackgroundResultButton" class="primary">Use (editable)</button>
            </div>
        </div>
    </div>

    <div id="aiResultModal" class="modal-backdrop">
        <div class="modal">
            <h3>Organized Memo</h3>
            <textarea id="aiResultText" rows="18" spellcheck="false"></textarea>
            <div class="modal-actions">
                <button data-close="aiResultModal">Cancel</button>
                <button id="writeAiMemoButton" class="primary">Write Back Memo</button>
            </div>
        </div>
    </div>

    <div id="historyModal" class="modal-backdrop">
        <div class="modal">
            <h3>History Snapshots</h3>
            <div class="history-layout">
                <div id="historyList" class="history-list"></div>
                <div>
                    <p class="preview-label">Task Background</p>
                    <textarea id="historyBackgroundPreview" class="preview" readonly></textarea>
                    <p class="preview-label">Memo</p>
                    <textarea id="historyMemoPreview" class="preview memo-preview" readonly></textarea>
                </div>
            </div>
            <div class="modal-actions">
                <button id="deleteHistoryButton">Delete Record</button>
                <button data-close="historyModal">Cancel</button>
                <button id="restoreHistoryButton" class="primary">Restore</button>
            </div>
        </div>
    </div>

    <div id="aiSettingsModal" class="modal-backdrop">
        <div class="modal">
            <h3>AI Settings</h3>
            <label>Endpoint</label>
            <input id="aiEndpoint">
            <label>Model</label>
            <input id="aiModel">
            <label>API Key</label>
            <input id="aiApiKey" type="password">
            <div class="hint">Leave blank to keep the current API Key.</div>
            <label>Prompt Language</label>
            <select id="promptLanguage">
                <option value="zh">Chinese</option>
                <option value="en">English</option>
            </select>
            <label>Organize Memo Prompt</label>
            <textarea id="organizePrompt" rows="8"></textarea>
            <label>Optimize Task Background Prompt</label>
            <textarea id="optimizeBackgroundPrompt" rows="6"></textarea>
            <div class="modal-actions">
                <button data-close="aiSettingsModal">Cancel</button>
                <button id="saveAiSettingsButton" class="primary">Save</button>
            </div>
        </div>
    </div>

    <script nonce="${nonce}">
        const vscode = acquireVsCodeApi();
        let state = ${safeJson(state)};
        let backgroundExpanded = false;
        let selectedHistoryIndex = -1;
        let organizeRunning = false;
        let optimizeRunning = false;
        let memoTimer;

        const memoText = document.getElementById('memoText');
        const backgroundText = document.getElementById('backgroundText');
        const memoHint = document.getElementById('memoHint');
        const organizeButton = document.getElementById('organizeButton');
        const copyButton = document.getElementById('copyButton');
        const historyButton = document.getElementById('historyButton');
        const clearButton = document.getElementById('clearButton');
        const backgroundEditText = document.getElementById('backgroundEditText');
        const backgroundResultText = document.getElementById('backgroundResultText');
        const aiResultText = document.getElementById('aiResultText');
        const aiEndpoint = document.getElementById('aiEndpoint');
        const aiModel = document.getElementById('aiModel');
        const aiApiKey = document.getElementById('aiApiKey');
        const promptLanguage = document.getElementById('promptLanguage');
        const organizePrompt = document.getElementById('organizePrompt');
        const optimizeBackgroundPrompt = document.getElementById('optimizeBackgroundPrompt');
        let currentPromptLanguage = 'zh';
        let promptDrafts = {
            zh: { organize: '', optimizeBackground: '' },
            en: { organize: '', optimizeBackground: '' }
        };

        render();

        window.addEventListener('message', (event) => {
            const message = event.data;
            if (message.type === 'state') {
                state = message.state;
                render();
            } else if (message.type === 'organizeStatus') {
                organizeRunning = message.running;
                updateButtons();
            } else if (message.type === 'organizeResult') {
                aiResultText.value = message.text || '';
                showModal('aiResultModal');
            } else if (message.type === 'optimizeStatus') {
                optimizeRunning = message.running;
                updateButtons();
            } else if (message.type === 'optimizedBackgroundResult') {
                backgroundResultText.value = message.text || '';
                showModal('backgroundResultModal');
            } else if (message.type === 'aiSettings') {
                openAiSettingsModal(message.settings || {});
            }
        });

        memoText.addEventListener('input', () => {
            state.memoText = getMemoPlainText();
            memoHint.style.display = state.memoText.trim() ? 'none' : 'block';
            clearTimeout(memoTimer);
            memoTimer = setTimeout(() => post('memoChanged', { memoText: state.memoText }), 250);
        });
        memoText.addEventListener('blur', () => {
            clearTimeout(memoTimer);
            state.memoText = getMemoPlainText();
            post('memoChanged', { memoText: state.memoText });
            post('memoBlur', { memoText: state.memoText });
            renderMemoEditor(state.memoText);
        });
        memoText.addEventListener('copy', (event) => {
            const selectedText = window.getSelection().toString();
            if (!selectedText) {
                return;
            }
            event.clipboardData.setData('text/plain', selectedText);
            event.preventDefault();
        });

        organizeButton.addEventListener('click', () => post('organizeMemo', { memoText: getMemoPlainText() }));
        copyButton.addEventListener('click', () => post('copyMemo', { memoText: getMemoPlainText() }));
        historyButton.addEventListener('click', () => {
            renderHistory();
            showModal('historyModal');
        });
        document.getElementById('settingsButton').addEventListener('click', () => post('openAiSettings'));
        document.getElementById('saveAiSettingsButton').addEventListener('click', () => {
            const apiKey = aiApiKey.value;
            saveActivePromptDraft();
            post('saveAiSettings', {
                settings: {
                    endpoint: aiEndpoint.value,
                    model: aiModel.value,
                    apiKey,
                    apiKeyChanged: apiKey.length > 0,
                    promptLanguage: promptLanguage.value,
                    organizePrompts: {
                        zh: promptDrafts.zh.organize,
                        en: promptDrafts.en.organize
                    },
                    optimizeBackgroundPrompts: {
                        zh: promptDrafts.zh.optimizeBackground,
                        en: promptDrafts.en.optimizeBackground
                    }
                }
            });
            aiApiKey.value = '';
            hideModal('aiSettingsModal');
        });
        promptLanguage.addEventListener('change', () => {
            saveActivePromptDraft();
            currentPromptLanguage = promptLanguage.value;
            renderPromptDraft();
        });
        clearButton.addEventListener('click', () => post('clearMemo'));
        document.getElementById('editBackgroundButton').addEventListener('click', () => {
            backgroundEditText.value = state.backgroundText || '';
            showModal('backgroundModal');
        });
        document.getElementById('toggleBackgroundButton').addEventListener('click', () => {
            backgroundExpanded = !backgroundExpanded;
            render();
        });
        document.getElementById('saveBackgroundButton').addEventListener('click', () => {
            post('saveBackground', { backgroundText: backgroundEditText.value });
            hideModal('backgroundModal');
        });
        document.getElementById('optimizeBackgroundButton').addEventListener('click', () => {
            post('optimizeBackground', { backgroundText: backgroundEditText.value });
        });
        document.getElementById('useBackgroundResultButton').addEventListener('click', () => {
            backgroundEditText.value = backgroundResultText.value;
            hideModal('backgroundResultModal');
        });
        document.getElementById('writeAiMemoButton').addEventListener('click', () => {
            post('writeAiMemo', { memoText: aiResultText.value });
            hideModal('aiResultModal');
        });
        document.getElementById('deleteHistoryButton').addEventListener('click', () => {
            if (selectedHistoryIndex < 0) return;
            const snapshot = state.history[selectedHistoryIndex];
            if (snapshot) {
                post('deleteSnapshot', { snapshot });
            }
        });
        document.getElementById('restoreHistoryButton').addEventListener('click', () => {
            if (selectedHistoryIndex < 0) return;
            const snapshot = state.history[selectedHistoryIndex];
            if (snapshot) {
                post('restoreSnapshot', { snapshot });
                hideModal('historyModal');
            }
        });
        document.querySelectorAll('[data-close]').forEach((button) => {
            button.addEventListener('click', () => hideModal(button.getAttribute('data-close')));
        });

        function render() {
            backgroundText.textContent = state.backgroundText && state.backgroundText.trim()
                ? state.backgroundText
                : 'Add task goals, symptoms, expected results, and constraints so AI can organize more accurately.';
            backgroundText.classList.toggle('expanded', backgroundExpanded);
            document.getElementById('toggleBackgroundButton').textContent = backgroundExpanded ? 'Collapse' : 'Expand';
            if (document.activeElement !== memoText) {
                renderMemoEditor(state.memoText || '');
            }
            memoHint.style.display = (state.memoText || '').trim() ? 'none' : 'block';
            updateButtons();
            renderHistory();
        }

        function updateButtons() {
            organizeButton.textContent = organizeRunning ? 'Organizing...' : 'Organize Memo';
            organizeButton.disabled = organizeRunning;
            clearButton.disabled = !(getMemoPlainText() || state.memoText || '').trim();
            historyButton.disabled = !state.history || state.history.length === 0;
            document.getElementById('optimizeBackgroundButton').textContent = optimizeRunning ? 'Optimizing...' : 'Optimize Task Background';
            document.getElementById('optimizeBackgroundButton').disabled = optimizeRunning;
        }

        function getMemoPlainText() {
            return memoText.innerText.replace(/\\u00a0/g, ' ');
        }

        function renderMemoEditor(text) {
            memoText.innerHTML = formatMemoHtml(text || '');
        }

        function formatMemoHtml(text) {
            if (!text) {
                return '';
            }
            let inCode = false;
            let inTask = false;
            return text.split('\\n').map((line) => {
                let className = 'memo-body';
                if (line.startsWith('File:') || line.startsWith('文件:')) {
                    className = 'memo-file';
                    inTask = false;
                } else if (line.startsWith('Line:') || line.startsWith('Location:') || line.startsWith('行号:') || line.startsWith('位置:')) {
                    className = 'memo-meta';
                    inTask = false;
                } else if (line === 'Key Code:' || line === '关键代码:') {
                    className = 'memo-header';
                    inTask = false;
                } else if (line.slice(0, 3) === String.fromCharCode(96, 96, 96)) {
                    className = 'memo-code';
                    inCode = !inCode;
                } else if (line === 'What to do:' || line === '要做什么:') {
                    className = 'memo-header';
                    inTask = true;
                } else if (inCode) {
                    className = 'memo-code';
                } else if (inTask) {
                    className = 'memo-task';
                }
                return '<span class="' + className + '">' + escapeHtml(line) + '</span>';
            }).join('<br>');
        }

        function escapeHtml(value) {
            return String(value || '')
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');
        }

        function renderHistory() {
            const list = document.getElementById('historyList');
            list.innerHTML = '';
            const history = state.history || [];
            if (history.length === 0) {
                selectedHistoryIndex = -1;
                updateHistoryPreview();
                return;
            }
            if (selectedHistoryIndex < 0 || selectedHistoryIndex >= history.length) {
                selectedHistoryIndex = history.length - 1;
            }
            for (let index = history.length - 1; index >= 0; index--) {
                const item = document.createElement('div');
                item.className = 'history-item' + (index === selectedHistoryIndex ? ' selected' : '');
                item.textContent = formatSnapshotLabel(history[index]);
                item.addEventListener('click', () => {
                    selectedHistoryIndex = index;
                    renderHistory();
                });
                list.appendChild(item);
            }
            updateHistoryPreview();
        }

        function updateHistoryPreview() {
            const snapshot = selectedHistoryIndex >= 0 ? state.history[selectedHistoryIndex] : undefined;
            document.getElementById('historyBackgroundPreview').value = snapshot ? snapshot.backgroundText || '' : '';
            document.getElementById('historyMemoPreview').value = snapshot ? snapshot.memoText || '' : '';
            document.getElementById('deleteHistoryButton').disabled = !snapshot;
            document.getElementById('restoreHistoryButton').disabled = !snapshot;
        }

        function openAiSettingsModal(settings) {
            aiEndpoint.value = settings.endpoint || '';
            aiModel.value = settings.model || '';
            aiApiKey.value = '';
            aiApiKey.placeholder = settings.apiKeyMask || 'Not set';
            promptDrafts = {
                zh: {
                    organize: settings.organizePrompts && settings.organizePrompts.zh ? settings.organizePrompts.zh : '',
                    optimizeBackground: settings.optimizeBackgroundPrompts && settings.optimizeBackgroundPrompts.zh ? settings.optimizeBackgroundPrompts.zh : ''
                },
                en: {
                    organize: settings.organizePrompts && settings.organizePrompts.en ? settings.organizePrompts.en : '',
                    optimizeBackground: settings.optimizeBackgroundPrompts && settings.optimizeBackgroundPrompts.en ? settings.optimizeBackgroundPrompts.en : ''
                }
            };
            currentPromptLanguage = settings.promptLanguage === 'en' ? 'en' : 'zh';
            promptLanguage.value = currentPromptLanguage;
            renderPromptDraft();
            showModal('aiSettingsModal');
        }

        function saveActivePromptDraft() {
            const language = currentPromptLanguage === 'en' ? 'en' : 'zh';
            promptDrafts[language] = {
                organize: organizePrompt.value,
                optimizeBackground: optimizeBackgroundPrompt.value
            };
        }

        function renderPromptDraft() {
            const language = currentPromptLanguage === 'en' ? 'en' : 'zh';
            organizePrompt.value = promptDrafts[language].organize || '';
            optimizeBackgroundPrompt.value = promptDrafts[language].optimizeBackground || '';
        }

        function formatSnapshotLabel(snapshot) {
            const date = new Date(snapshot.createdAtMillis || Date.now());
            const time = String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0') + ' ' + String(date.getHours()).padStart(2, '0') + ':' + String(date.getMinutes()).padStart(2, '0');
            return time + '  ' + (snapshot.label || 'Snapshot');
        }

        function showModal(id) {
            document.getElementById(id).classList.add('open');
        }

        function hideModal(id) {
            document.getElementById(id).classList.remove('open');
        }

        function post(type, payload) {
            vscode.postMessage(Object.assign({ type }, payload || {}));
        }
    </script>
</body>
</html>`;
}

function getRecordContextHtml(webview, data) {
    const nonce = getNonce();
    return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Record code context</title>
    <style>
        body { padding: 18px; color: var(--vscode-foreground); background: var(--vscode-editor-background); font-family: var(--vscode-font-family); }
        label { display: block; margin: 12px 0 6px; font-weight: 600; }
        input, textarea { box-sizing: border-box; width: 100%; color: var(--vscode-input-foreground); background: var(--vscode-input-background, var(--vscode-editor-background)); border: 1px solid var(--vscode-panel-border, #c8c8c8); border-radius: 4px; padding: 8px 9px; box-shadow: inset 0 0 0 1px rgba(127,127,127,.08); }
        input:focus, textarea:focus { outline: 1px solid var(--vscode-focusBorder); outline-offset: -1px; border-color: var(--vscode-focusBorder); }
        textarea { resize: vertical; font-family: var(--vscode-editor-font-family); line-height: 1.45; }
        .readonly { color: var(--vscode-descriptionForeground); background: var(--vscode-editorWidget-background, var(--vscode-input-background)); }
        .code { min-height: 240px; }
        .note { min-height: 132px; font-family: var(--vscode-font-family); background: var(--vscode-input-background, var(--vscode-editor-background)); }
        .actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; flex-wrap: wrap; }
        button { border: 1px solid var(--vscode-button-border, transparent); background: var(--vscode-button-secondaryBackground); color: var(--vscode-button-secondaryForeground); padding: 5px 10px; border-radius: 3px; cursor: pointer; }
        button.primary { background: var(--vscode-button-background); color: var(--vscode-button-foreground); }
    </style>
</head>
<body>
    <label>File path</label>
    <input class="readonly" value="${escapeHtml(data.relativePath)}" readonly>
    <label>Line</label>
    <input class="readonly" value="${escapeHtml(data.lineRange)}" readonly>
    ${data.codeLocation ? `<label>Location</label><input class="readonly" value="${escapeHtml(data.codeLocation)}" readonly>` : ''}
    <label>Selected code</label>
    <textarea class="readonly code" readonly>${escapeHtml(data.selectedCode)}</textarea>
    <label>What to do</label>
    <textarea id="noteText" class="note" autofocus></textarea>
    <div class="actions">
        <button id="cancelButton">Cancel</button>
        <button id="saveButton" class="primary">Record it</button>
    </div>
    <script nonce="${nonce}">
        const vscode = acquireVsCodeApi();
        document.getElementById('cancelButton').addEventListener('click', () => {
            vscode.postMessage({ type: 'cancel' });
        });
        document.getElementById('saveButton').addEventListener('click', () => {
            vscode.postMessage({ type: 'save', note: document.getElementById('noteText').value });
        });
        document.getElementById('noteText').focus();
    </script>
</body>
</html>`;
}

function safeJson(value) {
    return JSON.stringify(value).replace(/</g, '\\u003c');
}

function escapeHtml(value) {
    return String(value || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function maskApiKey(apiKey) {
    if (!apiKey) {
        return 'Not set';
    }
    if (apiKey.length <= 10) {
        return apiKey.slice(0, 2) + '*'.repeat(Math.max(0, apiKey.length - 4)) + apiKey.slice(-2);
    }
    return apiKey.slice(0, 6) + '*'.repeat(Math.min(24, apiKey.length - 10)) + apiKey.slice(-4);
}

function getNonce() {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let text = '';
    for (let index = 0; index < 32; index++) {
        text += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return text;
}

module.exports = {
    activate,
    deactivate
};
