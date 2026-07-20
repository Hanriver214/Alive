# JavaMail：反射加载 Provider/Session，必须保留全部类
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.mail.** { *; }
-keep class org.apache.harmony.** { *; }
-dontwarn javax.mail.**
-dontwarn javax.activation.**
-dontwarn com.sun.mail.**
-dontwarn org.apache.harmony.**

# Room：DAO 实现由 KSP 生成，被运行时反射调用
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}

# WorkManager：Worker 子类通过反射实例化
-keep class * extends androidx.work.Worker { <init>(...); }
-keep class * extends androidx.work.CoroutineWorker { <init>(...); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# DataStore Preferences：schema 通过反射读取
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences {
    <methods>;
}

# Compose：保留 Composable 函数签名（用于工具链/运行时）
-dontwarn androidx.compose.**

# 项目自有的 Application / BroadcastReceiver / Service 由 manifest 注册，AGP 自动 keep
# 此处无需额外规则
