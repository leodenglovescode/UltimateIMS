# 构建

需要 JDK 21、Python 3.12+、CMake 3.22+、Ninja，以及 Android SDK。项目固定使用 Gradle 9.8.0、AGP 9.4.1、API 37 和 NDK 28.1.13356709。

1. 安装对应 SDK/NDK，将 SDK 路径设为 ANDROID_HOME 或 ANDROID_SDK_ROOT；也可以在不提交的 local.properties 中设置 sdk.dir。
2. 确认 JAVA_HOME 指向 JDK 21，python3、cmake、ninja 可用。
3. 执行：

```sh
python3 scripts/check_locales.py
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

APK 位于 app/build/outputs/apk/debug/app-debug.apk。这是调试构建，不是生产签名发布包。

首次构建需要联网解析官方依赖，并下载固定 revision 的 AOSP BoringSSL。脚本核对归档 SHA-256 后构建 JNI；不从参考 IMS 应用获取二进制。构建缓存、依赖源码和中间产物不应提交。

可选配对 JNI 主机测试：先完成依赖构建，再执行 python3 scripts/test_pairing_host.py。主机测试不能代替真实 Android 无线配对流程。

两种界面语言始终随包提供，无动态语言下载。新增翻译需添加资源、扩展 LanguagePolicy/选择器与 locales_config，并运行翻译键和占位符检查。
