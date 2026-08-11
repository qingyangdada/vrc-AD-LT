这是一个用codex 开发的 安卓vrcx
VRC好友状态 (VRChat Friend Tracker)
一款自用的安卓应用：登录你自己的 VRChat 账号，查看好友的在线状态、所在房间、 简介变化、信任等级变化，并在本地保存变化时间线。

⚠️ 使用 VRChat 非官方 API（api.vrchat.cloud），违反 VRChat 服务条款， 存在账号被限制/封禁的风险。请只在自己账号上自用，不要分发。 信任等级由系统标签推断，仅供参考。

功能
好友列表：在线/离线状态、所在房间（世界名 + 实例类型）、信任等级徽章、简介预览
好友详情：完整资料、简介与链接、当前房间（世界缩略图/实例类型/区域/人数）、 变化记录时间线（简介/信任等级/上下线/换房间）
本地历史：所有变化保存在手机本地 Room 数据库，可追溯时间点
实时动态：好友列表顶部实时滚动最近变化，点“实时动态/查看全部”进入全部记录页（带头像）
自动同步：App 打开时按设定间隔轮询（默认 5 秒，可调 5 秒～30 分钟），后台每 15 分钟同步一次
登录：账号密码 + 2FA（验证器/邮箱/恢复代码），令牌经 Android Keystore AES-GCM 加密存储
不保存密码，不做系统推送通知
技术栈
Kotlin + Jetpack Compose (Material 3)
Retrofit + OkHttp + kotlinx.serialization
Room（好友缓存、历史记录、世界缓存）
WorkManager（后台周期同步）
Coil（头像/缩略图加载）
项目结构
app/src/main/java/com/vrc/friendtracker/
├── data/api/          # Retrofit 接口、DTO、OkHttp 拦截器（cookie 捕获）
├── data/security/     # Keystore AES-GCM 令牌加密存储
├── data/db/           # Room 实体 / DAO / 数据库
├── data/repo/         # 登录仓库、设置仓库
├── sync/              # 同步核心（拉取+差异检测+历史记录）、后台 Worker
├── ui/                # Compose 界面（登录/好友列表/详情/设置）
└── util/              # 信任等级解析、实例解析、时间格式化
构建
环境要求：JDK 17+、Android SDK（API 35）。

方式一：Android Studio（推荐）
安装 Android Studio（自带 JDK 与 SDK）
File -> Open 选择本项目目录（settings.gradle.kts 所在处）
等待 Gradle 同步完成
连接手机（开启 USB 调试），点击 Run ▶ 或 Build -> Build APK(s)
方式二：命令行
# 设置环境（按实际路径）
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$env:ANDROID_HOME = "C:\Android\Sdk"

# 调试包（可 sideload 安装）
.\gradlew.bat :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 正式包（需自备签名配置）
.\gradlew.bat :app:assembleRelease
安装到手机：

adb install -r app\build\outputs\apk\debug\app-debug.apk
首次构建需下载 Gradle 发行版与依赖，耗时较长。 若项目路径含中文（如用户名是中文），Gradle 已通过 android.overridePathCheck=true 关闭路径检查（见 gradle.properties）。

使用说明
打开 App，输入你的 VRChat 账号和密码登录（与 vrchat.com 相同）
若账号开启两步验证，按提示选择验证方式（验证器 App / 邮箱验证码 / 恢复代码，支持多方式切换）并输入对应验证码
邮箱验证：登录后到注册邮箱收验证码，输入后点“验证并登录”（App 会自动完成后续重新登录）
若提示“登录成功但会话立即被接口拒绝”，请到“设置 → 诊断信息”查看详细日志（含 HTTP 状态码与响应体）， 一般是因为会话尚未完成验证，重新登录并完成邮箱/验证器验证即可
登录后自动同步好友列表；每条好友显示在线状态、房间与信任等级
点击好友进入详情：简介、房间、变化记录
设置里可调整自动刷新频率（5 秒～30 分钟）或退出登录
关于信任等级
VRChat 官方接口不直接返回信任等级，本应用从好友全量资料的系统标签推断：

标签	等级（英文显示）
system_trust_veteran	Trusted User
system_trust_trusted	Known User
system_trust_known	User
system_trust_basic	New User
（无）	Visitor
好友列表接口的标签恒为空，因此 App 会轮流转取每个好友的全量资料 （每分钟约 10 人）来刷新信任等级。刚登录的几分钟内，等级可能显示为“访客”， 属正常现象，全部轮询完会纠正。

数据与隐私
密码仅用于换取登录令牌，不落盘；令牌经 Keystore 加密后存储
好友资料与变化记录仅保存在本机数据库
退出登录会清除令牌与本地数据
接口限流时自动跳过本轮，不会暴力重试
免责声明
本项目与 VRChat 官方无关，使用非官方 API
使用第三方 API 违反 VRChat 服务条款，请自行承担账号风险
信任等级为推断值，仅供参考
