# 来源与独立实现

UltimateIMS 使用独立 applicationId 与架构，不是任何参考应用的改名分支。

为核对功能需求和兼容行为，开发时检查过 Carrier IMS、TensorIMS 的文档及部分实现，也参考过公开的持久化 VoLTE 讨论。这不是 clean-room 声明。没有移植其第一方实现、资源、图标、构建脚本、stub 或捆绑二进制。

参考来源：

- Carrier IMS： https://github.com/ryfineZ/carrier-ims-for-pixel ，对比 revision 4312b5a9244a20444cccf5c4a5f763f6fd9d2ab5。
- TensorIMS： https://github.com/Pixel-Tailor-CN/TensorIMS ，对比 revision 0b114fe038c259f5b3889aa82f618f8873b109c7。
- 持久化 VoLTE 需求讨论： https://github.com/ryfineZ/carrier-ims-for-pixel/pull/106 。
- Android/AOSP 官方 API、框架定义及 Android Developers 文档，用于检查配置含义、权限与签名。

显示频段、带宽门槛和 SS-RSRP 预设等参数包含由参考应用行为识别的需求，按 AOSP 定义核对，不宣称自行发明。实际实现保留本项目的白名单、逐卡原值、草稿和恢复边界。

应用标志是第一方启动图标的矢量展示版本。标准依赖来自官方 Google Maven、Maven Central、Gradle 或固定 AOSP revision；版本与许可证见 DEPENDENCIES.md、runtime-dependencies.json 和根 NOTICE。没有自行实现底层密码算法。
