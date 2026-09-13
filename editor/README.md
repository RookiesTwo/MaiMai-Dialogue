# MaiMai Dialogue Editor

MaiMai Dialogue 的客户端附属 MOD，与主 MOD 在同一仓库中开发，独立编译和打包。

**当前只包含项目骨架，还没有编辑界面、工程保存或导出功能。**

## 结构与依赖

- 根 Gradle 项目 `:`：主 MOD，源码继续位于根目录 `src/`。
- 子项目 `:editor`：编辑器，源码位于 `editor/src/`。
- `editor/gradle.properties` 定义编辑器自己的 MOD ID、版本和描述。
- Minecraft、NeoForge、ModernUI 版本继承根 `gradle.properties`，Java toolchain 为 21。
- `gradle/modernui.gradle` 维护两个 MOD 共用的 ModernUI 依赖。
- 编辑器通过 `implementation project(':')` 引用主 MOD，不复制或嵌入其类和资源。
- 两个 MOD 使用不同 Java package 和资源 namespace；后续编辑器素材放在 `editor/src/main/resources/assets/maimai_dialogue_editor/`。

## IDEA 构建与启动

修改结构后在 IDEA 中重新加载根 Gradle 项目，使用共享运行配置：

| 配置 | Gradle 任务 | 结果 |
| --- | --- | --- |
| Build Main | `:build` | 主 MOD JAR，保持现有不自动运行测试的行为 |
| Build Editor | `:editor:build` | 编辑器 JAR，以及主 MOD 必要的依赖产物 |
| Build All | `:build :editor:build` | 两个 MOD 各自的 JAR |
| Prepare Editor Client | `:editor:prepareClientRun` | 准备双 MOD 开发启动配置，不启动游戏 |

裸 `build` 会选择根项目和子项目的同名任务；已有本地 `Build` 配置如果仍使用它，会构建两个 MOD。只构建主 MOD 时使用 `Build Main`。

产物默认位置：

```text
build/libs/maimai_dialogue-<主 MOD 版本>.jar
editor/build/libs/maimai_dialogue_editor-<编辑器版本>.jar
```

Gradle 同步后 ModDevGradle 生成 `Editor Client` 启动配置，同时加载主 MOD 与编辑器，工作目录为 `editor/run/`。原有根项目 `Client` 和 `Server` 不加载编辑器。两个运行目录各自维护资源包、配置和测试世界。

本项目的构建、测试与启动验证通过 IDEA MCP 执行。`Tests` 为本地测试配置，未纳入版本控制；构建成功不代表游戏内界面已经验证。

## 安装与后续边界

内容作者客户端需要安装主 MOD、ModernUI 和编辑器。当前编辑器元数据精确匹配构建时主 MOD 版本；主 MOD 升级后需同步构建编辑器。服务器和普通玩家不需要安装编辑器。

后续工程管理、属性面板、撤销重做、素材导入和导出放在本子项目；模型、Codec 和播放逻辑复用主 MOD，必要时由主 MOD 提供明确的预览接口。

编辑器设计记录在仓库本地 `.project-notes/dialogue-editor-design.md`，该目录按现有规则不纳入版本控制。
