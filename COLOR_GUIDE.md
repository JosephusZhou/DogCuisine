# DogCuisine 颜色规范

## 1. 单一颜色源
- 颜色定义唯一来源：`app/src/main/res/values/colors.xml`
- 主题映射（白天/夜间）：`app/src/main/res/values/themes.xml`、`app/src/main/res/values-night/themes.xml`
- Compose 统一入口：`app/src/main/java/com/dogcuisine/ui/DogCuisineTheme.kt`

## 2. 颜色 Token 与用途

| Token | HEX | 用在什么地方 | 典型页面/组件 |
|---|---|---|---|
| `dog_primary` | `#FEF1A5` | 主品牌底色（顶部栏、Splash 背景） | 各 Compose 页 `TopAppBar`、Splash |
| `dog_on_primary` | `#2C2518` | 主品牌底色上的文字/图标 | 顶栏标题、返回/保存/收藏图标 |
| `dog_primary_container` | `#FFF6C8` | 主色容器层（Material3 容器语义） | 主题保留给容器类组件 |
| `dog_on_primary_container` | `#2C2518` | 主色容器上的文字/图标 | 主题保留给容器类组件 |
| `dog_secondary` | `#4A3F2B` | 辅助操作色（按钮描边、图标色） | 编辑/搜索/添加/排序等图标，操作按钮文字 |
| `dog_on_secondary` | `#FFFFFF` | 辅助色底上的文字 | 分类选中态文字等 |
| `dog_secondary_container` | `#F2EAD6` | 次级容器背景 | 菜谱卡片背景、步骤/食材块背景 |
| `dog_on_secondary_container` | `#2C2518` | 次级容器上的内容色 | 图片删除按钮图标等 |
| `dog_surface` | `#FFFCF3` | 基础面板背景 | 卡片、页面内容容器 |
| `dog_surface_variant` | `#F7F2E6` | 次级面板背景 | 分类左侧区、输入代理框背景等 |
| `dog_on_surface` | `#2B2822` | 主正文色 | 常规正文、标题文本 |
| `dog_on_surface_variant` | `#5A5448` | 次正文/说明文字 | 时间、说明、占位说明 |
| `dog_outline` | `#D8CFBA` | 边框/分割线 | OutlinedTextField 边框、卡片边框 |
| `dog_gradient_top` | `#FFF8E8` | 页面渐变背景顶部色 | 主页面、添加页、详情页等背景渐变 |
| `dog_gradient_bottom` | `#FFFDF7` | 页面渐变背景底部色 | 页面背景渐变、BottomSheet 背景 |
| `dog_text_primary` | `#111827` | 强调正文（显式指定） | 标题、搜索输入文字、步骤正文 |
| `dog_text_placeholder` | `#9CA3AF` | 占位文字色 | 搜索占位、空内容提示 |
| `dog_text_muted` | `#6B7280` | 弱提示文字色 | “添加封面”提示文字 |
| `dog_scrim` | `#CC000000` | 半透明遮罩层 | 图片预览全屏遮罩、StepAdapter 预览背景 |
| `dog_crop_surface` | `#111111` | 裁剪页深色背景 | 裁剪封面页面背景 |
| `dog_crop_mask` | `#88000000` | 裁剪蒙层遮罩 | 裁剪框外遮罩层 |
| `celebrate_accent` | `#E38B2C` | 成就/庆祝强调色 | `dialog_level_up.xml` 文本强调 |

## 3. 代码中如何使用

### Compose 页面
- 外层统一包裹：`DogCuisineTheme { ... }`
- 页面背景统一使用：`dogCuisineBackgroundBrush()`
- 非 Material 语义色使用：`DogCuisineColors.*`
  - 例如：`DogCuisineColors.TextPrimary`、`DogCuisineColors.Scrim`

### XML 页面
- 优先用主题属性（推荐）：
  - `?attr/colorPrimary`
  - `?attr/colorOnPrimary`
  - `?attr/colorSurface`
  - `?attr/colorOutline`
- 必须用固定 token 时再写 `@color/dog_*`

### View/Java/Kotlin（非 Compose）
- 使用 `ContextCompat.getColor(context, R.color.dog_xxx)` 获取颜色
- 禁止再写硬编码如 `0xFFxxxxxx`

## 4. 维护规则
- 新增颜色时，只允许加在 `app/src/main/res/values/colors.xml`
- 新增 Material 语义色时，必须同步更新：
  - `app/src/main/res/values/themes.xml`
  - `app/src/main/res/values-night/themes.xml`
  - `app/src/main/java/com/dogcuisine/ui/DogCuisineTheme.kt`
- 提交前检查：
  - `rg -n "0x[0-9A-Fa-f]{8}|#[0-9A-Fa-f]{6,8}" app/src/main/java/com/dogcuisine/ui app/src/main/java/com/dogcuisine/ui/*.java`
