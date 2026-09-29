# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.forwebview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Settings storage ---
#
# There is deliberately no `-keep` rule for `SettingsRepository` or for
# `CustomAnimationRepository` any more (the facade rule added on 2025-06-16 has
# been removed). Neither class needs one:
#
#  * Nothing reaches them reflectively. There is no `Class.forName`, no
#    `javaClass` field access, no `kotlin.reflect`, and neither class is named
#    in a manifest, a navigation graph or any other XML.
#  * Nothing serialises them either. `SettingsRepository` is a stateless
#    forwarder to the settings slices, and `CustomAnimationRepository` maps
#    `ScriptAnimation` to `org.json` by hand (`put("id", …)` / `optString("id")`)
#    rather than through a reflective serialiser, so its JSON index survives
#    renaming either way.
#  * Both are constructed by Hilt, whose factories are generated at compile
#    time. Renaming the class renames the factory reference with it.
#
# What actually persists state here is the `SharedPreferences` behind the
# slices, and every key in them is a `const val` string literal, which
# obfuscation never rewrites.

# The one thing obfuscation *would* break: settings persisted as enum names.
# `ThemeSettings.getThemeStyle()` and `FontSettings.getFontVariant()` store
# `AppThemeStyle.CLASSIC.name` and read it back through `valueOf(String)`. The
# default Android rules keep `values()` and `valueOf(String)` but not the
# constants themselves, so an obfuscated build would stop recognising every
# stored value and silently fall back to the default theme.
-keepclassmembers enum com.bleelblep.glyphsharge.ui.theme.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** name;
}

# Preserve Hilt generated classes / injection metadata
-keep class dagger.hilt.** { *; }
-keep class androidx.hilt.** { *; }
-keepclassmembers class ** {
    @androidx.annotation.Keep *;
}
