# 依赖与许可

第一方代码使用 Apache-2.0。必须保留根 LICENSE、NOTICE，以及发行包内的第三方法律文本。

| 组件 | 固定版本/来源 | 许可 |
|---|---|---|
| Gradle / AGP | 9.8.0 / 9.4.1，官方分发/Google Maven | Apache-2.0 |
| AndroidX / Material Components | 完整解析版本见 runtime-dependencies.json | Apache-2.0 及随包通知 |
| Shizuku API/provider | 13.1.5，官方 Maven 分发 | MIT |
| HiddenApiBypass | 6.1，官方 Maven 分发 | Apache-2.0 |
| Conscrypt Android | 2.7.0，官方 Maven 分发 | Apache-2.0 及随包通知 |
| BoringSSL | AOSP ecc1358826150d6a1851c517c325b0e6c0e1b8be | OpenSSL / SSLeay / ISC 等文件许可 |
| JUnit / Hamcrest | 4.13.2 / 1.3，仅主机测试 | EPL-1.0 / BSD-3-Clause |

BoringSSL 源码归档由 scripts/build_pairing.py 从官方 AOSP 下载并核对 SHA-256，再用 NDK 构建。Gradle wrapper 同样固定官方分发校验值。源码缓存和生成的二进制不进入版本控制。

完整法律文本位于 third_party/licenses，并复制到应用 assets/licenses；原始版权、许可和 NOTICE 不应翻译或删改。公开身份与必要法律归属不属于设备诊断数据。
