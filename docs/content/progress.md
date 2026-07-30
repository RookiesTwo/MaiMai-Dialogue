---
title: ProgressNode 与访问条件
description: 使用布尔进度节点和表达式控制 Dialogue 可访问性。
---

# ProgressNode 与访问条件

## ProgressNode

ProgressNode 是玩家拥有的一组布尔标记：

```text
quest.trader.started
quest.trader.finished
dialogue.guide.introduced
```

每个点分段必须匹配 `[a-z0-9_-]+`。不允许大写、冒号、空段或连续点。

点号只用于命名：节点没有父级继承、通配符或树形权限语义。系统也不支持数值和字符串变量。

## `requires` 表达式

Dialogue 顶层可声明：

```json
{
  "requires": "(quest.trader.started || quest.trader.ready) && !quest.trader.finished",
  "presentation": {
    "theme": "maimai_dialogue:default"
  },
  "end": {
    "exit": {
      "type": "return"
    }
  }
}
```

支持的运算符：

| 语法 | 含义 | 优先级 |
|---|---|---:|
| `!a` | 非 | 最高 |
| `a && b` | 且 | 中 |
| `a \|\| b` | 或 | 最低 |
| `( ... )` | 显式分组 | — |

表达式允许空格并采用短路求值。省略 `requires` 表示公开；空字符串或非法表达式会使该 Dialogue 加载失败。

## 服务端判定

服务端是访问结果的唯一权威：

- 直接打开 Dialogue 时计算 `requires`；
- Option 显示前批量查询目标；
- 玩家点击 Dialogue target 时再次计算。

因此播放期间进度发生变化时，以点击时的最新状态为准。

## 修改节点

管理员可以使用[进度命令](../administration/commands.md)，其他 MOD 可以使用[Java API](../integration/java-api.md)。

```text
/maimai_dialogue progress add @s quest.trader.started
/maimai_dialogue progress check @s quest.trader.started
/maimai_dialogue progress remove @s quest.trader.started
```

目前仅支持在线玩家。节点按玩家保存到世界目录：

```text
<world>/data/maimai_dialogue/progress/<player-uuid>.dat
```

文件由系统异步、原子保存；不建议手工编辑。读取损坏时进度会标记为不可用，受条件保护的 Dialogue 将拒绝访问。
