package com.maar.shield

import android.content.Context
import java.security.MessageDigest

/** 5 one-time unlock passwords, stored only as salted SHA-256 hashes. Entered in order. */
object Unlock {
    const val SET_ID = 1               // bump when shipping a new set of passwords
    private const val SALT = "maar-shield-v1"
    private val HASHES = listOf(
        "f3ba1c3a9c6e449dff30488dd2cf4b9a4101217f8f9d93e76f60778e98845ff1",
        "7a8cbddee3bfb7af7cd6996f1c4cf5e3444e95e66f91596877eae6df38b5c76a",
        "85411105b7ea7c456e75c659bf6e19f3c244b4ecc85ec1e4d28d400485a228a2",
        "64589ff71379a15b922bd305200b39ee263f84595d1731b62efaac6efaa23818",
        "bc11620f86d9579c19e1898713cf61d349d12b35a938cbbb7e013334b18701ce"
    )
    const val TOTAL = 5

    private fun prefs(c: Context) = c.getSharedPreferences("shield", Context.MODE_PRIVATE)

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest((SALT + s.trim().uppercase()).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun stage(c: Context): Int {
        val p = prefs(c)
        if (p.getInt("set_id", 0) != SET_ID) { p.edit().putInt("set_id", SET_ID).putInt("stage", 0).apply() }
        return p.getInt("stage", 0)
    }

    /** true if the password was correct (and is now consumed) */
    fun submit(c: Context, input: String): Boolean {
        val s = stage(c)
        if (s >= TOTAL) return false
        if (hash(input) == HASHES[s]) { prefs(c).edit().putInt("stage", s + 1).apply(); return true }
        return false
    }

    fun complete(c: Context) = stage(c) >= TOTAL
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean("enabled", v).apply()
}
