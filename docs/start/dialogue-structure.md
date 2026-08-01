---
title: MOD 是怎样工作的
description: 不需要编程基础，用一张流程图理解 Data Pack、Resource Pack 与 MaiMai Dialogue 如何一起工作。
---

# MOD 是怎样工作的

把 MaiMai Dialogue 想成一位负责传话和演出的导演：Data Pack 决定它“什么时候可以演”，Resource Pack 告诉它“演出是什么样子”。

<div class="maimai-flow" role="img" aria-label="MaiMai Dialogue 从准备内容到玩家看到对话的工作流程">
  <div class="maimai-flow__box maimai-flow__box--strong">
    <strong>你准备一套对话内容</strong>
    <span>同一段对话分别放进 Data Pack 和 Resource Pack</span>
  </div>

  <div class="maimai-flow__arrow">↓</div>

  <div class="maimai-flow__branches">
    <div class="maimai-flow__box">
      <strong>Data Pack 交给世界</strong>
      <span>告诉 MOD 有哪些对话和进入条件；玩家的故事进度保存在世界存档中</span>
    </div>
    <div class="maimai-flow__box">
      <strong>Resource Pack 交给玩家电脑</strong>
      <span>提供文字、人物名称、图片、动画和界面样式</span>
    </div>
  </div>

  <div class="maimai-flow__arrow">↓ MOD 在进入游戏时读取两份内容</div>

  <div class="maimai-flow__box maimai-flow__box--strong">
    <strong>某件事触发了对话</strong>
    <span>例如玩家执行命令、与角色互动，或剧情进行到某个位置</span>
  </div>

  <div class="maimai-flow__arrow">↓</div>

  <div class="maimai-flow__box">
    <strong>世界先做检查</strong>
    <span>这段对话存在吗？玩家现在可以进入吗？</span>
  </div>

  <div class="maimai-flow__arrow">↓</div>

  <div class="maimai-flow__branches">
    <div class="maimai-flow__box maimai-flow__box--muted">
      <strong>不可以进入</strong>
      <span>对话不会打开，例如玩家还没有满足条件</span>
    </div>
    <div class="maimai-flow__box maimai-flow__box--accent">
      <strong>可以进入</strong>
      <span>世界通知玩家电脑：请显示这段对话</span>
    </div>
  </div>

  <div class="maimai-flow__arrow">↓</div>

  <div class="maimai-flow__box maimai-flow__box--accent">
    <strong>玩家电脑把对话演出来</strong>
    <span>从 Resource Pack 找到对应内容，显示正文、人物、图片和动画</span>
  </div>

  <div class="maimai-flow__arrow">↓ 玩家推进文字或选择一个选项</div>

  <div class="maimai-flow__box maimai-flow__box--strong">
    <strong>继续下一段，或者结束</strong>
    <span>进入下一段时，MOD 会再次检查并显示；没有后续内容时，对话关闭</span>
  </div>
</div>

::: tip 只要记住三件事
- Data Pack 决定“这段内容现在能不能发生”。
- Resource Pack 决定“玩家看到和听到什么”。
- 两边用同一个对话 ID 找到彼此，所以修改对话时要同步更新两份文件。
:::

单人游戏也遵循这套流程，只是“世界”和“玩家电脑”都运行在你的电脑上。下一章会带你创建这两个 Pack。

[下一步：创建内容包 →](./content-project.md)

<style scoped>
.maimai-flow {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin: 28px 0;
}

.maimai-flow__box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  padding: 16px 18px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 12px;
  background: var(--vp-c-bg-soft);
  text-align: center;
}

.maimai-flow__box strong {
  color: var(--vp-c-text-1);
  font-size: 16px;
}

.maimai-flow__box span {
  color: var(--vp-c-text-2);
}

.maimai-flow__box--strong {
  border-color: var(--vp-c-brand-2);
}

.maimai-flow__box--accent {
  background: var(--vp-c-brand-soft);
  border-color: var(--vp-c-brand-2);
}

.maimai-flow__box--muted {
  opacity: 0.72;
}

.maimai-flow__branches {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  width: 100%;
}

.maimai-flow__arrow {
  color: var(--vp-c-text-2);
  text-align: center;
}

@media (max-width: 640px) {
  .maimai-flow__branches {
    grid-template-columns: 1fr;
  }
}
</style>
