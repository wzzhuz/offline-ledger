// 顶层构建脚本：只声明插件版本，不实际应用。
// 版本组合的锁定依据与回退顺序见
// openspec/changes/scaffold-android-project/design.md 第 1 节。
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false

    // KSP 从 2.3.0 起不再与 Kotlin 版本绑定。
    // 旧写法 2.3.21-2.0.2 对应的 artifact 根本不存在，会报 Plugin was not found。
    id("com.google.devtools.ksp") version "2.3.11" apply false

    // Hilt 官方文档给出的版本；注解处理器走 ksp，不用已废弃的 kapt。
    id("com.google.dagger.hilt.android") version "2.57.1" apply false
}
