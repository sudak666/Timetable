# Більшість бібліотек (kotlinx.serialization, Ktor, Supabase, Room, Hilt) постачають власні consumer-rules.
# Сюди — лише те, чого їм бракує. Кожне правило з коментарем «навіщо».

# Ktor тягне необов'язкові JVM-класи, яких немає на Android.
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn java.lang.management.**

# Стек-трейси в звітах про падіння читабельні після деобфускації (mapping.txt зберігається як артефакт CI).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
