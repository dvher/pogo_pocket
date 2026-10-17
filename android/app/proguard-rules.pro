# The Go core (gomobile) calls into these classes from native code by name.
-keep class go.** { *; }
-keep class dev.pogo.pocket.core.** { *; }

# Glance creates widget click actions (actionRunCallback) by reflection through
# their no-arg constructor. Glance's own rule keeps the classes but not their
# constructors, which R8 drops since AGP 9 (strict keep rules), so taps on the
# widget's folded corner and its tasks did nothing in release builds.
-keep public class * extends androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
