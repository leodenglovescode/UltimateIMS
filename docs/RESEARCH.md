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
- [IMS MmTel公共API](https://developer.android.com/reference/android/telephony/ims/ImsMmTelManager)：注册、能力与传输回调需要精确/特权电话权限或运营商权限。普通安装可以合理地显示未知。后端已接入独立观测路径，待设备验收。
- [Android应用语言](https://developer.android.com/guide/topics/resources/app-languages)：API33起LocaleManager，空LocaleList跟随系统。当前已使用`localeConfig`与`setApplicationLocales`。

## 持久化 VoLTE 与普通覆盖的差异

候选操作只包含VoIMS opt-in与高级通话用户字段；VoNR是独立选择，不默认作为VoLTE副作用写入。先备份真实订阅字段（包括-1、未设置），写入后核验原始值、effective setting、IMS注册、实际语音路径。关闭功能应恢复原值；恢复unset不可简单映射为false。

## TikTok 兼容实验

只允许所选SIM明确确认后三位数字目标。目标由用户选择并保持于历史/恢复日志，不每次生成新值。当前白名单写入、确认、固定目标历史与恢复已接入，实际地区传播尚未验收。其他读取SIM region的应用（地区选择、语言、内容服务等）可能改变行为；并不修改MCC/MNC或实际网络归属，不保证互联网访问、登录或TikTok长期可用。

## 无线 ADB 与后端

[官方无线ADB架构](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/dev/adb_wifi.md)明确区分配对端口与连接端口，以及TLS通信与传统tcpip5555。实际配对需要配对码/QR共享秘密和官方配对协议，不能把成功connect当成成功pair。调试开关、Wi-Fi网络和端口可能随重启变化。

仅从官方AOSP/BoringSSL获取标准密码依赖，固定revision和许可证；不自制密码算法，不使用TensorIMS bundled adb/shizuku-adb二进制。当前引入固定官方密码依赖并连接协议路径，实际Android配对尚未验收。

私有服务只允许固定127.0.0.1本机地址，拒绝任意外部主机；身份由应用签名/UID及会话认证绑定。配对码只驻留内存，不写日志/偏好；持久主机私钥在Keystore支持能力验证后保护；广播发现须绑定本设备，不能信任仅由mDNS名字识别的服务。需要在Android13至17实测连接限制和Keystore对所选TLS库的兼容性。

[官方Shizuku API](https://github.com/RikkaApps/Shizuku-API)允许受控UserService。其API库可作为有许可证的标准依赖；当前已加入13.1.5。不能将全局shell委托并发安全视为理所当然。root路径只在用户主动选择并批准时启动固定私有服务入口，绝不提供任意命令RPC。

## 尚需设备证据

Pixel固件的隐藏Binder方法参数/返回值、实际可委托权限、raw订阅属性、ICCID读取、CarrierConfig override/reset可观测性、NetworkStack endpoint优先级、SIM ISO消费路径、VoNR与跨卡注册技术。设备未连接不等于这些功能被证明不支持；目前是验证缺口。

## 0.2.0 实施核验补充（覆盖前述“尚未实现”的进度描述）

实际已引入官方 Shizuku、Conscrypt、BoringSSL，并实现设备路径与真实协议，不再是只读原型。Android 配对仍未运行；主机 JNI 互通不能代替它。

- 官方 [Android13 ISub](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android13-release/telephony/java/com/android/internal/telephony/ISub.aidl) setter 为 int，Android16 为 void；兼容集中检查这两种形状，并独立核验raw字符串。无可靠 raw null 写回方式时提前拒绝。
- 官方 [Android13 ActivityManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android13-release/core/java/android/app/ActivityManager.java) 与16均定义 NO_RESTART/隐藏API instrumentation flags。实际固件仍逐字段取值；在绑定独立 broker 后启动。Android 16 的 NO_RESTART 实际使用 ApplicationInfo.processName，不能假设 targetProcesses 会改变其附着进程。权限委托要求有效UiAutomation instrumentation，全局委托占用时不抢占或清除别人的授权。
- [Android16 SubscriptionManagerService](https://android.googlesource.com/platform/frameworks/opt/telephony/+/android16-release/src/java/com/android/internal/telephony/subscription/SubscriptionManagerService.java) 限定 direct-access column/读写权限；代码不开放任意字段。普通IMS用户setter还会更新MMTel capability；不能先写相同raw值再指望setter触发变化。
- 官方 [CarrierConfigLoader](https://android.googlesource.com/platform/packages/services/Telephony/+/master/src/com/android/phone/CarrierConfigLoader.java) 通过handler应用覆盖。实现允许有限等待 effective回读，但不借此宣称覆盖层删除。`5g_icon_configuration_string` 场景/5G/5G_Plus/None语法来自官方定义，5GA字形不是此键保证。
- [Android16 Shell manifest](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/packages/Shell/AndroidManifest.xml) 声明WRITE_APN_SETTINGS；加入固定白名单委托。固件provider/权限可能仍拒绝，不将读回失败称成功。
- APN URI/首选策略依赖AOSP TelephonyProvider，使用明确subId路径及参数化selection；保存原首选行、只新增自有tag行。不执行全库reset、删其他APN或改变SIM operator。
- [T-Mobile官方APN](https://www.t-mobile.com/support/devices/not-sold-by-t-mobile/byod-t-mobile-data-and-apn-settings) 审阅 fast.t-mobile.com、MMSC、310260、default/supl/mms、IPv4/IPv6；不复用其删除其他APN/重启建议。漫游IPv4-only可能适用，当前固定profile明确显示IPv4/IPv6而不承诺漫游通用。
- [中国移动官方终端设置](https://www1.10086.cn/roaming/zizhu/zdsz/) 和 [官方漫游指引](https://www.10086.cn/roaming/cmcc_myzn.pdf) 指向CMNET，其他字段默认。第一方profile为default/supl、IP默认协议、不加认证/MMS/代理。此映射为实现选择，仍须目标SIM实际验收；不覆盖IMS专用APN。
- 第一方 ADB 配对：version/type/BE长度帧，TLS exporter `adb-label` 64字节拼接共享码，SPAKE2身份包含结尾NUL；HKDF-SHA256、AES128-GCM由官方库完成。ADB连接只接受STLS，不退回传统AUTH/tcpip。自签名TLS没有公有CA验证，仅允许loopback、共享码与最后Binder UID/nonce认证；不能把TLS握手视为框架后端已通过认证。

TikTok路径现已连接CarrierConfig写入，但Android13仍不开放三位地区键；API34+也仅是实验，需要确认传播到getSimCountryIso/SubscriptionInfo及目标app，不能认定合法ISO或通用网络解决方案。

APN插入额外核验：阅读官方 [Android16 TelephonyProvider](https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/refs/heads/android16-release/src/com/android/providers/telephony/TelephonyProvider.java) 后确认name不在唯一约束内，普通重复插入可能合并已有行。因此新行采用由持久操作tag确定的独立正数`apn_set_id`（该字段在AOSP唯一约束中），先拒绝已占用分组，写后同时验证分组与字段。不是更改modem profile_id，也不会用重复名称冒充独立行。实际固件分组选择/冲突行为和移动数据仍需设备验收。

Android17 [本地网络权限文档](https://developer.android.com/privacy-and-security/local-network-permission) 明确SDK37的权限门槛。配对/连接按钮在API37+需要时提示并请求ACCESS_LOCAL_NETWORK，授予后需再点按钮，不自动配对；loopback实际固件行为尚未验证。没有mDNS扫描、没有外部ADB主机连接。

HTTP诊断采用base cleartext=false和五个preset域的精确domain配置，不开放子域。ProbePolicy同时检查应用层白名单，其余自选探测必须HTTPS。HTTPS使用系统CA，公网IP查询始终HTTPS。用户自定义Settings.Global HTTP门户值仍可写，实际探测由系统NetworkStack执行而非此app的HTTP策略。

私有启动按官方AOSP外部provider API进行固定authority调用，逐签名检查getContentProviderExternal/holder.provider/IContentProvider.call/removeContentProviderExternalAsUser；明确传递应用Android userId和shell/root AttributionSource，不伪造系统包的ContentResolver身份。UID、当前nonce、app会话secret和客户端Binder生命周期都是不同的验证边界。客户端/后端死亡清理等待正在运行操作的finally，仍须真机验证死亡时序与全局委托释放。
