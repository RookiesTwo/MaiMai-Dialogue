import { defineConfig } from "vitepress";
import { withMermaid } from "vitepress-plugin-mermaid";

export default defineConfig(withMermaid({
  lang: "zh-CN",
  title: "MaiMai Dialogue",
  description: "MaiMai Dialogue 内容制作教程与参考资料",
  base: "/MaiMai-Dialogue/",
  cleanUrls: true,
  lastUpdated: true,
  markdown: {
    mermaid: {
      theme: "neutral",
      flowchart: { curve: "linear" },
    },
  },
  themeConfig: {
    search: {
      provider: "local",
    },
    nav: [
      { text: "开始使用", link: "/start/installation" },
      { text: "编写对话", link: "/dialogue/steps" },
      { text: "制作画面", link: "/scene/background" },
      { text: "发布与管理", link: "/publish/client-server" },
      {
        text: "更多",
        items: [
          { text: "常见任务速查", link: "/reference/quick-recipes" },
          { text: "概念总览", link: "/concepts/overview" },
          { text: "术语表", link: "/concepts/glossary" },
          { text: "Java API", link: "/integration/java-api" },
          { text: "参考资料", link: "/reference/resource-paths" },
          { text: "故障排查", link: "/reference/troubleshooting" },
        ],
      },
    ],
    sidebar: [
      {
        text: "开始使用",
        items: [
            { text: "安装", link: "/start/installation" },
            { text: "认识 Dialogue", link: "/start/dialogue" },
            { text: "创建内容包", link: "/start/content-project" },
          { text: "第一段对话", link: "/start/first-dialogue" },
        ],
      },
      {
        text: "核心概念",
        items: [
          { text: "概念总览", link: "/concepts/overview" },
          { text: "会话与导航", link: "/concepts/session" },
          { text: "术语表", link: "/concepts/glossary" },
        ],
      },
      {
        text: "编写对话",
        items: [
          { text: "步骤与推进", link: "/dialogue/steps" },
          { text: "显示 Speaker", link: "/dialogue/speaker" },
          { text: "编写 Markdown 正文", link: "/dialogue/markdown" },
          { text: "选项与子对话", link: "/dialogue/choices" },
          { text: "Progress 条件", link: "/dialogue/progress" },
        ],
      },
      {
        text: "制作画面",
        items: [
          { text: "添加背景", link: "/scene/background" },
          { text: "添加 VisualObject", link: "/scene/visual-objects" },
          { text: "播放 SceneAction", link: "/scene/actions" },
          { text: "调整对话框布局", link: "/scene/dialogue-box" },
          { text: "添加场景滤镜", link: "/scene/filters" },
          { text: "制作 Theme", link: "/scene/themes" },
        ],
      },
      {
        text: "发布与管理",
        items: [
          { text: "双端发布", link: "/publish/client-server" },
          { text: "命令与管理", link: "/publish/commands" },
          { text: "玩家操作", link: "/publish/player-controls" },
          { text: "客户端设置", link: "/publish/client-settings" },
        ],
      },
      {
        text: "开发者接入",
        items: [
          { text: "Java API", link: "/integration/java-api" },
        ],
      },
      {
        text: "参考资料",
        items: [
          { text: "常见任务速查", link: "/reference/quick-recipes" },
          { text: "内置演示内容", link: "/reference/built-in-content" },
          { text: "资源路径与 ID", link: "/reference/resource-paths" },
          { text: "故障排查", link: "/reference/troubleshooting" },
          { text: "当前限制", link: "/reference/limitations" },
          {
            text: "对话与条件",
            collapsed: true,
            items: [
              { text: "Dialogue JSON", link: "/reference/dialogue-json" },
              { text: "Progress 表达式", link: "/reference/progress-expression" },
            ],
          },
          {
            text: "画面",
            collapsed: true,
            items: [
              { text: "Presentation JSON", link: "/reference/presentation-json" },
              { text: "演出配置文件", link: "/reference/presentation-definition-json" },
              { text: "Scene JSON", link: "/reference/scene-json" },
              { text: "VisualAsset JSON", link: "/reference/visual-asset-json" },
            ],
          },
          {
            text: "动画",
            collapsed: true,
            items: [
              { text: "SceneAction JSON", link: "/reference/scene-action-json" },
              { text: "内置预设 SceneAction", link: "/reference/preset-actions" },
            ],
          },
          {
            text: "主题",
            collapsed: true,
            items: [
              { text: "Theme JSON", link: "/reference/theme-json" },
            ],
          },
        ],
      },
    ],
    socialLinks: [
      { icon: "github", link: "https://github.com/RookiesTwo/MaiMai-Dialogue" },
    ],
    outline: {
      level: [2, 3],
      label: "本页目录",
    },
    docFooter: {
      prev: "上一页",
      next: "下一页",
    },
    lastUpdated: {
      text: "最后更新",
    },
  },
}));
