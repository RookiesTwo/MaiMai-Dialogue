---
title: 命令参考
description: 打开 Dialogue 并管理在线玩家的 ProgressNode。
---

# 命令参考

所有 `/maimai_dialogue` 命令默认要求 permission level 2。`<player>` 使用单个在线玩家参数，可用 `@s` 或能解析为一人的 selector，不能直接使用 `@a` 批量操作。

## 打开 Dialogue

```text
/maimai_dialogue open <player> <dialogue_id>
```

示例：

```text
/maimai_dialogue open @s example:guide/root
```

服务端会检查 Dialogue 是否存在、Progress 数据是否可用以及 `requires` 是否满足。命令反馈可能为：

- 已向玩家发送打开请求；
- Dialogue 不存在；
- 玩家不满足条件；
- Progress 数据不可用；
- 内部错误。

“已发送”不保证客户端最终显示成功；客户端仍需拥有同 ID Dialogue 及全部表现资源。如果玩家已有 MaiMai Dialogue UI，新请求会被客户端忽略。

## 添加节点

```text
/maimai_dialogue progress add <player> <node>
```

```text
/maimai_dialogue progress add @s quest.guide.started
```

重复添加会反馈 `ALREADY_PRESENT` 对应信息，不会创建重复节点。

## 删除节点

```text
/maimai_dialogue progress remove <player> <node>
```

删除不存在的节点会反馈 `NOT_PRESENT` 对应信息。

## 列出节点

```text
/maimai_dialogue progress list <player>
```

节点按字典序输出；没有节点时显示 `(empty)`。

## 检查节点

```text
/maimai_dialogue progress check <player> <node>
```

存在时 Brigadier result 为 `1`，不存在时为 `0`，适合与 Minecraft 命令条件组合：

::: info 异步反馈
`open`、`add` 和 `remove` 会异步完成访问判断或保存。命令最初返回不代表最终操作成功，应以随后显示的命令反馈和日志为准。
:::
