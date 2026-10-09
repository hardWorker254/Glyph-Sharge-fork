package com.bleelblep.glyphsharge.data

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Typed read/write shorthands for the settings store.
 *
 * Spelling out `prefs.getBoolean(KEY, false)` and
 * `prefs.edit { putBoolean(KEY, value) }` for every one of ~40 keys is where
 * the two halves of a setting drift apart: nothing ties the type used to save
 * to the type used to read, so a `putString` against a `getBoolean` compiles
 * and then throws at runtime on that phone only. The reified type parameter
 * makes the compiler check that pairing instead.
 *
 * Reads never hand back a null unless the caller asked for one: for the one
 * type that can hold one — a cleared string — the caller's default is
 * substituted here, and a caller whose *default* is null (it wants to know
 * "was this ever written?") gets that null back to elvis over itself. The
 * return type does not have to lie about it and no caller needs an elvis of
 * its own for the ordinary case.
 *
 * Writes go through `androidx.core.content.edit`, which is `apply()` — the
 * write is asynchronous and the caller is not told whether it reached the
 * disk. Nothing here catches exceptions, so a `ClassCastException` from a
 * value written by a different version of the app still surfaces.
 */
internal inline fun <reified T> SharedPreferences.getSetting(key: String, default: T): T {
    @Suppress("UNCHECKED_CAST")
    return when (T::class) {
        Boolean::class -> getBoolean(key, default as Boolean) as T
        Int::class -> getInt(key, default as Int) as T
        Long::class -> getLong(key, default as Long) as T
        Float::class -> getFloat(key, default as Float) as T
        // `null` is a legitimate stored value (a cleared string), so the read
        // can come back empty-handed — the caller's default is the answer.
        // The default itself may be null: a caller asking `getSetting<String?>(key, null)`
        // wants "never written" to stay null so its own elvis can answer. The
        // old `(default as String)` cast was typed for the non-null case only,
        // and threw "null cannot be cast to non-null type kotlin.String" on
        // exactly that path — before the caller's `?: DEFAULT` ever ran, so
        // the fallback it had written was unreachable.
        String::class -> (getString(key, default as String?) ?: default) as T
        else -> error("Unsupported settings type ${T::class} for key '$key'")
    }
}

internal inline fun <reified T> SharedPreferences.putSetting(key: String, value: T) {
    edit {
        when (T::class) {
            Boolean::class -> putBoolean(key, value as Boolean)
            Int::class -> putInt(key, value as Int)
            Long::class -> putLong(key, value as Long)
            Float::class -> putFloat(key, value as Float)
            String::class -> putString(key, value as String?)
            else -> error("Unsupported settings type ${T::class} for key '$key'")
        }
    }
}
