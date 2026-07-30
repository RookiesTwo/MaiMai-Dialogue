import { defineConfig } from "vitepress";

export default defineConfig({
  lang: "zh-CN",
  title: "MaiMai Dialogue",
  description: "MaiMai Dialogue 用户文档",
  base: "/MaiMai-Dialogue/",
  cleanUrls: true,
  lastUpdated: true,
  themeConfig: {
    search: {
      provider: "local",
    },
    nav: [
      { text: "快速入门", link: "/guide/quick-start" },
      { text: "内容制作", link: "/content/resources" },
      { text: "命令", link: "/administration/commands" },
      { text: "Java API", link: "/integration/java-api" },
    ],
    sidebar: [
      {
        text: "开始使用",
        items: [
          { text: "项目介绍", link: "/" },
          { text: "安装与双端分发", link: "/guide/installation" },
          { text: "快速入门", link: "/guide/quick-start" },
          { text: "玩家交互", link: "/guide/player-experience" },
        ],
      },
      {
        text: "内容制作",
        items: [
          { text: "资源组织", link: "/content/resources" },
          { text: "Dialogue 与流程", link: "/content/dialogues" },
          { text: "Speaker 与正文", link: "/content/speakers-and-text" },
          { text: "场景表现", link: "/content/presentation" },
          { text: "PresentationAction", link: "/content/actions" },
          { text: "Theme", link: "/content/themes" },
          { text: "ProgressNode 与访问条件", link: "/content/progress" },
        ],
      },
      {
        text: "管理与集成",
        items: [
          { text: "命令参考", link: "/administration/commands" },
          { text: "Java API", link: "/integration/java-api" },
        ],
      },
      {
        text: "参考",
        items: [
          { text: "故障排查", link: "/reference/troubleshooting" },
          { text: "当前限制", link: "/reference/limitations" },
        ],
      },
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
});

