---
title: 命令与管理
description: 使用 /maimai_dialogue 打开对话并管理在线玩家的 ProgressNode。
---

# 命令与管理

## 本章要实现什么

你将学会测试 Dialogue、授予或撤销剧情节点、查看玩家当前节点，并在命令方块中判断一个节点是否存在。

## 开始前

所有 `/maimai_dialogue` 命令默认要求 permission level 2。`<player>` 必须解析为一个在线玩家，可使用 `@s`，不能用 `@a` 批量修改。

## 需要修改的文件

本章不修改资源文件，只在游戏或服务器控制台中执行命令。

## 跟着做

### 打开 Dialogue

```text
/maimai_dialogue open <player> <dialogue_id>
```

例如：

```text
/maimai_dialogue open @s example:guide/welcome
```

打开前会检查 Dialogue 是否存在、玩家进度是否可用以及 `requires` 是否满足。命令显示"已发送"表示打开请求已经发出；如果客户端缺少对应的资源包内容，界面仍可能不显示。

### 添加节点

```text
/maimai_dialogue progress add @s guide.secret_unlocked
```

重复添加不会创建副本，并会反馈节点已经存在。

### 删除节点

```text
/maimai_dialogue progress remove @s guide.secret_unlocked
```

删除不存在的节点不会报破坏性错误，只会反馈节点原本不存在。

### 查看所有节点

```text
/maimai_dialogue progress list @s
```

节点按字典序显示；玩家没有节点时显示 `(empty)`。

### 检查一个节点

```text
/maimai_dialogue progress check @s guide.secret_unlocked
```

节点存在时命令 result 为 `1`，不存在时为 `0`，可以放进 Minecraft 的 `execute if` 条件。例如在命令方块里，只有玩家拥有 `guide.secret_unlocked` 节点时才执行后续命令：

```text
/execute if command /maimai_dialogue progress check @s guide.secret_unlocked run say 已解锁
```

## 进入游戏验证

执行下面的顺序：

```text
/maimai_dialogue progress remove @s guide.secret_unlocked
/maimai_dialogue progress check @s guide.secret_unlocked
/maimai_dialogue progress add @s guide.secret_unlocked
/maimai_dialogue progress check @s guide.secret_unlocked
/maimai_dialogue progress list @s
```

两次 `check` 应先返回不存在，再返回存在，最后 `list` 中应出现该节点。

::: info 异步反馈
`open`、`add` 和 `remove` 会异步完成访问判断或保存。最初的命令返回不代表操作已经持久化，应以后续聊天反馈和日志为准。
:::

## 如果没有生效

- 选择器选中了多人：改用能解析为单个在线玩家的参数。
- 节点格式非法：使用小写点分名称，不要使用冒号或连续点号。
- Dialogue 被拒绝：检查目标的 `requires`，并用 `progress list` 查看真实节点。
- 玩家已经打开对话：新的 open 请求不会替换当前 MaiMai Dialogue UI。

## 下一步

阅读[玩家操作](./player-controls.md)，把实际交互方式写进整合包说明；遇到问题时查看[故障排查](../reference/troubleshooting.md)。
