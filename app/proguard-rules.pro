# Hapture release rules. R8 runs with proguard-android-optimize.txt plus these.
#
# What already covers the app, so nothing is listed here for it:
#  - Room finds HaptureDatabase_Impl and the DAO _Impl classes by name; room-runtime ships consumer
#    rules that keep them (and every RoomDatabase subclass's constructor).
#  - Enums are stored and saved as their name and read back with valueOf(); the default file keeps
#    values()/valueOf() on every enum, and name() returns the declared name even when obfuscated.
#  - HaptureApp, MainActivity and the FileProvider are kept through the manifest.
#  - JSON goes through org.json by hand (no reflection-based serializer).

# Readable stack traces from Play Console crash reports (upload the mapping file with each release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
