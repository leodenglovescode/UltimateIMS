# 外观与易用性

设置页提供以下本机选项，均不需要特权后端：

- Material（默认）或磨砂玻璃风格。玻璃风格在分组、工具栏与悬浮导航下采样同一壁纸，使用共享 RenderNode 与 RenderEffect 只模糊面板背后的区域，文字和开关不参与模糊；按功能分组，不为每个选项增加卡片。
- 自动跟随系统（默认）、浅色、深色；动态配色默认开启。
- 从系统照片选择器导入背景、移除照片。导入在工作线程执行，限制解码尺寸，重编码后原子替换；失败保留旧背景。不需要整个相册的读取权限。
- 背景可见度（0–100%）、玻璃不透明度（10–90%）与模糊（0–40dp）均可实时调整，拖动时无需重建 Activity。玻璃默认背景可见度 90%、面板不透明度 38%、模糊 18dp；未保存可见度时，Material 使用较低的背景可见度。
- 实色背景：隐藏照片、渐变和半透明装饰，优先可读性。
- 紧凑行距：开关及普通行的最小触控高度从 56dp 调整为 48dp；字体放大时允许行高继续增加。
- 应用打开时保持屏幕常亮（默认关闭），方便查看实时诊断；不修改系统休眠时间或电源策略。
- 重置外观：恢复默认主题、移除照片并关闭常亮；不清除后端选择、语言、配置历史、SIM 原值和恢复备份。
- 关于页显示作者 `leodenglovescode` 和版本，并保留依赖许可证入口。

照片保存在应用不参与备份的私有目录，重新启动不依赖来源 URI 的持续授权。透明度过高会降低复杂壁纸上的文字对比度，可提高玻璃不透明度或选择实色背景。渲染使用共享模糊层、轻薄染色、柔和高光、亮边与圆角阴影，滚动时重新对齐背景采样；这是 Android 上独立实现的磨砂玻璃界面，不宣称采用其他平台的 Liquid Glass 框架。

## 验收边界

构建、Android lint 与翻译键检查只能验证静态集成；不能冒充设备视觉验收。本轮已在 Android 17 设备安装，并通过截图检查清晰壁纸、玻璃面板与悬浮导航的显示。截图不代表全部对比度、性能或交互验收已通过。设备检查应覆盖：

| 检查 | 预期 |
|---|---|
| 首次启动 / 系统明暗切换 | 默认跟随系统；配置页和草稿在外观重建后保留 |
| Material / 玻璃 × 浅色 / 深色 | 正文和 pill 开关清晰，系统栏与主题协调 |
| 亮/暗照片、损坏文件、取消选择 | 正常照片可显示；损坏或取消时旧背景保持 |
| 选择照片或导入时旋转 / 切后台 | 状态不误触后端，完成后正确刷新或在下次打开显示 |
| 移除照片 / 重置外观 | 照片删除，SIM 配置与恢复备份保留 |
| 200% 字体、TalkBack、长中文 | 行高自适应，最小 48dp，装饰图层不朗读 |
| 常亮开关 | 仅影响应用可见窗口，不改变全局电源设置 |

API 依据：

- https://developer.android.com/training/data-storage/shared/photo-picker
- https://developer.android.com/reference/android/graphics/ImageDecoder
- https://developer.android.com/reference/android/util/AtomicFile
- https://developer.android.com/develop/ui/views/theming/darktheme

渲染 API：

- https://developer.android.com/reference/android/graphics/RenderNode
- https://developer.android.com/reference/android/graphics/RenderEffect
