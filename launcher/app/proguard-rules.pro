# Règles R8 du launcher. Compose, Coil, coroutines et AndroidX embarquent leurs propres règles.

# Garde les numéros de ligne lisibles dans les rapports d'erreur
-keepattributes SourceFile,LineNumberTable

# Les champs BuildConfig sont lus directement
-keep class com.custom.tvlauncher.BuildConfig { *; }

# Avertissements sans conséquence
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
