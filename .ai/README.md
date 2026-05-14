# DogCuisine AGENTS 指南

本文件定义 AI 代理与协作者在本仓库中的统一工作规范。

## 1. 项目定位

- 项目类型：原生 Android 应用。
- 项目目标：管理狗狗菜谱，支持分类、收藏、搜索、图文步骤、封面裁剪、备份恢复和 WebDAV 同步。
- 当前实现形态：单模块应用，数据层以 Java + Room 为主，界面层以 Kotlin Activity + Jetpack Compose 为主，局部仍保留 Android View / Dialog 交互。
- 技术栈：Kotlin、Java 17、Jetpack Compose Material 3、Room、Gson、Coil、WebDAV、自定义系统栏处理。
- 最小假设：当前仓库代码就是事实，不补历史背景，不脑补“理想架构”。

## 2. 核心原则

- 先读后改：先确认 Activity 入口、数据库读写、图片文件链路和同步链路，再动代码。
- 小步精改：优先做最小且完整的修改，避免把单点需求扩展成架构重写。
- 全链路一致：菜谱、分类、图片、备份、恢复、同步相关字段一旦变化，必须同步检查所有上下游。
- 尊重现状：本项目是混合式实现，不要为了“统一风格”强行把 Java、Compose、View、Dialog 全部重构。
- 不猜需求：用户未要求的新字段、新入口、新模式不主动添加。
- 明确风险：如果保留兼容逻辑、已知限制或未验证路径，要在最终说明中写清楚。

## 3. 代码事实来源

处理任务时按以下优先级建立事实：

1. 用户当前指令。
2. 当前仓库源码与资源文件。
3. 本文件约束。
4. 构建、运行、静态检查结果。

不要把其他项目、通用 Android 模板或默认 Material 行为当作本仓库事实来源。

## 4. 目录与关键入口

高价值入口文件：

- 应用入口：`app/src/main/java/com/dogcuisine/App.java`
- 主页面入口：`app/src/main/java/com/dogcuisine/ui/MainActivity.kt`
- 新增/编辑菜谱：`app/src/main/java/com/dogcuisine/ui/AddRecipeActivity.kt`
- 菜谱详情：`app/src/main/java/com/dogcuisine/ui/RecipeDetailActivity.kt`
- 搜索页面：`app/src/main/java/com/dogcuisine/ui/SearchRecipeActivity.kt`
- 分类管理：`app/src/main/java/com/dogcuisine/ui/CategoryManageActivity.kt`
- 备份恢复：`app/src/main/java/com/dogcuisine/ui/BackupRestoreActivity.kt`
- WebDAV 同步：`app/src/main/java/com/dogcuisine/ui/WebDavSyncActivity.kt`
- 主题与颜色：`app/src/main/java/com/dogcuisine/ui/DogCuisineTheme.kt`
- 系统栏处理：`app/src/main/java/com/dogcuisine/ui/ComposeSystemBarDelegate.kt`
- 数据库：`app/src/main/java/com/dogcuisine/data/AppDatabase.java`
- 菜谱实体：`app/src/main/java/com/dogcuisine/data/RecipeEntity.java`
- 菜谱 DAO：`app/src/main/java/com/dogcuisine/data/RecipeDao.java`
- 同步配置：`app/src/main/java/com/dogcuisine/sync/WebDavSyncConfig.java`
- 同步实现：`app/src/main/java/com/dogcuisine/sync/WebDavSyncManager.java`

主要功能目录：

- UI：`app/src/main/java/com/dogcuisine/ui/`
- 数据层：`app/src/main/java/com/dogcuisine/data/`
- 同步：`app/src/main/java/com/dogcuisine/sync/`
- 资源：`app/src/main/res/`

## 5. 架构现实与改动边界

- 本项目不是 ViewModel + Repository 的标准分层结构，很多页面直接在 Activity 中持有 Compose state、DAO 和 `ExecutorService`。
- `App` 持有全局 `AppDatabase`、单线程 `ioExecutor`，并负责首启默认数据、用户资料初始化、图片清理和自动 WebDAV 上传调度。
- 多数页面通过 `ActivityResultLauncher`、`setResult(RESULT_OK)` 和 `onResume()` 完成页面间刷新，而不是响应式状态共享。
- 修改时优先延续现有方式，除非用户明确要求，否则不要顺手引入 ViewModel、Flow、Repository、DI 或多模块化。

## 6. 功能边界规则

- 只实现用户明确要求的范围。
- 修改菜谱相关逻辑时，至少检查：主列表、详情、编辑、搜索、收藏、图片预览、图片清理。
- 修改分类逻辑时，至少检查：主页面左侧分类、下拉选择、分类管理页、删除限制、排序保存。
- 修改图片逻辑时，至少检查：封面裁剪、步骤图、食材图、本地存储路径、删除时清理、备份和恢复。
- 修改同步或备份逻辑时，至少检查：数据库文件、图片目录、配置持久化、恢复后的数据库重载。
- 不添加“未接通”的功能入口，例如只有按钮没有实际保存、只有 UI 没有数据库写入、只有本地修改没有同步/备份适配。

## 7. UI 与交互规范

- 保持现有 DogCuisine 视觉语言：暖色调、金色图标、渐变背景、圆角卡片、显式顶部栏。
- 当前大量页面通过 `dogCuisineBackgroundBrush()` 绘制全屏渐变背景；非必要不要改成纯 Material 默认背景。
- 顶部栏、卡片、输入框、按钮、空态文案要沿用现有颜色语义，优先复用 `DogCuisineTheme`、`DogCuisineColors` 和已有 Compose 组件模式。
- 多数页面显式调用 `ComposeSystemBarDelegate.install()`，并设置 `WindowCompat.setDecorFitsSystemWindows(..., false)`；修改页面布局时必须检查内容是否与状态栏重叠。
- 页面返回行为并不完全统一：有些页面禁用默认返回并自定义动画或未保存检查，修改交互时要保留原有返回栈和转场预期。
- 未经要求，不要把现有界面整体改成另一套设计语言，不要把金色资源图标替换成通用 Material Icon。

## 8. 状态管理与线程约束

- 当前主流模式是 `mutableStateOf` / `mutableStateListOf` + `ioExecutor.execute { ... }` + `runOnUiThread { ... }`。
- 不要在未评估线程影响时把数据库、文件、压缩、同步等耗时操作搬到主线程。
- 也不要在没有明确收益时混入新的协程域、复杂并发控制或响应式封装，避免和现有单线程执行模型冲突。
- 修改 Compose 代码时重点检查：
  - 状态是否仍然由 Activity 持有。
  - 列表 key、拖拽排序、图片预览等交互是否被破坏。
  - 页面关闭、返回、恢复后是否会重复加载或丢状态。
  - 需要 `setResult(RESULT_OK)` 的路径是否仍然能通知上游刷新。

## 9. 数据与持久化规则

- `Room` 数据库版本当前为 `7`，定义在 `AppDatabase.java`。
- 实体当前包括：`RecipeEntity`、`CategoryEntity`、`UserProfileEntity`。
- 菜谱核心字段包含：名称、创建/更新时间、正文、封面图路径、步骤 JSON、食材 JSON、分类 ID、收藏状态。
- `steps_json` 与 `ingredient_json` 通过 Gson 序列化，属于高影响字段；结构改动必须同步更新解析、保存、展示、搜索、备份恢复和图片清理逻辑。
- 任何 `Room Entity`、DAO、数据库版本或迁移改动，都必须成套检查：
  - 编译是否通过。
  - 首次安装是否正常。
  - 升级迁移是否合理。
  - 列表、详情、编辑、搜索是否仍能正确读取。
  - 备份压缩包与恢复流程是否仍可用。
  - WebDAV 上传下载恢复后是否能正确重载数据库。

## 10. 图片与文件系统规则

- 图片是本项目的核心数据资产，不只是 UI 附件。
- 封面图、食材图、步骤图都保存在 App 私有目录，并在数据库中存储本地绝对路径。
- 删除菜谱时需要同步删除封面与步骤图文件；应用启动时 `App.cleanupUnusedImages()` 还会扫描数据库外的孤儿图片。
- 备份恢复会打包数据库文件和 `files/images` 目录；任何图片路径或目录结构调整都必须同步适配打包和解压逻辑。
- 图片导入链路包含打开系统文档、复制、压缩、封面裁剪、预览，多步流程中的任一改动都可能导致残留文件、路径失效或恢复失败。

## 11. 搜索、收藏、分类特殊规则

- “收藏”在主页面被视为一个伪分类，`MainActivity` 中使用固定 ID `-1L` 表示收藏筛选；不要把它误当作真实数据库分类。
- 分类删除前会检查是否仍有关联菜谱；修改分类删除逻辑时必须保留这一保护，除非用户明确要求改变规则。
- 搜索当前基于 SQL `LIKE`，覆盖 `name`、`content`、`ingredient_json`；如果调整字段结构，必须确认搜索结果仍符合预期。
- 详情页和主列表都依赖 `is_favorite` 字段；修改收藏逻辑时要检查两个入口是否一致。

## 12. 备份与同步规则

- 本地备份由 `BackupRestoreActivity` 负责，核心对象是 `dogcuisine.db` 和 `files/images`。
- 恢复流程会关闭数据库、覆盖数据库文件与图片目录，然后调用 `App.reloadDatabase()` 重建实例。
- WebDAV 相关配置当前使用 `SharedPreferences` 保存，上传、下载、校验都由 `WebDavSyncActivity` 驱动。
- `App` 内部还存在自动 WebDAV 上传能力；只要动到保存、恢复、同步配置或数据库替换逻辑，都要主动评估自动上传行为是否受影响。
- 不要破坏“恢复成功后上游页面重新加载数据库”的既有机制。

## 13. 禁止事项

除非用户明确要求，否则不要：

- 擅自把整个项目重构成全 Compose、全 Kotlin、全协程或 MVVM。
- 大规模重命名稳定类名、字段名、资源名或数据库列名。
- 删除看似冗余但仍参与备份、同步、搜索、图片清理的字段或逻辑。
- 用临时硬编码替代现有字符串资源、颜色资源、图片资源或数据库真实数据。
- 只改 UI 不改持久化，或只改数据库不改展示链路，留下半完成状态。
- 忽略系统栏遮挡、返回行为变化、数据库迁移缺失、文件残留等高回归风险问题。

## 14. 实施流程

执行非琐碎任务时，建议遵循以下顺序：

1. 阅读相关 Activity、DAO、实体、同步或备份实现。
2. 明确本次修改会影响哪些页面、字段、文件目录和返回结果。
3. 以最小改动完成实现。
4. 自查 UI、线程、数据库、图片文件、备份恢复、同步链路是否一致。
5. 运行必要验证。
6. 在最终说明中写清修改点、验证结果和剩余风险。

## 15. 验证要求

完成有意义的改动后，默认至少执行或评估以下验证：

1. 构建应用：`./gradlew :app:assembleDebug`
2. 若涉及数据库：检查实体、DAO、版本、迁移、列表读取、详情读取、编辑保存。
3. 若涉及分类：检查新增、编辑、排序、删除限制、主页面刷新。
4. 若涉及图片：检查导入、压缩、裁剪、预览、删除、重启后读取。
5. 若涉及搜索或收藏：检查主列表、详情页和搜索页结果一致性。
6. 若涉及备份或同步：检查导出、导入、恢复后重载、WebDAV 校验/上传/下载路径。

如果因为环境限制无法完成验证，必须明确说明未验证项和潜在风险。

## 16. 最终交付要求

完成任务后的说明应尽量简洁，但必须包含：

- 改了什么。
- 为什么这样改。
- 运行了哪些验证，结果如何。
- 是否存在未解决风险、未覆盖验证或有意保留的差异。

若任务是代码审查型请求，优先输出问题清单，按严重程度排序，并附文件路径与定位信息。

## 17. 面向 AI 代理的执行要求

- 把自己当作该仓库的长期维护者，而不是一次性脚本执行者。
- 优先保护数据正确性、图片文件一致性、备份可恢复性和低回归风险。
- 发现工作区有其他未提交改动时，不回滚不是自己产生的修改。
- 与当前任务无关的问题可以记录，但不要顺手扩大改动面。
- 如果真实冲突或需求边界不清，只问一个最小必要问题；否则直接完成实现。

## 18. 简要 Do / Don't

Do：

- 先理解 Activity 入口和 DAO 调用链再修改。
- 优先做小而完整的改动。
- 涉及菜谱字段或图片时同步检查搜索、收藏、备份、同步和清理逻辑。
- 保持 DogCuisine 现有视觉语义、返回行为和数据流方式。
- 在最终说明中明确验证与剩余风险。

Don't：

- 不要把其他仓库或通用模板当作真相来源。
- 不要发明新需求、新字段或未接通入口。
- 不要留下半迁移、半删除、半同步完成的代码路径。
- 不要为了“更现代”无边界引入新架构。
- 不要忽略数据库、文件系统和恢复链路中的回归风险。
