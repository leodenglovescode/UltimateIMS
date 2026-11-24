# README 截图

图片位于 `docs/assets/screenshots/`，来自独立 Android 16（API 36）ARM64 模拟器中的 UltimateIMS 0.9.1-dev，分辨率为 1080 × 2400。英文与简体中文分别展示 Material 浅色通话页、磨砂玻璃深色外观页和 Material 网络设置页。

- 使用实际应用界面与系统每应用语言，不修改应用源码、注册结果或配置读数。
- 后端未连接；不可读取的项目和禁用开关保持真实状态。运营商名称来自模拟器的虚拟 SIM，不是个人 SIM 数据。
- 玻璃界面使用应用内置渐变，不导入个人照片。
- 状态栏演示模式固定时钟和电量；它们是展示样式，不作为通信验证。
- PNG 直接通过模拟器 `screencap` 获取，未拼接或重绘界面；不使用实体手机截图。

重新截图时使用独立 AVD，并在每条 ADB 命令中明确指定模拟器序列号，避免误操作实体设备。例如：

```sh
adb -s emulator-5580 shell getprop ro.kernel.qemu
adb -s emulator-5580 exec-out screencap -p > screenshot.png
```

先确认第一条命令输出为 `1`，再操作该模拟器。通过应用设置选择语言、风格和明暗模式，滚动到相应页面后截图。发布前人工检查全部画面、PNG 元数据和 README 图片链接。截图只能证明界面显示，不能代替真实运营商的 IMS、5G 或通话测试。
