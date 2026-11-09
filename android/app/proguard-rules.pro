# Більшість бібліотек (kotlinx.serialization, Ktor, Supabase, Room, Hilt) постачають власні consumer-rules.
# Сюди — лише те, чого їм бракує. Кожне правило з коментарем «навіщо».

# Ktor тягне необов'язкові JVM-класи, яких немає на Android.
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn java.lang.management.**

# Звіти про збої (Crashlytics, Android vitals) мають бути читабельні без mapping.txt.
# Обфускація тут нічого не захищає — код відкритий на GitHub; R8 і далі стискає й прибирає зайве.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
