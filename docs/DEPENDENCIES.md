# 依赖与许可证

只从官方发行来源取标准依赖；未使用参考IMS项目的依赖副本、资源或二进制。

| 组件 | 版本 | 官方来源 | 许可证/用途 |
|---|---|---|---|
| Android Gradle Plugin | 9.4.1 | Google Maven `com.android.tools.build:gradle` | Apache-2.0；构建工具 |
| Gradle wrapper/distribution | wrapper 9.8.0 | github.com/gradle/gradle、services.gradle.org | Apache-2.0；构建工具，分发包SHA固定 |
| Android SDK平台 | 37.0 | Android SDK Manager / Google | Android SDK协议；平台API，非打包第三方SDK |
| JDK | 本机Homebrew OpenJDK21 | OpenJDK/Homebrew | GPLv2+Classpath Exception；主机构建 |
| JUnit | 4.13.2 | Maven Central `junit:junit`，github.com/junit-team/junit4 | EPL-1.0；仅主机测试 |
| Hamcrest Core | 1.3（JUnit传递依赖） | Maven Central `org.hamcrest:hamcrest-core` | BSD-3-Clause；仅主机测试 |

AGP传递构建依赖由Google/Maven Central解析。当前APK只含本项目第一方代码及标准Android工具生成/desugar代码，没有第三方应用运行时SDK。Java HmacSHA256、SecureRandom、MessageDigest使用标准密码API，不自行实现密码算法。

未来引入官方Shizuku API、ADB/BoringSSL时必须先记录确切版本、官方来源、许可证文本、NOTICE及二进制构建来源。此列表不声称尚未引入的依赖已完成许可证审核。

项目第一方代码采用根目录Apache-2.0 LICENSE。Gradle wrapper保留官方文件内版权与许可证声明。若发布包含第三方运行时代码，应添加所需NOTICE，不以参考应用的致谢代替准确归属。

新增语言时添加`values-语言标签/strings.xml`、`locales_config.xml`条目和应用语言选项，并运行`python3 scripts/check_locales.py`；之后再做真机文本扩展与辅助功能验收。
