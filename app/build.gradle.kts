import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.zwz.offlineledger"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zwz.offlineledger"
        minSdk = 26
        // Google Play 自 2026-08-31 起要求新应用以 Android 16 (API 36) 为目标。
        // 年度复核约定见 openspec/changes/scaffold-android-project/design.md。
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // 不能 create("debug")：AGP 已内置同名 SigningConfig，会报 already exists。
        // 换一个名字并让 debug / release 都引用它，保证 CI 每次编译签名一致。
        create("commonDebug") {
            storeFile = file("debug.keystore")
            storePassword = "offlineledger2026"
            keyAlias = "offlineledgerdebug"
            keyPassword = "offlineledger2026"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("commonDebug")
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("commonDebug")
            // 阶段 0 刻意不混淆：先让 lintVital 跑通，R8 取舍留到出包阶段。
            isMinifyEnabled = false
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

    // ---- SQLCipher（阶段 1 接入加密 OpenHelper）----
    // 官方坐标带 @aar；不引入 Coil/Glide 之类会传递引入网络栈的库。
    implementation("net.zetetic:sqlcipher-android:4.5.6@aar")

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

    // ---- 单元测试 ----
    testImplementation("junit:junit:4.13.2")
}
