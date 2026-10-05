# 依赖与许可证

所有运行时依赖从官方 Google/Maven Central 或官方 AOSP 固定revision获取，不使用参考 IMS 项目的副本/二进制。

| 组件 | 版本/来源 | 许可证与用途 |
|---|---|---|
| AGP | 9.4.1，Google Maven | Apache-2.0；构建 |
| Gradle | 9.8.0，官方wrapper与services.gradle.org | Apache-2.0；构建，distribution SHA固定 |
| Android SDK / NDK | SDK37，buildtools36，NDK28.1.13356709，官方SDK Manager | SDK协议；编译平台工具 |
| JDK | 本机Homebrew OpenJDK21 | GPLv2+Classpath exception；主机构建 |
| Shizuku API/provider/aidl/shared | 13.1.5，dev.rikka.shizuku，官方RikkaApps/Shizuku-API | MIT（RikkaW）；认证UserService传输 |
| AndroidX Annotation | 1.3.0，Google Maven，Shizuku传递依赖 | Apache-2.0；标准注解 |
| HiddenApiBypass | 6.1，org.lsposed.hiddenapibypass，官方LSPosed/AndroidHiddenApiBypass | Apache-2.0；集中隐藏API访问，逐签名核验仍必须执行 |
| Conscrypt Android | 2.7.0，org.conscrypt:conscrypt-android，官方google/conscrypt | Apache-2.0及NOTICE；局部TLS1.3/exporter，不注册为全局provider |
| BoringSSL | 官方AOSP external/boringssl commit ecc1358826150d6a1851c517c325b0e6c0e1b8be | 官方LICENSE包含OpenSSL/SSLeay/ISC等文件许可；JNI SPAKE2/HKDF/AES128-GCM |
| JUnit / Hamcrest Core | 4.13.2 / 1.3，官方Maven Central | EPL-1.0 / BSD-3-Clause；主机测试，不打包运行时 |

BoringSSL官方源码归档SHA-256：`961384159d8952274dac3ddc48ccb6c172af0c1932e18d004c8a371c7bdeb6fc`。脚本初次下载验证该值，源与中间构建不提交；arm64-v8a/x86_64动态JNI由本机官方NDK构建，16KB链接页支持。未自行实现密码算法。

第三方完整版权/许可证与Conscrypt的NOTICE及Netty/Harmony许可证在`third_party/licenses`，随APK存入`assets/licenses`并在应用设置展示。根NOTICE记录打包组件；不删除或翻译原版权文本。Conscrypt自己的BoringSSL/平台代码归属以其官方分发许可证/通知为准。

项目第一方代码Apache-2.0，根LICENSE。未打包参考应用源、资源、图标、脚本、stub或adb/shizuku二进制。AGP构建时的传递依赖由官方仓库解析，与运行时dependency tree分开。

新增翻译需添加values目录、locales_config和语言选择条目，并运行`python3 scripts/check_locales.py`。所有用户界面配置标签为显式资源引用，日志保留英文；主机资源检查不能代替设备文本扩展/TalkBack验证。
