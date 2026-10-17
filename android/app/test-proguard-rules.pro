# R8 rules for the instrumented-test APK, used when testing the minified build.
# androidx.test refers to Error Prone annotations that only exist at compile time.
-dontwarn com.google.errorprone.annotations.**
