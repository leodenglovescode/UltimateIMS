# 功能清单与验收矩阵

| 要求/参考行为 | 来源 | 作用域与 API | 状态 | 证据/验收缺口 |
|---|---|---|---|---|
| 设备、版本、安全补丁、后端状态 | C/T | 设备，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| 活动实体 SIM、eSIM、默认数据变化 | C/T/L | 每卡，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| 非活动订阅、Wi-Fi-only 注册 | L | 每卡；权限相关 | 已接入；待真机 | 已实现非活动订阅只读列表；不向非活动卡写入；IMS传输独立于data RAT，不因无蜂窝数据否定Wi-Fi IMS |
| VoLTE availability 与显式关闭 | C/T/L | 每卡 CarrierConfig，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| VoWiFi availability | C/T/L | 每卡，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| WFC Wi-Fi-only / Wi-Fi优先 / 蜂窝优先 | T/L | 每卡用户设置+能力，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| WFC roaming enable / roaming preference | L | 每卡，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| VoNR configuration 与实际 voice over NR | C/T/L | 每卡；VoNR key 公共 34+ | 部分观测；平台限制 | NR/用户设置、MMTel、IMS registrationTechnology/按NR的voice capability、nrState、data/voice RAT已接入；通话的厂商modem路径证据不可保证读取，不宣称VoNR通话成立 |
| VT / 视频能力 | C/T/L | 每卡，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| Cross-SIM calling | C/T/L | 每卡，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| UT / 补充服务 | C/T/L | 每卡，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| 运营商显示名称覆盖 | C/T/L | 每卡，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| IMS User-Agent | C/T/L | 每卡，33+，vendor消费 | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| IMS 注册、能力、传输 | C/T/L | 每卡，权限相关 | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| IMS 重启/手动注册请求 | C/T/L | 每卡特权 | 已接入；待真机 | 实际disable/enable框架调用，finally尝试enable，确认且活动通话拒绝；只报告请求，不宣称注册成功 |
| NR NSA / SA availability | C/T/L | 每卡，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| NR 信号阈值 | C/T/L | 每卡，33+ | 已接入；待真机 | RSRP/RSRQ/SINR四项递增与各自范围，CoreSafetyTest；真实信号与显示未测 |
| 5G+/5GA 图标配置 | C/T/L | 每卡，33+ | 已接入；待真机 | NR_ICON AOSP场景映射、NR advanced bandwidth；验证语法不等于图标显示；AOSP只有5G/5G_Plus，5GA字形由系统/厂商决定 |
| LTE/LTE+ 能力、增强图标隐藏 | T/L | 每卡，33+ | 已接入；待真机 | LTE能力、editable/hide用户入口、LTE+阈值、hide增强数据图标、4G/4GLTE标签分别可编辑；不提高modem硬件能力 |
| LTE显示4G、其他data-icon控制 | C/T/L | 每卡，33+ | 已接入；待真机 | LTE能力、editable/hide用户入口、LTE+阈值、hide增强数据图标、4G/4GLTE标签分别可编辑；不提高modem硬件能力 |
| Preferred network / USER mask | L | 每卡特权读取，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| POWER mask、省电阻止NR解释 | L | 每卡特权读取，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| 实际 NR 注册/SA/NSA与图标观测 | L | 每卡权限相关 | 部分观测；平台限制 | NR/用户设置、MMTel、IMS registrationTechnology/按NR的voice capability、nrState、data/voice RAT已接入；通话的厂商modem路径证据不可保证读取，不宣称VoNR通话成立 |
| 持久化 VoLTE，VoIMS订阅 opt-in | T/L | 原始订阅数据库，33+需固件核验 | 已接入；待真机 | raw VoIMS opt-in + 高级通话用户值，标准框架通知；不附带 VoNR；无后端/无Wi-Fi重启通话未测 |
| 持久化原值、unset、恢复、中断恢复 | L | 每卡已验证身份 | 已接入；主机通过 | RecoveryJournal / OperationCoordinator；真实 raw null 无已验证恢复路径时写前拒绝，-1 原样保留；49个核心测试 |
| TikTok 三位数字 SIM ISO、明确确认 | C/L | 每卡；public key 34+ | 已接入；待真机 | API34+固定三位目标、两次确认、历史/desired保存与原值恢复；不改MCC/MNC；平台地区传播及TikTok均未测 |
| TikTok/其他地区兼容状态 | C/L | 每卡，33+ | 已接入；待真机 | 读取真实SIM region；不按历史美化国家；其他app影响与实验限制明确说明 |
| Captive Portal HTTP/HTTPS读写与原值恢复 | C/T/L | 设备 Settings.Global | 已接入；待真机 | 独立设备作用域，Settings.Global读/写/删除/null恢复逐项核验；NetworkStack探测优先级未测 |
| 门户 endpoint presets | T/L | 设备 | 已接入；待真机 | Google/V2EX/Xiaomi/vivo/Huawei预设形成草稿，明确应用后读取设置核验；实际NetworkStack行为未测 |
| 连接验证、门户与计费状态 | L | 设备，33+ | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| 用户主动DNS/HTTP/TLS连接诊断（HTTP限preset域） | L | 用户选择网络/每卡 | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| “为什么5G不可用” | L | 每卡+设备 | 已接入；待真机 | USER/POWER/CARRIER掩码、后台状态、NR观测、注册与不足证据分开；省电不自动关闭；策略规则主机通过 |
| APN查看、系统设置入口 | C | 每卡 | 已接入；待真机 | 固定URI首选APN查询及所选SIM系统APN设置入口；不可读保持未知 |
| 网络出口检测 | C | 用户触发外部端点 | 已接入；待真机 | 明确输入HTTPS纯文本IP服务并确认；默认网络/VPN出口；仅本地显示，分享报告排除IP |
| QS VoLTE/IMS状态图块 | C | 设备入口+所选卡 | 已接入；待真机 | 实际TileService，显示当前已读取IMS状态，打开VoLTE恢复页；不会自动获取权限/切换；真机添加图块未测 |
| 单卡/显式所有SIM、逐卡结果 | C/T/L | 已验证活动卡快照 | 已接入；待真机 | 明确所有SIM勾选、逐卡身份日志与结果，失败后剩余未执行；双卡设备未测 |
| 实时配置、编辑草稿、选择性写入 | C/T/L | 每卡+设备 | 已接入；待真机 | snapshot/刷新与时间标记、草稿/历史严格区分；写后全量读取；后台或读取失败不推断当前值 |
| 启用/禁用/还原/保持不变 | T/L | 每字段 | 已接入；主机通过 | RecoveryJournal / OperationCoordinator；真实 raw null 无已验证恢复路径时写前拒绝，-1 原样保留；49个核心测试 |
| 推荐/大陆5G/大陆LTE/全开启预设 | T | 草稿每卡 | 已接入；待真机 | PlatformOperations / FeaturePanels；只有构建与主机规则证据，设备行为待验收 |
| 历史、备份导出/导入、选择性恢复 | C/T/L | 每卡/设备 | 已接入；待真机 | AtomicFile历史、private原值日志、版本化无身份配置草稿导入/导出、选择性恢复；导入不能覆盖原值，跨安装原值迁移不实现 |
| CarrierConfig reset及删除验证 | C/T/L | 每卡特权 | 请求已接入；平台限制 | 只确认调用请求，永不报覆盖删除成功；保存恢复状态并禁用自动重应用 |
| 后端ready时自动恢复opt-in | T/L | 所选后端+已绑定卡 | 已接入；待真机 | 默认关闭；仅已验证身份与非恢复状态的已确认CarrierConfig targets重应用；不自动启动后端，与VoIMS订阅持久化分离 |
| 官方Shizuku | C/T/L | 显式选择+授权 | 已接入；待真机 | 官方13.1.5 UserService + 第一方认证broker；显式授权启动；真实Binder/delegation尚未测 |
| 私有无线ADB与已有root路径 | T/L | 显式启动 | 已接入；待真机 | 独立TLS/STLS/ADB帧、SPAKE2 JNI、固定私有启动入口、已有root显式路径；JNI主机通过，Android配对未测 |
| 权限委托冲突、全局串行、超时未知 | L | 跨进程+特权服务 | 已接入；待真机 | 检查占用/委托/释放，UID+secret，单RPC队列；超时不取消、不盲目重试；真实权限冲突/死亡未测 |
| 本地应用日志、过滤配置dump、脱敏导出 | C/T/L | 本地 | 已接入；待真机 | 固定非特权UID/标签logcat查看；报告白名单与受控英文事件，原始日志/URL/身份/IP/位置/秘密不自动导出 |
| Auto/English/简体中文与可访问性 | C/T/L | app，33+ | 已接入；待真机 | LocaleManager、246个中英键/占位符对齐检查；Material 3平面开关列表、自动系统浅深色与壁纸配色，详见UI_DESIGN；真实语言切换、200%字号/TalkBack未测 |
| 广告、商务/支付功能 | C | 第三方远端 | 排除 | 与无广告/遥测/云上传要求冲突，未引入 |

## 附加范围与明确限制

- 参考资料中的运营商“国家码(MCC)”与 SIM country ISO 是不同字段。没有获得可安全持久化、可验证恢复的 AOSP 每卡 MCC/MNC 覆盖契约；本应用不实现 SIM 身份 MCC/MNC 伪造，也不把三位 ISO 实验写成 MCC 修改。这是额外兼容控制的明确平台/范围限制，不隐藏在已完成状态中。
- APN profile 的 MCC/MNC 来自已验证的真实 SIM operator，仅新增 APN 行匹配；不修改 SIM operator。范围外运营商仍支持其余 IMS 功能，APN 显示无匹配审阅 profile。
- 系统门槛、运营商 provisioning、基带/频段/套餐、IMS服务和当前注册路径决定通信能力。应用不能创造 modem 功能；未公开/不匹配签名、权限拒绝或回读失败明确报未知/不支持，保留日志。
- 原值完全恢复包括覆盖层结构、真实 null 订阅字段恢复和实际 VoNR 呼叫技术等存在可观测性/框架限制；没有用 placeholder 或成功提示替代。
- 设备为空时无需猜测Android配对/双卡/重启/语音通过；全部真机验收见 DEVICE_ACCEPTANCE。

## 0.4.0-dev 导航与后端补充

后端/高级配置统一在配置页；IMS 注册证据与 VoNR/Vo5G、NSA/SA、LTE 等直接开关在 IMS 与无线页。当前/草稿值明确区分，未知不等于关闭，原有确认/恢复约束保留。Shizuku 预检前不绑定 broker，认证超时保留未知结果，启动快照只交接一次。49 个核心测试通过；新增 6 项验证开关类型、默认/未知和 NSA/SA 保留规则。ADB 升级安装并回读版本成功，未继续任何功能/后端/主题真机测试。

## 0.4.1-dev Shizuku 准备修复

- 修复 NO_RESTART 实际附着主进程与独立 broker 静态状态不共享的问题；新增认证就绪与关闭会话，原有设备操作仍在独立 broker 中执行。
- 准备阶段验证请求后清除入站 Binder 身份；null 权限列表按占用处理，不夺取其他客户委托。
- 新增准备阶段诊断及错误消息脱敏测试，保留失败/未知结果状态。
- 主机测试只能验证握手状态与错误处理；Shizuku 真机启动、首读及双 SIM 行为仍待重新连接设备验证。

## 0.4.2-dev Android 17 兼容补充

## 0.5.3-dev 连接设备后的增量

## 0.5.4-dev 身份读取修复候选

## 0.5.5-dev 完整 ICCID 格式修复

只读实际探针确认：第一个 SIM 完整框架 ICCID 为 20 位十六进制表示，而 modem 接口给出 6 位短前缀；旧正则错误拒绝。第二个 SIM 的 19 位十进制表示正常。新完整记录读源在同一设备两个活动 SIM 上只读通过；host 111 项测试通过，含旧 HMAC 黄金值、完整 hex、截断拒绝及换卡不匹配。仅证明身份读算法，不将 app 内应用、恢复、5G/VoNR 记为通过。

## 0.6.0-dev 显示预设

| 能力 | 作用域/兼容 | 实现与证据 | 真机缺口 |
|---|---|---|---|
| 5G enhanced/5G+/5GA 显示预设 | 单卡/明确所有卡；33+ | 紧凑开关；图标场景与20MHz门槛，仅草稿和确认后写入 | 实际图标、NR Advanced 资格、5GA 字形未测 |
| 大陆风格四级 NR 信号格 | 单卡/所有卡；33+ | 四项 SSRSRP、测量掩码、NSA主信号；高级页可编辑；状态必须全部字段可读 | 实际 bar 行为及载波测量未测；不是统一运营商标定 |
| 关闭预设与多卡恢复 | 已有可读原值 | 未应用取消本组草稿；已应用读取各卡自己的原值；明确缺失/原值已匹配；不可伪造关闭 | 手机恢复过程未测 |

新增14项核心测试覆盖选择字段、原值恢复、未知、无关草稿保留和每卡不同原值；总125项主机通过。安装成功仅证明安装，不代表显示或网络效果。
