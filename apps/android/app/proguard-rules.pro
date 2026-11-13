# R8 rules for the native app. Libraries ship their own consumer rules; add keeps here only for reflection the app itself does.

# NewPipeExtractor (README): Rhino is used to evaluate YouTube's signature scripts.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
# Rhino's optional JDK-only integrations (absent on Android); the extractor runs it in interpreted mode.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# Error reports (PostHog) show stack traces as they are: keep class names and line numbers readable
# instead of uploading R8 mapping files. Shrinking and optimizing still apply.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
