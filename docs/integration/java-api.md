---
title: Java API
description: 从第三方 NeoForge MOD 打开 Dialogue 并管理在线玩家进度。
---

# Java API

## 本章要实现什么

让另一个 NeoForge MOD 在服务端为在线玩家打开 Dialogue、读取 Progress 快照，并可靠地添加或删除 ProgressNode。

## 开始前

- Minecraft 1.21.1、NeoForge 21.x 与 Java 21 开发环境；
- 你的运行实例已经安装 MaiMai Dialogue；
- 要打开的 Dialogue 已按[双端发布](../publish/client-server.md)提供给服务端和客户端。

## 需要修改的文件

通常需要修改第三方 MOD 的构建依赖、`neoforge.mods.toml` 和调用 API 的服务端 Java 类。

## 跟着做

### 1. 声明构建依赖

当前仓库没有承诺公开 Maven 坐标。使用本地 jar 时，将 MaiMai Dialogue 放入调用方工程的 `libs`，并在 Gradle 中加入：

```groovy
dependencies {
    implementation files("libs/maimai_dialogue-0.1.0-alpha.jar")
}
```

如果两个 MOD 位于同一个多项目构建中，可改用对应的 `project(...)` 依赖。

### 2. 声明 MOD 依赖

在调用方的 `neoforge.mods.toml` 中加入，并把 `your_mod_id` 替换成自己的 mod ID：

```toml
[[dependencies.your_mod_id]]
modId = "maimai_dialogue"
type = "required"
versionRange = "[0.1.0-alpha,)"
ordering = "AFTER"
side = "BOTH"
```

### 3. 获取公开入口

```java
import top.rookiestwo.maimai_dialogue.api.MaiMaiDialogueApi;

MaiMaiDialogueApi api = MaiMaiDialogueApi.get();
```

不要直接依赖 `client`、`network`、`server`、`internal` 或具体的 progress 实现类。

### 4. 打开 Dialogue

```java
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import top.rookiestwo.maimai_dialogue.api.DialogueOpenResult;
import top.rookiestwo.maimai_dialogue.api.MaiMaiDialogueApi;

ServerPlayer player = /* 当前在线玩家 */;
ResourceLocation dialogueId = ResourceLocation.fromNamespaceAndPath(
        "example",
        "guide/welcome"
);

MaiMaiDialogueApi.get()
        .dialogues()
        .open(player, dialogueId)
        .whenComplete((result, error) -> {
            if (error != null) {
                // 记录异常并向玩家提供失败反馈。
                return;
            }

            switch (result) {
                case SENT -> {
                    // 服务端已经向客户端发送打开请求。
                }
                case DIALOGUE_NOT_FOUND -> {
                }
                case REQUIREMENTS_NOT_MET -> {
                }
                case PROGRESS_UNAVAILABLE -> {
                }
                case INTERNAL_ERROR -> {
                }
            }
        });
```

`SENT` 只表示 payload 已发送。客户端缺少本地资源或已经打开另一个 Dialogue 时，仍可能不显示新界面。

### 5. 管理 Progress

```java
import top.rookiestwo.maimai_dialogue.api.MaiMaiDialogueApi;
import top.rookiestwo.maimai_dialogue.progress.ProgressNode;

var progress = MaiMaiDialogueApi.get().progress();
var node = new ProgressNode("guide.secret_unlocked");

progress.add(player, node).thenAccept(result -> {
    // ADDED 或 ALREADY_PRESENT
});

progress.contains(player, node).thenAccept(present -> {
    // present 表示当前快照中是否存在节点。
});

progress.snapshot(player).thenAccept(snapshot -> {
    snapshot.playerId();
    snapshot.nodes();
    snapshot.contains(node);
});

progress.remove(player, node).thenAccept(result -> {
    // REMOVED 或 NOT_PRESENT
});
```

`ProgressSnapshot.nodes()` 是不可变集合。`add` 与 `remove` 返回的 CompletionStage 会在持久化成功后完成。

## 进入游戏验证

从调用方 MOD 的服务端事件、命令或任务完成回调中执行 API：

1. 为在线玩家添加 `guide.secret_unlocked`；
2. 等待 CompletionStage 成功完成；
3. 调用 `dialogues().open(player, example:guide/welcome)`；
4. 确认受保护选项已经出现。

## 如果没有生效

- 启动时报缺失 MOD：检查 `neoforge.mods.toml` 的 `modId` 和运行环境 jar。
- 编译找不到 API：检查本地 jar 是否在调用方 compile classpath。
- 抛出线程异常：从玩家所属 logical server thread 发起 Progress API 调用。
- CompletionStage 异常完成：记录原始异常，不要把进度保存失败当成成功。
- 使用离线玩家失败：当前 API 只接受在线 `ServerPlayer`。

## API 签名

```java
CompletionStage<DialogueOpenResult> open(
        ServerPlayer player,
        ResourceLocation dialogueId
);

CompletionStage<ProgressSnapshot> snapshot(ServerPlayer player);
CompletionStage<Boolean> contains(ServerPlayer player, ProgressNode node);
CompletionStage<ProgressChangeResult> add(
        ServerPlayer player,
        ProgressNode node
);
CompletionStage<ProgressChangeResult> remove(
        ServerPlayer player,
        ProgressNode node
);
```

## 下一步

查阅 [Dialogue JSON](../reference/dialogue-json.md) 和 [Progress 表达式](../reference/progress-expression.md)，确保代码使用的 ID 与内容包一致。
