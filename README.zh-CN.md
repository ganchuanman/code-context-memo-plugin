# IDE Code Context Memo Plugin

[English](README.md) | 中文

IDE Code Context Memo 用于把选中的代码片段、文件位置和任务说明收集到项目级备忘录里，方便整理后直接复制给代码 Agent。

仓库包含两端实现：

- `forIDEA`：JetBrains IDE 插件，适用于 IntelliJ IDEA 和 Android Studio。
- `forVSCode`：VS Code 插件。

## 功能

- 提供 `Code Memo` 侧边栏面板。
- 选中代码后，在编辑器右键菜单中提供 `Record code context`。
- 记录工程相对路径、行号范围、尽力识别的代码位置、选中代码和 `What to do` 说明。
- 每次记录会追加到备忘录末尾，用户可以继续手动编辑最终文本。
- 复制时只复制备忘录正文。
- 按项目或工作区持久化任务背景、备忘录正文和历史记录。
- 最多保留 20 条历史快照，并支持删除单条历史记录。
- 支持 DeepSeek/OpenAI 兼容的 chat completion API。
- 内置两套可编辑 AI 提示词：中文和英文。
- 根据当前 Prompt Language 生成备忘录字段名：
  - 中文：`文件`、`行号`、`位置`、`关键代码`、`要做什么`
  - 英文：`File`、`Line`、`Location`、`Key Code`、`What to do`
- 产品 UI 保持英文。
- 多条备忘录记录之间只保留空行，不再使用装饰性分割线。

## AI 设置

AI 设置包含：

- Endpoint
- Model
- API Key
- Prompt Language
- Organize Memo Prompt
- Optimize Task Background Prompt

默认 Endpoint 使用 DeepSeek 兼容接口：

```text
https://api.deepseek.com/chat/completions
```

默认模型：

```text
deepseek-v4-pro
```

## 安装

JetBrains IDE 安装包：

```text
dist/code-context-memo-0.7.5.zip
```

VS Code 安装包：

```text
dist/code-context-memo-vscode-0.7.5.vsix
```

JetBrains IDE 需要从磁盘安装 zip。升级插件后建议重启 IDE，确保 IDE 重新加载插件 class、菜单和 action。

## 构建

JetBrains 插件要求：

- JDK 17 或更高版本
- Gradle 9.0 或更高版本

构建命令：

```bash
cd forIDEA
gradle buildPlugin
```

VS Code 插件要求：

- Node.js
- 如果要测试本地安装，需要 VS Code CLI

检查并准备打包文件：

```bash
cd forVSCode
npm run check
npm run prepare-vsix
```

本地构建产物会生成到 `forIDEA/build/distributions/` 和 `forVSCode/build/distributions/`。当前仓库中跟踪的最新可安装插件包会复制到 `dist/` 目录。

## 存储

- JetBrains 备忘录数据按项目存储在 IDE workspace 文件中。
- VS Code 备忘录数据按 workspace 存储。
- AI 设置按应用或全局维度保存。
- API Key 通过 IDE 或 VS Code 的 secret storage 机制保存，不写入备忘录正文。
