# MaiMai Dialogue Editor

MaiMai Dialogue 的客户端附属 MOD，与主 MOD 在同一仓库中开发，独立编译和打包。

**当前提供第一版可调整分区的编辑器工作台。分区内部为占位内容，尚未接入工程保存、内容编辑、播放或导出。实际 UI 效果待用户验收。**

## 打开与使用

启动 IDEA 的 `Client`（同时加载主 MOD 与编辑器），使用以下任一入口：

- 在 MOD 列表中选中 **MaiMai Dialogue Editor**，点击配置按钮；关闭编辑器返回原页面。
- 进入世界后执行客户端命令 `/maimai_dialogue_editor`，不需要 OP 权限；关闭编辑器返回游戏。

工作台包含工具栏、项目资源、对话步骤、场景预览、动作／时间轴、属性面板和状态栏七个分区。

编辑器统一使用直角样式，包括按钮各状态、分区、滚动条与悬停提示。

配色采用白色内容区、浅灰色工具栏／分区标题／状态栏，以及亮蓝色强调；文字使用深灰，悬停和按下使用浅蓝色反馈，悬停提示同步使用浅色样式。

- 拖动左右竖线调整侧栏宽度，拖动中央横线调整步骤区与动作区高度。
- 侧栏标题中的箭头用于折叠，折叠条中的箭头用于展开；右上角可恢复默认布局。
- 宽度不足时先临时折叠右栏，再折叠左栏；增大窗口后恢复。工具栏内容可横向滚动，关闭按钮固定保留。
- Escape 或左上角关闭按钮退出，背包键不退出。单人世界打开编辑器时暂停，联机世界不暂停。
- 布局状态只保留到本次编辑器关闭；窗口缩放或 View 重建保留当前布局，重新打开恢复默认值。
- 工程、保存、撤销、重做、预览、导出均为禁用占位；分类列表不读取真实资源。

## 用户验收清单

UI 显示与交互由用户验收，不编写 UI／布局效果单元测试，也不使用模型视觉或 Computer-use 验收。

1. 从两个入口分别打开、关闭编辑器，确认返回位置正确；Escape 可退出，背包键不会退出。
2. 检查七个分区、中英文文案、白色／浅灰背景、亮蓝强调色和禁用按钮。
3. 拖动四条分隔线，检查最小尺寸限制；松开鼠标或切出窗口后不再继续拖动。
4. 折叠、展开两侧栏，再恢复默认布局；检查缩小窗口的临时折叠和放大后的恢复。
5. 调整窗口尺寸、GUI scale，反复开关编辑器，检查错位、残留、异常关闭或持续拖动。

构建通过只说明编译、依赖和打包检查通过，不代表上述实际效果已验收。

## 结构与依赖

- 根 Gradle 项目 `:`：主 MOD，源码继续位于根目录 `src/`。
- 子项目 `:editor`：编辑器，源码位于 `editor/src/`。
- `editor/gradle.properties` 定义编辑器自己的 MOD ID、版本和描述。
- Minecraft、NeoForge、ModernUI 版本继承根 `gradle.properties`，Java toolchain 为 21。
- `gradle/modernui.gradle` 维护两个 MOD 共用的 ModernUI 依赖。
- 编辑器通过 `implementation project(':')` 引用主 MOD，不复制或嵌入其类和资源。
- 两个 MOD 使用不同 Java package 和资源 namespace；编辑器翻译和后续素材放在 `editor/src/main/resources/assets/maimai_dialogue_editor/`。

## IDEA 构建与启动

修改结构后在 IDEA 中重新加载根 Gradle 项目，使用共享运行配置：

| 配置 | Gradle 任务 | 结果 |
| --- | --- | --- |
| Build Main | `:build` | 主 MOD JAR，保持现有不自动运行测试的行为 |
| Build Editor | `:editor:build` | 编辑器 JAR，以及主 MOD 必要的依赖产物 |
| Build All | `:build :editor:build` | 两个 MOD 各自的 JAR |
| Prepare Client | `:prepareClientRun` | 编译两个 MOD 并准备统一 Client 启动配置，不启动游戏 |

裸 `build` 会选择根项目和子项目的同名任务；已有本地 `Build` 配置如果仍使用它，会构建两个 MOD。只构建主 MOD 时使用 `Build Main`。

产物默认位置：

```text
build/libs/maimai_dialogue-<主 MOD 版本>.jar
editor/build/libs/maimai_dialogue_editor-<编辑器版本>.jar
```

Gradle 同步后统一使用根项目的 `Client` 启动配置，同时加载主 MOD 与编辑器，工作目录为根目录 `run/`。启动前自动执行 `:prepareClientRun`，保证两个 MOD 的代码和资源已编译。编辑器子项目不再生成单独的启动配置或 `:editor:runClient`／`:editor:prepareClientRun` 任务。

`Server`、`Data`、`GameTestServer` 和主 MOD 单元测试只加载主 MOD。编辑器依旧单向依赖主 MOD，`Build Main`／`Build Editor`／`Build All` 和独立 JAR 保持原有用途。

原 `editor/run/` 下的配置、资源包和测试世界保留原样；统一入口使用根 `run/`，不会自动迁移或覆盖已有内容。

本项目的构建、测试与启动验证通过 IDEA MCP 执行。`Tests` 为本地测试配置，未纳入版本控制；构建成功不代表游戏内界面已经验证。

## 安装与后续边界

内容作者客户端需要安装主 MOD、ModernUI 和编辑器。当前编辑器元数据精确匹配构建时主 MOD 版本；主 MOD 升级后需同步构建编辑器。服务器和普通玩家不需要安装编辑器。

当前入口适配位于 `client/EditorScreens`，界面与布局位于 `client/ui/`；只复用主 MOD 公开的 `ResponsiveFrameLayout`。后续工程管理、属性编辑、撤销重做、素材导入和导出仍放在本子项目，模型、Codec 和播放逻辑按需求复用主 MOD。

编辑器设计记录在仓库本地 `.project-notes/dialogue-editor-design.md`，该目录按现有规则不纳入版本控制。
