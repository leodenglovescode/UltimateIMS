# Android 17 兼容性复核

## 来源与结论边界

- [Carrier IMS 3.9.1 发布说明](https://github.com/ryfineZ/carrier-ims-for-pixel/releases/tag/3.9.1.r107.4312b5a9) 报告 Android 17 的 stopDelegateShellPermissionIdentity 签名变化，且指出配置可能已写入才在释放阶段失败。这里只阅读发布说明，没有移植实现。
- [TensorIMS 发布说明](https://github.com/Pixel-Tailor-CN/TensorIMS/releases) 同样报告无参/带 UID 释放接口兼容。说明不能代替实际设备签名。
- [官方 Android 17 行为变化](https://developer.android.com/about/versions/17/behavior-changes-all) 确认 API37；公开行为清单不能保证隐藏 API 稳定。
- 官方 AOSP android17-release 分支的 [IActivityManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/core/java/android/app/IActivityManager.aidl) 本次获取内容仍是无参释放；不能把参考应用对较新 Pixel 固件的报告当作该分支定义。QPR1/QPR2 同名公开分支请求返回 404。实际固件需运行时验证。

## 审计表

| 能力 | 官方 Android 17 定义/本次结果 | 实现与待验证范围 |
|---|---|---|
| 权限委托 | start(int,String[])、get() 在所查 AOSP 分支保留；stop() 与参考发布报告的 stop(int) 并存风险 | 初始化预解析三个接口；优先带 UID，只在方法缺失时选无参。调用拒绝/异常绝不尝试另一签名。释放后必须回读为空，否则关闭后端并保留不确定状态 |
| CarrierConfig | [CarrierConfigManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/telephony/java/android/telephony/CarrierConfigManager.java) 仍定义 overrideConfig(int,PersistableBundle,boolean) | 没有证据表明该方法整体被删除；实际厂商权限和每键回读仍需设备验证 |
| 覆盖权限/持久性 | [CarrierConfigLoader](https://android.googlesource.com/platform/packages/services/Telephony/+/refs/heads/android17-release/src/com/android/phone/CarrierConfigLoader.java) 拒绝直接 shell UID；user 构建 persistent=true 要求系统应用 | 原架构通过应用 UID broker 加短时白名单委托，并使用 persistent=false。无需把应用变成 shell 身份。不是绕过 modem/carrier provisioning |
| NR NSA/SA、阈值、图标 | 29 个本项目 carrier key 均在官方定义中存在；IMS user-agent 使用 ims. 前缀拼接 | key 存在不代表特定固件/基带接受或实际使用。图标、NR 注册、VoNR 分开呈现 |
| VoNR | [ITelephony](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/telephony/java/com/android/internal/telephony/ITelephony.aidl) 仍定义 setVoNrEnabled(int,boolean)、isCapable(int,int,int)、isAvailable(int,int,int) | 原签名保留；语音偏好/能力不能证明实际 VoNR 呼叫 |
| 用户/电源/运营商策略 | [TelephonyManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/telephony/java/android/telephony/TelephonyManager.java) 保留 getAllowedNetworkTypesForReason(int) | 改成三项独立读取，任何一项失败不遮蔽其他项；未知不写成零。Battery Saver 排除 NR 时只解释，不更改电源策略 |
| 实际注册 | [ServiceState](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/telephony/java/android/telephony/ServiceState.java) 保留 getNrState() 与 PS NetworkRegistrationInfo；TelephonyManager 保留无位置 getServiceState(int) | 继续区分 NSA 辅载波、SA PS RAT 和显示覆盖，设备未测 |
| 持久 VoLTE 订阅字段 | [ISub](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/telephony/java/com/android/internal/telephony/ISub.aidl) 保留原始属性读取与 void setSubscriptionProperty(int,String,String) | 与 CarrierConfig 重放独立；此表只核对接口，未验证 Android17 重启持久性或 provisioning 实际消费行为。原值/未知/恢复保护不变 |

## 0.5.4-dev 身份路径更正

新实现按官方 [IPhoneSubInfo](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/telephony/java/com/android/internal/telephony/IPhoneSubInfo.aidl) 的 getIccSerialNumberForSubscriber(int,String,String)，由用户选择的后端实际读取，经活动卡/重复标识检查后认证回传。未扩大为任意身份信息执行器，未更改系统权限或 AppOps。源码字节哈希在 privileged-identity-validation.json 中。

自动重放遇到身份不可验证时暂停，并保留所有备份；SIM 访问受限原因在主页面按需打开。最终实际 ICCID/双卡/NR/写入/恢复结果仍由用户继续验收。
