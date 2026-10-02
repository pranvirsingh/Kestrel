-dontobfuscate
-keep class com.pranvir.kestrel.MainActivity { *; }
-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**
-dontwarn java.lang.invoke.**
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}
