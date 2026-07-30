---
title: Speaker 与正文
description: 定义说话者、Markdown 正文和文字播放行为。
---

# Speaker 与正文

## Speaker 资源

路径：

```text
assets/<namespace>/speakers/<path>.json
```

例如 `assets/example/speakers/npc/guide.json` 对应 `example:npc/guide`：

```json
{
  "name": "向导"
}
```

`name` 必填且不可全为空白。当前 Speaker 只包含显示名称，不提供头像、本地化或其他元数据。

## 在 Step 中切换

设置 Speaker：

```json
{
  "speaker": {
    "type": "set",
    "id": "example:npc/guide"
  },
  "text": "你好。"
}
```

隐藏 Speaker：

```json
{
  "speaker": {
    "type": "hide"
  },
  "text": "旁白内容。"
}
```

字段省略时继承当前状态。客户端缺少 Speaker 资源时会报告内容错误，并临时显示 Speaker ID；发布包不应依赖这种回退。

## Markdown 正文

只有 Dialogue 的 `text` 解析 Markdown：

```json
{
  "text": "# 标题\n\n支持 **粗体**、*斜体* 和 `inline code`。"
}
```

Speaker 名称、Option 文本和其他 controls 都按普通字符串显示。当前版本没有文本本地化模型。

## 打字机与 Step 完成

正文按 Unicode code point 播放打字机效果。文字和本 Step 的阻塞 Action 都完成后才进入 READY：

- 播放中推进：立刻显示完整正文并结算阻塞 Action；
- READY 后推进：进入下一 Step 或执行 EndStep 的 Exit。

因此，长文本应按自然阅读段落拆分为多个 Step，而不是依赖玩家一次跳过整页内容。

