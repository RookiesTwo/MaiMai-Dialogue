---
title: Progress 表达式
description: ProgressNode 命名、requires 运算符、优先级和访问行为参考。
---

# Progress 表达式

## ProgressNode

ProgressNode 是玩家拥有的一组布尔标记：

```text
guide.met
guide.secret_unlocked
quest.trader.finished
```

每个点分段必须匹配 `[a-z0-9_-]+`。不允许大写、冒号、空段或连续点号。

点号只用于命名；节点没有父级继承、通配符或树形权限语义。系统不支持数值和字符串变量。

## requires

Dialogue 顶层可声明：

```json
{
  "requires": "(guide.met || guide.invited) && !guide.finished",
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

| 语法 | 含义 | 优先级 |
|---|---|---:|
| `!a` | 非 | 最高 |
| `a && b` | 且 | 中 |
| `a \|\| b` | 或 | 最低 |
| `( ... )` | 显式分组 | — |

表达式允许空格并使用短路求值。省略 `requires` 表示无条件公开；空字符串或非法表达式会使该 Dialogue 加载失败。

## 判定时机

服务端在以下时机使用最新 Progress：

- 命令或 Java API 直接打开 Dialogue 时；
- 客户端准备显示目标选项时；
- 玩家点击 Dialogue target 时再次校验。

条件不满足的选项会隐藏。进度不可用时，公开 Dialogue 仍可进入，带 `requires` 的 Dialogue 会拒绝访问。

## 存储

节点按玩家保存在世界目录：

```text
<world>/data/maimai_dialogue/progress/<player-uuid>.dat
```

文件由系统异步、原子保存，不建议手工编辑。当前只支持在线玩家。
