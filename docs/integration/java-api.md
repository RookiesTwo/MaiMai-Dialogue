---
title: Java API
description: 从第三方 NeoForge MOD 打开对话并管理玩家进度。
---

# Java API

公开入口为：

```java
MaiMaiDialogueApi api = MaiMaiDialogueApi.get();
```

调用方需要通过自己的构建配置依赖 MaiMai Dialogue，并把它声明为 MOD 依赖。本文只描述当前公开 API，不建议引用 `client`、`network`、`server` 或内部 repository 实现。

## 打开 Dialogue

```java
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import top.rookiestwo.maimai_dialogue.api.DialogueOpenResult;
import top.rookiestwo.maimai_dialogue.api.MaiMaiDialogueApi;

ServerPlayer player = /* 在线玩家 */;
ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
        "example",
        "guide/root"
);

MaiMaiDialogueApi.get()
        .dialogues()
        .open(player, id)
        .whenComplete((result, error) -> {
            if (error != null) {
                // 记录或处理异常
                return;
            }

            switch (result) {
                case SENT -> {
                    // S2C 打开请求已发送
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

签名：

```java
CompletionStage<DialogueOpenResult> open(
        ServerPlayer player,
        ResourceLocation dialogueId
);
```

`SENT` 只表示已发送 payload；若客户端缺少资源或已经打开对话，仍可能不显示。

## Progress API

```java
import top.rookiestwo.maimai_dialogue.api.MaiMaiDialogueApi;
import top.rookiestwo.maimai_dialogue.progress.ProgressNode;

var progress = MaiMaiDialogueApi.get().progress();
var node = new ProgressNode("quest.guide.started");

progress.add(player, node).thenAccept(result -> {
    // ADDED 或 ALREADY_PRESENT
});

progress.contains(player, node).thenAccept(present -> {
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

接口：

```java
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

`ProgressSnapshot.nodes()` 是不可变快照。`add`/`remove` 的 CompletionStage 在持久化完成后结束，调用方必须处理异常。

## 调用约束

- API 只接受在线 `ServerPlayer`。
- Progress API 应从玩家所属 logical server thread 调用；错误线程会抛出 `IllegalStateException`。
- 玩家进度尚未加载或已损坏时，操作可能同步失败或返回 exceptional CompletionStage。
- 不要直接修改 Progress NBT 文件或依赖内部 `DefaultPlayerProgressService`。
- 当前没有公开的客户端侧“请求打开” API；NPC、任务或事件集成应在服务端调用 `DialogueService.open`。

## 推荐集成方式

1. 第三方系统完成任务或事件时，通过 `progress().add/remove(...)` 更新节点。
2. 保存成功后，根据业务需要调用 `dialogues().open(...)`。
3. Dialogue 的 `requires` 仍作为服务端最终访问边界。
4. 对每种 `DialogueOpenResult` 和异常提供日志或玩家反馈。
