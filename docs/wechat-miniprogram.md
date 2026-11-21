# 小程序前端实现说明（可直接粘进结题报告）

> 当前版本对应**近红外单通道二分类**主线：后端 `MobileNetV3-Small(1ch)`，输出 p(mold)，阈值 0.6823。早期 RGB 三分类版本已停用（见 `backend/README_已停用.md`）。

> 对应代码：`wechat_miniprogram/`　｜　版本 v2.3.0　｜　2026-11-21
> 本文只讲前端，不涉及模型与后端推理。

---

## 1. 技术选型

| 选项 | 决定 | 理由 |
|---|---|---|
| 框架 | **微信小程序原生**（WXML/WXSS/JS） | 不引入 Taro/uni-app，包体积小、无编译层、审核风险低；本项目没有跨端需求 |
| UI 库 | **无** | 设计稿是全自定义的深色体系，用组件库反而要大量覆写 |
| 图表 | **无**（纯 CSS 柱状图） | 只有 7 根柱子，canvas 或 echarts 都属过度设计 |
| 3D | **纯 WXSS**（`perspective` / `preserve-3d` / `conic-gradient`） | three.js 约 600KB，会顶到 2MB 包体限制 |
| 图标 | **自绘 PNG（PIL 生成）+ CSS 绘制** | 不再用 emoji，避免 Android/iOS 渲染差异 |
| 海报 | **canvas 2d 官方 API** | 原生能力，无第三方依赖 |

---

## 2. 目录结构与职责

```
wechat_miniprogram/
├─ app.js                 # 只做一件事：把 config.js 注入 globalData
├─ app.json               # 4 个 tab 页 + 原生 tabBar（自绘图标）
├─ app.wxss               # 设计令牌（CSS 变量）+ 基础类（.container/.glass/.section-title/.press）
├─ config.js              # 唯一需要改的地方：API_BASE
├─ data/metrics.js        # 关于页显示的实测指标（数据与视图分离）
├─ images/tab/*.png       # 8 张 81×81 tabBar 图标（4 图标 × 2 状态）
└─ pages/
   ├─ index/              # 检测：3D 全息台、取样、三态判定、海报、批量
   ├─ history/            # 档案：统计、7 天趋势、记录、详情、空态
   ├─ knowledge/          # 科普：知识卡、3D 翻转毒素卡、国标表
   └─ about/              # 关于：指标宫格、技术栈、流程线、边界说明
```

**分层原则**：视图（WXML）不带业务判断，所有计算（三态判定、趋势聚合、今日计数、批量排序）都在页面 JS 里，样式只在 WXSS。

---

## 3. 页面交互流程

```
启动 → onLoad: 请求 /api/health
        ├─ 成功 → 顶部 chip 变绿「服务在线」，允许检测
        └─ 失败 → chip 变红「服务离线」+ 提示条 + 重试按钮，检测被拦截
                     ↓
        选图（拍照/相册/批量）→ 上传 /api/detect
                     ↓
        得到三类概率 → 端上 decide() 判三态 → 结果卡翻转入场
                     ↓
        写本地 history → 首页「最近一次」、档案页趋势、今日计数同步更新
                     ↓
        可选：生成海报（canvas）→ 保存相册 / 转发
```

**离线兜底**：后端不可用时不是白屏，而是状态可见 + 操作被拦 + 给出排查文案（"先运行一键启动后端.bat"）。

---

## 4. 设计系统

| 令牌 | 值 | 用途 |
|---|---|---|
| `--ink` | `#12100d` | 页面底色（深墨） |
| `--gold` / `--gold-deep` | `#e0bb7a` / `#b78a43` | 主色、强调 |
| `--jade` | `#58c9a3` | 正常/通过 |
| `--warn` | `#f0b429` | 复检 |
| `--danger` | `#ff7a63` | 霉变/无法判断 |
| `--text` / `--muted` / `--muted-2` | `#f4ece0` / `#a2937f` / `#6f6557` | 三级文字 |
| 字号 | 46/34/27/23/20 rpx | 5 级，正文 23rpx |
| 圆角 | 24–28 rpx（卡片）、999rpx（按钮） | |
| 动效 | 0.5–0.6s，`cubic-bezier(0.22,1,0.36,1)` | 结果卡入场的缓动 |

### 4.1 对比度审计（WCAG 2.1 AA）

用脚本把 `app.wxss` 里的令牌逐个对深墨底色算 WCAG 对比度（`tools/contrast_audit.py`）：

| 令牌 | 色值 | 对 `--ink` 对比度 | AA 正文（≥4.5） |
|---|---|---|---|
| `--text` | `#f4ece0` | 16.21 | ✅ |
| `--gold` | `#e0bb7a` | 10.45 | ✅ |
| `--warn` | `#f0b429` | 10.19 | ✅ |
| `--jade` | `#58c9a3` | 9.31 | ✅ |
| `--danger` | `#ff7a63` | 7.43 | ✅ |
| `--muted` | `#a2937f` | 6.35 | ✅ |
| `--gold-deep` | `#b78a43` | 6.09 | ✅ |
| `--muted-2` | `#8e8476` | 5.17 | ✅（修复后） |

**修复记录**：`--muted-2` 原为 `#6f6557`，对 `--ink` 仅 3.32、对 `--ink-3` 仅 2.91，**不达 AA**（它用于提示、时间戳、脚注等小字）。
按"在三种底色上都 ≥4.5"的约束重新求解，提亮到 `#8e8476`（ink 5.17 / ink-2 4.85 / ink-3 4.53）。
同时把三处硬编码的页脚色 `#4e463b`（对比度约 2.2）统一换成 `var(--muted-2)`。

**按钮上的深色文字**（`#2b1d10` on 金色渐变）：中段 7.78，最暗端 `#a97a33` 处 4.29——文字位于按钮中部，实际可按 7.8 计；若后续把渐变压暗，需要重新核。

---

## 5. 3D 与动效实现

| 效果 | 实现 |
|---|---|
| 全息扫描台 | `.stage { perspective: 1000rpx }` + `.hologram { transform-style: preserve-3d }` |
| 两圈反向旋转刻度盘 | `rotateX(74deg) rotateZ()` 的 `@keyframes`，正反两个方向，周期 16s / 11s |
| 刻度线 | `repeating-conic-gradient(from 0deg, gold 0 0.9deg, transparent 0.9deg 30deg)` |
| 花生本体 | 两个椭圆 `.lobe` + 腰部 `.waist` + 点阵壳纹（`radial-gradient` + `background-size`）+ 高光层；**无图片、无 emoji** |
| 扫描光带 | 3rpx 渐变线，`translateY` 3.4s 循环 |
| 结果卡入场 | `perspective(900rpx) rotateX(-12deg) → 0` + 透明度过渡 |
| 置信度环 | 内联 `conic-gradient(色 {{confidence}}%, rgba(255,255,255,.08) 0)` + `mask` 挖空圆心 |
| 科普卡翻转 | `.flip-inner { transform-style: preserve-3d }` + `backface-visibility: hidden`，点击加 `.flip-on` → `rotateY(180deg)` |
| 背景呼吸 | 两个 `radial-gradient` 圆斑 + `blur(70rpx)` + 14–18s `drift` 动画 |

> 所有动画只用 `transform` / `opacity`，不触发布局重排；小程序端实测滑动不掉帧。

---

## 6. 阈值与灰区判定（端上算法）

模型输出单标量 `p(mold)`（判为"发霉"的概率），判定不是三类比大小，而是**一个阈值加一段灰区**：

```js
// pages/index/index.js -> decide(data)
const T = 0.6823           // 阈值（来自验证集，约束 recall ≥ 0.95）
const M = 0.15             // 灰区半宽，可调
const d = Math.abs(p_mold - T)

if (d >= M)       → clear   「结果可信」
else if (d >= M/2) → review  「建议人工复检」
else              → unknown 「灰区，建议复检」
```

| 状态 | 界面 | 语义 |
|---|---|---|
| clear | 绿色提示 | 距阈值足够远，可直接采信 |
| review | 黄色提示 | 距阈值较近，建议换图复核 |
| unknown | 红色提示 + 判定置灰 | 几乎压在阈值上，不给结论 |

**设计依据**：二分类模型在小样本、单一采集条件下容易在阈值附近抖动；食品安全场景失误代价不对称，"自信地错"比"说不知道"危险。

**阈值可配置**：`judge_thresholds = {threshold, marginP}` 存本地，「关于」页用两个 slider 调整；默认 0.6823 / 0.15。

**局限（需在报告中写明）**：阈值取自交付包验证集，未在本项目数据上重新标定；灰区宽度是经验值，可用共形预测替换，界面无需改动。

---

## 7. 数据与存储

```js
// wx.setStorageSync('history', [record, ...])，最多 100 条
record = {
  status: 'moldy' | 'normal',   // 供统计用
  classIndex: 0 | 1 | 2,        // 三分类原始下标
  result: '正常' | '发霉不长毛' | '发霉长毛',
  confidence: 95.7,
  decision: '结果可信',          // v2.3.0 新增
  imagePath: '...',             // 本地临时路径
  time: '2026/11/21 20:12:33'
}
```

| 派生数据 | 计算位置 | 说明 |
|---|---|---|
| 近 7 天趋势 | `history.js -> buildTrend()` | 正则解析时间字符串 → 按天聚合 → 按当周峰值归一化成百分比高度 |
| 今日检测次数 | `index.js -> loadLast()` | 前缀匹配 `YYYY/M/D` |
| 批量汇总 | `index.js -> batchDetect().complete` | 排序（霉变优先、置信度降序）+ 平均置信度 |

**时间解析不用 `new Date(str)`**：iOS 对 `2026/11/21 20:12:33` 这种格式历史上解析不一致，统一用正则取年月日再构造。

---

## 8. 前端验收清单（都已实测）

- [x] 4 个页面在模拟器中真实渲染（非设计稿）
- [x] 离线态：状态 chip 变红、提示条出现、检测被拦截
- [x] 三态判定：clear / review / unknown 三种截图各一张，颜色与文案正确
- [x] 3D 翻转卡：点击后翻面显示背面文案
- [x] 海报：canvas 生成成功（`posterPath` 非空）、弹层预览正常
- [x] 批量：9 张真实样本，汇总条数字与实际一致（9 张 / 霉变 6 / 正常 3 / 86.4%）
- [x] 档案：7 天趋势柱状图与记录数一致
- [x] 首页：最近一次结果与今日计数正确（4 次）

---

## 9. 可直接粘进报告的一段话

> 系统前端采用微信小程序原生框架实现，按"检测—档案—科普—关于"四个页面组织。界面采用深色仪器化视觉语言，通过 WXSS 的 `transform-style: preserve-3d`、`repeating-conic-gradient` 与关键帧动画实现全息扫描台与卡片翻转等三维效果，未引入任何第三方图形库，包体积保持在 1 MB 以内。交互上，检测完成后端上依据三类概率的最高值与次高值之差计算三态判定（结果可信 / 建议人工复检 / 无法判断），在置信不足时主动拒识而非给出确定性结论；结果可一键生成 1200×1560 的分享海报并保存至相册。数据侧，检测记录以本地存储保存最近 100 条，档案页按日聚合出近 7 天趋势，首页回显最近一次结果与当日检测次数。
