# Extra R8 rules for the minified build type only (not release). The test APK
# leaves out libraries the app also depends on and uses the app's copies, so
# R8 must not remove the parts the test runner and ReleaseSmokeTest use but
# the app itself doesn't. These keep library code only, never the app's own
# classes, so the smoke test still sees the app as R8 shrinks it for release.
-keep class androidx.tracing.** { *; }
-keep class androidx.concurrent.futures.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
