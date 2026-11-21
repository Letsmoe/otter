# R8 rules for the native app. Libraries ship their own consumer rules; add keeps here only for reflection the app itself does.

# Error reports (PostHog) show stack traces as they are: keep class names and line numbers readable
# instead of uploading R8 mapping files. Shrinking and optimizing still apply.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
