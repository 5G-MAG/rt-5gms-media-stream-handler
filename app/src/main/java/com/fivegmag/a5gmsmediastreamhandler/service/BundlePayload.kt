package com.fivegmag.a5gmsmediastreamhandler.service

import android.os.Bundle
import android.os.Parcelable

/** The payload of a message as a bundle; entries without a value are left out */
fun Map<String, Any?>.toBundle(): Bundle {
    val bundle = Bundle()
    for ((key, value) in this) {
        when (value) {
            null -> {}
            is String -> bundle.putString(key, value)
            is Boolean -> bundle.putBoolean(key, value)
            is Int -> bundle.putInt(key, value)
            is Long -> bundle.putLong(key, value)
            else -> throw IllegalArgumentException("The payload item [$key] has a type that a bundle cannot carry")
        }
    }
    return bundle
}

/** PayloadReader over a bundle; the class loader of the common library is set so that its Parcelable classes can be read */
class BundleReader(private val bundle: Bundle) : PayloadReader {
    init {
        bundle.classLoader = com.fivegmag.a5gmscommonlibrary.models.BdtWindow::class.java.classLoader
    }

    override fun string(key: String): String? = bundle.getString(key)
    override fun bool(key: String): Boolean = bundle.getBoolean(key)
    override fun has(key: String): Boolean = bundle.containsKey(key)

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    override fun <T> list(key: String): List<T> = (bundle.getParcelableArrayList<Parcelable>(key) ?: arrayListOf()) as List<T>

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    override fun <T> item(key: String): T? = bundle.getParcelable<Parcelable>(key) as T?
}
