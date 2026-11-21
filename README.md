<div align="center">
  <img src="docs/assets/logo.svg" width="96" height="96" alt="UltimateIMS logo" />
  <h1>UltimateIMS</h1>
  <p>IMS &amp; network configuration, with clarity and control.</p>
  <p>
    <img alt="Android 13 and later" src="https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&amp;logoColor=white" />
    <img alt="Material 3" src="https://img.shields.io/badge/Design-Material%203-146B63" />
    <img alt="Development status" src="https://img.shields.io/badge/Status-Development-orange" />
    <a href="LICENSE"><img alt="Apache 2.0 license" src="https://img.shields.io/badge/License-Apache%202.0-blue" /></a>
  </p>
  <p><a href="#english">English</a> · <a href="#简体中文">简体中文</a></p>
</div>

## English

An independent Android app for managing IMS and connectivity settings per SIM. Built around Material 3, explicit backend selection, and verified changes.

**Features**

- VoLTE, Wi-Fi calling, VoNR, video calling, Cross-SIM calling, and supplementary-service controls.
- Separate NSA/SA configuration, 5G+ display options, and signal-bar thresholds.
- Persistent VoLTE settings on supported firmware, with per-SIM original-value backups and recovery.
- Captive-portal settings, reviewed APN profiles, local diagnostics, and an optional SIM-region compatibility setting.
- English and Simplified Chinese; system-following language and light/dark appearance.

**Get started**

1. Install a build, select a backend, and authorize it. Official [Shizuku](https://shizuku.rikka.app/) is the simplest starting point; private wireless ADB and existing-root modes are also available.
2. Select the intended SIM and read its live status.
3. Review changes before applying. Keep original-value backups for recovery.

**Development build.** Android 13+; Tensor-based Pixels are the primary target. Support varies by firmware, modem, and carrier. A setting or icon does not prove provisioning, registration, or a successful call. Wireless ADB pairing and reboot persistence still require device-specific validation.

No analytics, ads, telemetry, or automatic uploads. Network probes, public-IP queries, and exports are user-triggered. [Privacy](docs/PRIVACY.md) · [Build](docs/BUILDING.md) · [Compatibility](docs/COMPATIBILITY.md)

## 简体中文

独立开发的 Android IMS 与网络配置应用，以 Material 3 界面、明确的后端选择和经过核验的修改，提供逐 SIM 管理。

**主要功能**

- VoLTE、Wi-Fi 通话、VoNR、视频通话、跨 SIM 通话与补充业务控制。
- 独立的 NSA/SA 配置、5G+ 显示选项和信号格门槛。
- 支持固件上的持久化 VoLTE，包含逐卡原值备份与恢复。
- 联网检测门户、已审阅 APN、本地诊断，以及可选的 SIM 地区兼容设置。
- 英语与简体中文，语言及浅深色外观可跟随系统。

**开始使用**

1. 安装构建版本，选择后端并授权。建议先使用官方 [Shizuku](https://shizuku.rikka.app/)；也提供私有无线 ADB 和已有 root 模式。
2. 选择目标 SIM，读取实时状态。
3. 审阅后再应用，保留原值备份以便恢复。

**当前为开发版本。** 最低 Android 13，优先面向 Tensor Pixel；实际支持取决于固件、基带与运营商。开关或图标不能证明已开通、已注册或通话成功。无线 ADB 配对和重启持久性仍需逐设备验证。

无分析、广告、遥测或自动上传。网络探测、公网 IP 查询和导出均由用户触发。[隐私](docs/PRIVACY.md) · [构建](docs/BUILDING.md) · [兼容性](docs/COMPATIBILITY.md)

---

[Apache-2.0](LICENSE) · [Third-party notices](NOTICE) · [Provenance](docs/PROVENANCE.md)
