# 架构与实施计划

## 独立边界

## 当前分层

- `app/MainActivity` / `FeaturePanels`：原创六页原生 Android UI、所选 SIM、草稿、差异确认、SAF 文档操作、LocaleManager、可滚动布局与系统字体缩放。技术状态标出读取时间，不从历史填入当前设置。
- `core`：白名单及校验、UNKNOWN/UNSET/显式值、HMAC 身份、会话认证、原值日志、选择性事务、预设与 APN 范围规则。
- `WorkspaceController`：应用唯一的串行操作队列，AndroidKeyStore 安装私有 HMAC、noBackup 日志、历史、逐卡执行、恢复、APN 独立日志、ready 时用户选择的 CarrierConfig 重应用。
- `backend/PlatformMethods` / `PlatformOperations`：集中逐参数与返回类型检查。Android 13 的订阅 setter 返回 int、Android 16 返回 void，实际版本只接受已确认形状，不用猜测事务码。原始 getter 与标准 IMS 用户 setter 分开；用户 setter 保留系统 capability 更新通知。
- `backend/PrivilegeService`：官方 UserService 与私有 app_process 入口共用第一方窄协议；应用 UID + 256 bit 内存 secret 校验。私有 provider 要求 DUMP、shell/root UID、当前启动 nonce。不接收任意命令/服务名/文件路径。
- 私有 `:broker`：应用 UID 的非导出绑定服务，认证后处理操作。用户所选后端在主进程启动带 UiAutomationConnection 的 NO_RESTART instrumentation；主进程通过认证会话通知后端，后端再认证独立 broker 的就绪状态；每次操作检查全局委托占用，短时委托白名单权限，操作后释放。不能与其他客户占用委托时并发写入。
- `adb` 与第一方 JNI：仅 loopback，独立 TLS/ADB 帧与配对状态机；Conscrypt 提供 TLS/exporter，官方 BoringSSL 提供 SPAKE2/HKDF/AES。Keystore 保存不可导出 RSA 私钥，六位码只在内存且及时清零。

## 事务与恢复

显式后端 → 认证 → 真实 ICCID 验证 → 读取真实原值 → 同步落盘 PREPARED → APPLYING → 写入/有限等待回读 → 完整集合再核验 → VERIFIED。原值只追加，终态也不删除。raw -1、null、false、空数组与不可读取不同。

所有 SIM 是用户选中的活动订阅快照，每卡独立身份和结果；首张出现失败/未知后剩余列为未执行。设备门户使用独立的 -2 设备作用域和安装绑定身份。

超时不取消已发生的设备写入。尚运行的 RPC 禁止第二个请求；超时标记 uncertain，恢复需先收敛。持久日志 PREPARED/APPLYING/RESTORING/RECOVERY_REQUIRED 在重启后仍阻止新配置。选择性恢复不能清除其他字段的不确定状态，完整原值始终保留。恢复过的字段从 desired target 移除，防止 opt-in 重应用复活已恢复字段。

SIM 替换、订阅号改变均重新验证 ICCID；卡槽/MCC/MNC/运营商名称不能代替身份。Keystore key 丢失且任一原值/APN备份存在时拒绝生成替代 key。无验证身份的 SIM 不自动写入。

CarrierConfig 只能读取合并后的 effective bundle，无法备份其他应用的覆盖层结构。原值恢复核验 effective 值；reset 则保存日志并始终报告删除未验证，自动重应用暂停。真实 raw null 订阅值缺少已验证 unset 恢复方法，开启前拒绝写入，而不是默认 false。支持的 -1 值原样恢复。

APN 使用另外的 AtomicFile：保存原首选行的非秘密字段及唯一操作 tag，写前 RECOVERY_REQUIRED；仅新增/选择固定审阅 profile，重启后按 tag 找回本应用行。恢复前检查原行未变，选择读回原 ID，并验证本应用行已删除；不删除原行或其他 APN。没有可读取首选 ID 时拒绝开始。

## 隐私与协议限制

共享诊断用 allowlist，不包含 ICCID/IMSI/号码、IP、位置/小区、配对码、URL 或响应正文。用户显式配置草稿导出包含其输入配置文本，不能当作自动脱敏的诊断。原始应用日志只本地查看，不直接加入分享报告。

ADB 使用自签名证书与共享码认证，不是公有 CA HTTPS。配对密钥绑定 TLS exporter；连接限本机，启动命令严格固定，私有 Binder 附加另验 UID/nonce。不能把收到 CNXN/关闭 shell 流当作服务就绪，必须完成认证后的框架读取。实际 Android 配对、loopback 限制与 Keystore/Conscrypt 协同尚无设备证据。

## 优先级进度

| 阶段 | 目标 | 实现状态 | 验收门槛 |
|---|---|---|---|
| P0 | 新项目、清单、API研究、安全核心 | 已实现 | 主机构建/测试/lint |
| P1 | 官方传输、broker、身份与权限冲突 | 已接入 | Pixel 原始属性与真实委托 |
| P2 | 草稿、每卡/全卡、历史、恢复 | 已接入 | 真机写后回读、杀进程/断权限恢复 |
| P3 | 持久 VoLTE、用户偏好、IMS维护 | 已接入 | 断后台、无Wi-Fi重启后真实通话 |
| P4 | 私有无线 ADB、可选已有root | 已接入，JNI主机通过 | Android实际配对/连接/切换 |
| P5 | 显示、门户、固定地区实验 | 已接入 | 实际NetworkStack/地区消费/展示 |
| P6 | 审阅APN、IP opt-in、图块、日志/导出 | 已接入 | APN权限与恢复、实际出口服务 |
| P7 | Android 13–17、双卡/语言/可访问 | 待真机验收 | DEVICE_ACCEPTANCE 全项证据 |

首个只读增量与本次设备操作增量都保留提交；后续首先执行只读真机验收，不擅自重启、重置或中断通话。矩阵保留所有范围及平台限制，不用按钮存在证明验收完成。

## 0.3.0-dev UI 边界

MainActivity 改为 AppCompatActivity；MaterialUi 统一 Material 3 控件、语义色、表单与间距。ThemePreferences 仅持久化外观；UltimateApplication 在 Activity 创建前应用 DayNight 与可选动态颜色。没有改变特权选择、原始备份格式、恢复引擎或通信白名单。功能页仍由原 WorkspaceController 处理，视觉变化不获取后端。详见 UI_DESIGN.md。

## 0.4.0-dev 页面与连接增量

MaterialUi 的分组容器改为无卡片平面列表，ConfigurationToggle 集中管理布尔/0-1 用户偏好与 NSA/SA 数组语义。两条主要页面为 IMS 与无线、配置；后者同时持有显式后端操作。原恢复日志与第一方通信架构不变。BackendManager 的授权回调绑定 epoch，认证使用相同串行 RPC 和未知超时约束；一次性认证快照传给 WorkspaceController，消除重复启动读取。注册与可用性证据仍由真实框架观测提供，不从历史或开关推断。

## 0.5.4-dev 特权身份读取

readSimIdentity 是固定窄接口：先核对调用 UID 和 32 字节会话密钥，仅在 invoke 已通过认证与准备检查、broker.execute 正在执行期间开放。普通操作只允许当前 subId；SNAPSHOT 可枚举当前活动卡。所有情况均使用真实活动列表核验，读取前后检查活动状态，重复读取并比对 ICCID。不同读源矛盾、空值、非活动卡或换卡均拒绝。回调使用独立读锁，不能获取 invoke 持有的监视器，以免跨进程等待死锁。

声明的服务名/接口/方法和参数类型为固定常量，不接受任意命令、服务、方法名或外部归属包。shell 使用自己的 com.android.shell；root 不伪装包名。原 HMAC 与私有原值文件格式不改变，不删除或迁移备份。回调完成后关闭读范围；STOP/unbind 清除回调 token。

此项在主机测试中验证，尚未在用户设备上验证新回调、权限或实际 ICCID 返回。

## 0.5.5-dev 完整记录读源与统一验证

特权身份读源从 modem serial 改为 ISub.getActiveSubscriptionInfo 返回的完整 ICCID。保留认证回调及读范围、活动列表、记录 subId 对应关系和重复读取校验。不能用短 modem 数字前缀生成持久身份。中央 VerifiedSimIdentity 同时服务于 AndroidKeyStore IdentityStore、主机 SimIdentity、原值恢复目标和写入前身份比较。既有十进制 ICCID 的 HMAC 输入字节不变，已有原值文件不迁移、不删除。

## 0.6.0-dev 显示预设与逐卡计划

RadioDisplayProfiles 仅定义两组明确字段、实时匹配、已存原值子集和未提交草稿撤销。NR_NSA_LTE_SIGNAL 与 NR_SIGNAL_METRICS 使用固定 AOSP 白名单键，原 journal 继续按键名保存，旧备份不改名。

确认页面为每卡构建不可变字段计划与当时的已验证 SIM 身份；applyPerSim 后台任务使用该身份进行核验，不能以任务开始时的新快照悄悄替换。失败保留每卡已执行/失败/未执行状态及原值日志。所有开关仍走既有有限协议，没有任意框架调用或 shell 执行器。
