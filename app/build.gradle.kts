import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// ---------------------------------------------------------------
// 出包阶段：版本号机制
// 唯一手动维护点是 defaultVersionName，发版时改这里。
// CI 可用 -PversionNameOverride=0.2.0 临时覆盖，但**不会回写文件**，
// 会打印 warning 提醒发版后同步。
// versionCode 由版本名推导（major*10000 + minor*100 + patch），
// 不再恒为 1 —— 否则系统判不出新旧、装不上覆盖升级。
// ---------------------------------------------------------------
val defaultVersionName = "0.2.49"

fun versionCodeFrom(name: String): Int {
    val parts = name.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    return (parts.getOrElse(0) { 0 } * 10000
            + parts.getOrElse(1) { 0 } * 100
            + parts.getOrElse(2) { 0 }).coerceAtLeast(1)
}

val versionNameValue =
    (project.findProperty("versionNameOverride") as String?)?.takeIf { it.isNotBlank() }
        ?: defaultVersionName
val versionCodeValue =
    (project.findProperty("versionCodeOverride") as String?)?.toIntOrNull()
        ?: versionCodeFrom(versionNameValue)

if (versionNameValue != defaultVersionName) {
    println("[版本] ⚠️ CI 覆盖为 $versionNameValue（versionCode=$versionCodeValue），" +
            "文件里仍是 \"$defaultVersionName\"，发版后请同步修改 defaultVersionName")
} else {
    println("[版本] $versionNameValue（versionCode=$versionCodeValue）")
}

// R8 默认关闭：只有 -PminifyRelease=true 才开启。
// build.yml 不带这个参数，日常 push 的编译行为完全不变；
// release.yml 的 minify 输入默认 'on'，出包即混淆。
val minifyRelease: Boolean =
    (project.findProperty("minifyRelease") as String?)?.toBoolean() ?: false

// 正式签名：4 个环境变量全齐才启用，缺任一回退仓库内 commonDebug。
// 环境变量由 .github/scripts/prepare-signing.sh 注入。
val releaseSigningKeys = listOf(
    "OFFLINELEDGER_KEYSTORE_FILE",
    "OFFLINELEDGER_STORE_PASSWORD",
    "OFFLINELEDGER_KEY_ALIAS",
    "OFFLINELEDGER_KEY_PASSWORD"
)
val useReleaseSigning = releaseSigningKeys.all { !System.getenv(it).isNullOrBlank() }

android {
    namespace = "com.zwz.offlineledger"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zwz.offlineledger"
        minSdk = 26
        // Google Play 自 2026-08-31 起要求新应用以 Android 16 (API 36) 为目标。
        // 年度复核约定见 openspec/changes/scaffold-android-project/design.md。
        targetSdk = 36
        versionCode = versionCodeValue
        versionName = versionNameValue
    }

    signingConfigs {
        // 不能 create("debug")：AGP 已内置同名 SigningConfig，会报 already exists。
        create("commonDebug") {
            storeFile = file("debug.keystore")
            storePassword = "offlineledger2026"
            keyAlias = "offlineledgerdebug"
            keyPassword = "offlineledger2026"
        }
        // 名字用 official 而不是 release，避免与 AGP 内置 SigningConfig 混淆。
        if (useReleaseSigning) {
            create("official") {
                storeFile = file(System.getenv("OFFLINELEDGER_KEYSTORE_FILE")!!)
                storePassword = System.getenv("OFFLINELEDGER_STORE_PASSWORD")!!
                keyAlias = System.getenv("OFFLINELEDGER_KEY_ALIAS")!!
                keyPassword = System.getenv("OFFLINELEDGER_KEY_PASSWORD")!!
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("commonDebug")
            isMinifyEnabled = false
        }
        release {
            signingConfig = if (useReleaseSigning) {
                signingConfigs.getByName("official")
            } else {
                signingConfigs.getByName("commonDebug")
            }
            // 出包阶段：-PminifyRelease=true 才开 R8 与资源压缩。
            // keep 规则见 app/proguard-rules.pro。
            isMinifyEnabled = minifyRelease
            isShrinkResources = minifyRelease
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        // Kotlin 2.2+ 起 kotlinOptions 已废弃，必须用 compilerOptions DSL。
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}


dependencies {
    // ---- Compose ----
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")

    // ---- Room（阶段 1 起使用，阶段 0 验证工具链可解析）----
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ---- Hilt（阶段 0 用最小闭环验证 KSP 代码生成）----
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-android-compiler:2.57.1")

    // ---- 备份序列化（DTO 与 Room Entity 分离）----
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // ---- 第 50 批：本地库改为明文 Room（去 SQLCipher）----
    // 2026-11-22 使用者显式授权：自用 App 装在个人可信手机，威胁模型不含
    // 设备丢失 / 取证 / 物理接触；实测加密开库链导致冷启动 1~2s，去掉后基本无感。
    // 不再引入 sqlcipher-android，Room 走平台默认 SQLiteOpenHelper。
    // 备份 / 导出仍走 SecureBackupCodec 口令加密，MUST NOT 倒退。
    // 注意：不引入 Coil/Glide 之类会传递引入网络栈的库。

    // ---- 阶段 2：导航 / 分页 / ViewModel / 图标 ----
    // Compose BOM 2026.06 起不再通过 material3 传递图标 artifact，
    // 用图标就必须显式声明。取 frozen core set 即可，
    // 不拖入已被官方冻结、体积达数 MB 的 material-icons-extended。
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    // paging-runtime 是 RecyclerView 适配器，Compose 不需要。
    implementation("androidx.paging:paging-common:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")

    // ---- 阶段 5：应用锁（官方最新稳定版 1.1.0；1.4.0 仍属 alpha 线）----
    // 传递引入 androidx.fragment，MainActivity 改继承 FragmentActivity。
    // 刻意不引入任何含网络行为的依赖。
    implementation("androidx.biometric:biometric:1.1.0")

    // ---- 第 19 批：列表拖拽重排（纯 UI 库，无运行时网络行为） ----
    // sh.calvin.reorderable:reorderable:3.1.0，Apache-2.0，Maven Central。
    // 传递依赖仅 kotlin-stdlib-common 与 compose runtime/animation/foundation，
    // 不含网络客户端；Gradle 依赖只在构建期解析，不产生 App 运行时联网。
    implementation("sh.calvin.reorderable:reorderable:3.1.0")

    // ---- 单元测试 ----
    testImplementation("junit:junit:4.13.2")
}
