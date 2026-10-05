# 功能清单与验收矩阵

状态：**只读**=代码存在但未真机验收；**核心**=仅平台无关逻辑；**待实现**=设备路径未接通；**需确认**=补充范围；**排除**=明确冲突。API 列说明项目目标和已知边界，不是所有厂商保证。

| 要求/参考行为 | 来源 | 作用域与 API | 状态 | 证据/验收缺口 |
|---|---|---|---|---|
| 设备、版本、安全补丁、后端状态 | C/T | 设备，33+ | 部分只读 | 当前设备版本读取；安全补丁/后端实测待补 |
| 活动实体 SIM、eSIM、默认数据变化 | C/T/L | 每卡，33+ | 只读 | SubscriptionManager；双卡回调未真机验证 |
| 非活动订阅、Wi-Fi-only 注册 | L | 每卡；权限相关 | 待实现 | 不将无活动卡等同无 IMS |
| VoLTE availability 与显式关闭 | C/T/L | 每卡 CarrierConfig，33+ | 只读+核心 | 键映射和类型测试；实际写入待接入 |
| VoWiFi availability | C/T/L | 每卡，33+ | 只读+核心 | 不默认改变 T-Mobile 用户偏好 |
| WFC Wi-Fi-only / Wi-Fi优先 / 蜂窝优先 | T/L | 每卡用户设置+能力，33+ | 部分只读+核心 | default mode≠用户偏好；用户设置写入待实现 |
| WFC roaming enable / roaming preference | L | 每卡，33+ | 部分只读+核心 | 用户与默认 CarrierConfig 分开；漫游实测待做 |
| VoNR configuration 与实际 voice over NR | C/T/L | 每卡；VoNR key 公共 34+ | 只读+核心 | IMS voice capability≠实际 VoNR；通话制式待验证 |
| VT / 视频能力 | C/T/L | 每卡，33+ | 只读+核心 | 回调有权限时读取；视频通话待验收 |
| Cross-SIM calling | C/T/L | 每卡，33+ | 只读+核心 | 需用户偏好和跨卡注册技术读取；待双卡实测 |
| UT / 补充服务 | C/T/L | 每卡，33+ | 只读+核心 | 不自动拨号或更改呼叫转移；实际服务待验收 |
| 运营商显示名称覆盖 | C/T/L | 每卡，33+ | 只读+核心 | name string 与 override flag 独立 |
| IMS User-Agent | C/T/L | 每卡，33+，vendor消费 | 只读+核心 | AOSP `ims.ims_user_agent_string`，非 MMS userAgent |
| IMS 注册、能力、传输 | C/T/L | 每卡，权限相关 | 只读 | 公共回调；permission denied保持未知 |
| IMS 重启/手动注册请求 | C/T/L | 每卡特权 | 待实现 | 中断通话前明确授权；注册成功需回调证据 |
| NR NSA / SA availability | C/T/L | 每卡，33+ | 只读+核心 | AOSP 数组 1=NSA，2=SA；配置不等于连接 |
| NR 信号阈值 | C/T/L | 每卡，33+ | 部分只读+核心 | RSRP 四项递增约束；RSRQ/SINR待补 |
| 5G+/5GA 图标配置 | C/T/L | 每卡，33+ | 部分只读+核心 | NR advanced bandwidth已映射；完整图标映射待补 |
| LTE/LTE+ 能力、增强图标隐藏 | T/L | 每卡，33+ | 部分只读+核心 | hide flag读回；LTE+阈值待补，不能称提升硬件能力 |
| LTE显示4G、其他data-icon控制 | C/T/L | 每卡，33+ | 部分只读+核心 | show4G已映射；其余项目级行为待调查 |
| Preferred network / USER mask | L | 每卡特权读取，33+ | 只读 | 未授权保持未知；不自动写入 |
| POWER mask、省电阻止NR解释 | L | 每卡特权读取，33+ | 只读+核心 | 故障案例测试；不会静默关闭省电模式 |
| 实际 NR 注册/SA/NSA与图标观测 | L | 每卡权限相关 | 待实现 | data RAT仅部分观测，不足以验证NSA或通话制式 |
| 持久化 VoLTE，VoIMS订阅 opt-in | T/L | 原始订阅数据库，33+需固件核验 | 核心，设备待实现 | 不依赖重新应用 CarrierConfig；断后端重启通话待验收 |
| 持久化原值、unset、恢复、中断恢复 | L | 每卡已验证身份 | 核心 | CoreSafetyTest；实际 raw 读取与写入待接入 |
| TikTok 三位数字 SIM ISO、明确确认 | C/L | 每卡；public key 34+ | 核心，设备待实现 | 固定输入目标+日志保存；不随机换值、不改MCC/MNC |
| TikTok/其他地区兼容状态 | C/L | 每卡，33+ | 部分只读 | 当前读 SIM region；其他 app 影响需确认 |
| Captive Portal HTTP/HTTPS读写与原值恢复 | C/T/L | 设备 Settings.Global | 只读+输入约束 | 不将存储回读等同NetworkStack实际探测切换 |
| 门户 endpoint presets | T/L | 设备 | 待实现 | Google/V2EX/小米/vivo/华为候选；需实际端点有效性验证 |
| 连接验证、门户与计费状态 | L | 设备，33+ | 只读 | ConnectivityManager实时读取 |
| 用户主动DNS/HTTP/TLS连接诊断 | L | 用户选择网络/每卡 | 待实现 | 超时界限，不扫网，不自动上传 |
| “为什么5G不可用” | L | 每卡+设备 | 部分只读+核心 | USER/POWER/CARRIER掩码判定已有；实际注册/硬件诊断待补 |
| APN查看、系统设置入口 | C | 每卡 | 需确认/待实现 | 文档新增；不自动更改APN |
| 推荐APN写入 | C | 每卡 privileged provider | 需确认/待实现 | 目标运营商、字段集、覆盖/新增行为需确认；不猜T-Mobile APN |
| 网络出口检测 | C | 用户触发外部端点 | 需确认/待实现 | 可能暴露IP给检测站；须明确端点/用途，默认关闭 |
| QS VoLTE/IMS状态图块 | C | 设备入口+所选卡 | 待实现 | 非自启动权限通道；写图块仍必须认证/备份 |
| 单卡/显式所有SIM、逐卡结果 | C/T/L | 已验证活动卡快照 | 待实现 | 不使用全局原值替代逐卡备份 |
| 实时配置、编辑草稿、选择性写入 | C/T/L | 每卡+设备 | 只读部分，草稿待实现 | 历史不得填作当前值；改动集合独立于未编辑项 |
| 启用/禁用/还原/保持不变 | T/L | 每字段 | 核心 | Bool false、UNSET与UNKNOWN独立；Android接入待做 |
| 推荐/大陆5G/大陆LTE/全开启预设 | T | 草稿每卡 | 待实现 | 只形成可审阅差异；不自动应用或改变不相关服务 |
| 历史、备份导出/导入、选择性恢复 | C/T/L | 每卡/设备 | 核心部分 | 原值日志有；版本化导入和脱敏报告待做 |
| CarrierConfig reset及删除验证 | C/T/L | 每卡特权 | 待实现，有平台可观测性限制 | effective bundle无法证明覆盖项已删除，不能报成功 |
| 后端ready时自动恢复opt-in | T/L | 所选后端+已绑定卡 | 待实现 | 默认关闭；权限丢失/未知结果暂停；与订阅持久化分离 |
| 官方Shizuku | C/T/L | 显式选择+授权 | 选择记录，传输待实现 | token核心测试不能证明UserService认证 |
| 私有无线ADB与已有root路径 | T/L | 显式启动 | 待实现 | 实际TLS/SPAKE2配对必须真机测试；不移植第三方IMS二进制 |
| 权限委托冲突、全局串行、超时未知 | L | 跨进程+特权服务 | 核心部分 | 本地协调器串行，委托冲突与真实RPC超时待实现 |
| 本地应用日志、过滤配置dump、脱敏导出 | C/T/L | 本地 | 待实现 | 当前英文错误日志不含标识符；无导出按钮冒充功能 |
| 仓库/反馈/手动更新 | C | 用户触发 | 待实现 | 不自动下载或远程加载代码；不自动提交诊断 |
| Auto/English/简体中文与可访问性 | C/T/L | app，33+ | 已实现，待设备验收 | LocaleManager；文本扩展、TalkBack、200%字号待验收 |
| 广告、商务/支付功能 | C | 第三方远端 | 排除 | 与用户无广告/遥测/上传要求冲突 |

补充控制的“需确认”不会阻塞无需这些字段的只读诊断或安全核心实现；确认以前不实施 APN 写入或外部 IP 检测。其他未知附加区域控制保留待调查，不凭文档标题编造键。
