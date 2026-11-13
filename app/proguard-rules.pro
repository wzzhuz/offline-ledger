# =============================================================
# 出包阶段：R8 混淆规则（默认开启，见 release.yml 的 minify 输入）
#
# 本项目用到 Hilt(KSP + 反射) / Room(生成 _Impl) /
# kotlinx.serialization(@Serializable 的 $serializer 与 Companion) /
# SQLCipher(System.loadLibrary + JNI) / BiometricPrompt / Compose。
# 多数 AndroidX 库自带 META-INF/proguard consumer rules，AGP 会自动合并；
# 下面是**防御性补全**，重点是序列化与 JNI 两个边界——
# 这两处出问题的典型表现是「装得上、一启动就崩」，CI 编译完全看不出来。
# =============================================================

# ---- 基础属性 ----
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ---- kotlinx.serialization ----
# @Serializable 类会生成 Foo$$serializer 与 Companion.serializer()，
# R8 full mode 下不保留就找不到序列化器，备份导出/导入直接崩。
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.zwz.offlineledger.**$$serializer { *; }
-keepclassmembers class com.zwz.offlineledger.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.zwz.offlineledger.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- 备份 DTO：字段名是备份格式的一部分，绝不能被改名 ----
# Entity 与 DTO 分离本来就是为了「Entity 加字段不破坏备份格式」，
# 这里再补一层：DTO 的字段名与成员保持不变。
-keepclassmembers class com.zwz.offlineledger.backup.dto.** { *; }
-keep class com.zwz.offlineledger.backup.dto.** { *; }

# ---- Room ----
# Room 自带 consumer rules，这里补 Entity / TypeConverter / 生成实现边界。
-keep class androidx.room.** { *; }
-keep class com.zwz.offlineledger.data.local.entity.** { *; }
-keep class com.zwz.offlineledger.data.local.AppDatabase { *; }
-keep class com.zwz.offlineledger.data.local.AppDatabase_Impl { *; }
-keep class com.zwz.offlineledger.data.local.dao.**_Impl { *; }

# ---- 枚举：Room TypeConverter 用 valueOf(name) ----
-keepclassmembers enum com.zwz.offlineledger.data.model.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Hilt / Dagger ----
# Hilt 自带 consumer rules，补一层生成组件与 EntryPoint 边界。
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class com.zwz.offlineledger.**_HiltComponents { *; }
-keep class com.zwz.offlineledger.**_GeneratedInjector { *; }
-keep class com.zwz.offlineledger.**_Factory { *; }

# ---- SQLCipher / JNI ----
# native 方法依赖「类名 + 方法名」与 .so 里的符号对应，
# 混淆类名会直接 UnsatisfiedLinkError（一打开数据库就崩）。
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# ---- BiometricPrompt / FragmentActivity ----
-keep class androidx.biometric.** { *; }
-keep class androidx.fragment.app.FragmentActivity { *; }

# ---- 反射按名字查找的其他边界 ----
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
