# Android 调研与待核验项

调研时间：2026-10-05。框架源阅读仅限官方 AOSP，不从其他 IMS 项目获取 Binder stub、事务编号或实现。AOSP 分支用于设计研究，不等于 Pixel 固件一致。

## 已确认的官方定义

工具更新：官方[Gradle版本服务](https://services.gradle.org/versions/current)确认9.8.0为current/final/released，因此最终固定9.8.0而非最初本机缓存的9.6.1。构建日志和最终校验和以VALIDATION为准。

- [AGP 9.4兼容表](https://developer.android.com/build/releases/agp-9-4-0-release-notes)：支持 API 37；Gradle最低9.6.0、Build Tools36.0.0、JDK最低17。[Google Maven元数据](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml) 已列出稳定9.4.1。JDK21已本机执行。
- [Android16 CarrierConfigManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/telephony/java/android/telephony/CarrierConfigManager.java)：NR availability 1=NSA、2=SA。NR RSRP阈值四项，范围-140至-44。NR advanced bandwidth是展示判据，不能据此认定5G-A硬件功能。IMS User-Agent是`ims.ims_user_agent_string`，不能误用MMS的`userAgent`。
- [SIM ISO公共API](https://developer.android.com/reference/android/telephony/CarrierConfigManager#KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING)：API34起公开，影响`getSimCountryIso()`和`SubscriptionInfo.getCountryIso()`。三位数字并非合法ISO国家码；Android厂商路径是否接受与传播是需要实际验证的兼容实验，非官方TikTok解决方案。Android13默认不开放写入，除非逐固件另有证据。
- [Android16 ProvisioningManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/telephony/java/android/telephony/ims/ProvisioningManager.java)：VoIMS opt-in为隐藏key68；不是普通SDK常量。不能仅依据该数字推断所有固件可写。
- [Android16 ImsManager](https://android.googlesource.com/platform/frameworks/opt/net/ims/+/refs/heads/android16-release/src/java/com/android/ims/ImsManager.java)：opt-in从订阅属性读取，并能进入平台VoLTE可用性的提前返回分支。高级通话用户设置仍独立存在。这支持订阅持久化路径的设计，但不证明网络会允许IMS注册。
- [Android16 SubscriptionManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/telephony/java/android/telephony/SubscriptionManager.java)：隐藏订阅属性接口有权限要求；setter会捕获部分RemoteException，返回不等于实际写入确认。integer getter可吞掉解析/缺值状态并返回默认，因此原值备份应检查原始字符串或独立完整字段，不用effective boolean伪装原值。
- [Android16 TelephonyManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/telephony/java/android/telephony/TelephonyManager.java)：allowed-network-types包含USER、POWER、CARRIER等原因；POWER为隐藏系统常量。普通READ_PHONE_STATE不能保证可读所有掩码。当前集中适配器仅在字段与方法签名吻合时读POWER，否则未知；不以猜测编号调用。
- [IMS MmTel公共API](https://developer.android.com/reference/android/telephony/ims/ImsMmTelManager)：注册、能力与传输回调需要精确/特权电话权限或运营商权限。普通安装可以合理地显示未知。后端将提供独立观测路径。
- [Android应用语言](https://developer.android.com/guide/topics/resources/app-languages)：API33起LocaleManager，空LocaleList跟随系统。当前已使用`localeConfig`与`setApplicationLocales`。

## 持久化 VoLTE 与普通覆盖的差异

候选操作只包含VoIMS opt-in与高级通话用户字段；VoNR是独立选择，不默认作为VoLTE副作用写入。先备份真实订阅字段（包括-1、未设置），写入后核验原始值、effective setting、IMS注册、实际语音路径。关闭功能应恢复原值；恢复unset不可简单映射为false。

## TikTok 兼容实验

只允许所选SIM明确确认后三位数字目标。目标由用户选择并保持于历史/恢复日志，不每次生成新值。当前核心检查输入与确认并保存目标；实际写入尚未实现。其他读取SIM region的应用（地区选择、语言、内容服务等）可能改变行为；并不修改MCC/MNC或实际网络归属，不保证互联网访问、登录或TikTok长期可用。

## 无线 ADB 与后端

[官方无线ADB架构](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/dev/adb_wifi.md)明确区分配对端口与连接端口，以及TLS通信与传统tcpip5555。实际配对需要配对码/QR共享秘密和官方配对协议，不能把成功connect当成成功pair。调试开关、Wi-Fi网络和端口可能随重启变化。

后续仅从官方AOSP/BoringSSL获取协议实现依赖，固定revision和许可证；不自制密码算法，不使用TensorIMS bundled adb/shizuku-adb二进制。当前未引入协议库、未配对、未宣称支持实际配对。

私有服务计划只允许本设备确认的loopback或已验证本机调试地址，拒绝任意外部主机；身份由应用签名/UID及会话认证绑定。配对码只驻留内存，不写日志/偏好；持久主机私钥在Keystore支持能力验证后保护；广播发现须绑定本设备，不能信任仅由mDNS名字识别的服务。需要在Android13至17实测连接限制和Keystore对所选TLS库的兼容性。

[官方Shizuku API](https://github.com/RikkaApps/Shizuku-API)允许受控UserService。其API库可作为有许可证的标准依赖；当前还未加入。不能将全局shell委托并发安全视为理所当然。root路径只在用户主动选择并批准时启动固定私有服务入口，绝不提供任意命令RPC。

## 尚需设备证据

Pixel固件的隐藏Binder方法参数/返回值、实际可委托权限、raw订阅属性、ICCID读取、CarrierConfig override/reset可观测性、NetworkStack endpoint优先级、SIM ISO消费路径、VoNR与跨卡注册技术。设备未连接不等于这些功能被证明不支持；目前是验证缺口。
