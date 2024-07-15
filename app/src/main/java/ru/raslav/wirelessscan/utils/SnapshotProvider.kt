package ru.raslav.wirelessscan.utils

import android.content.ContentProvider
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.content.ContentValues
import android.database.Cursor
import java.io.File
import java.io.FileNotFoundException

class SnapshotProvider : ContentProvider() {

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val file = File(
            context?.filesDir ?: return null,
            uri.path ?: return null,
        )
        if (!file.exists())
            throw FileNotFoundException(file.absolutePath)

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
    }

    override fun getType(uri: Uri): String = "text/xml"

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, strings: Array<String>?, s: String?, strings2: Array<String>?, s2: String?): Cursor? = null

    override fun insert(uri: Uri, contentValues: ContentValues?): Uri? = null

    override fun delete(uri: Uri, s: String?, strings: Array<String>?): Int = 0

    override fun update(uri: Uri, contentValues: ContentValues?, s: String?, strings: Array<String>?): Int = 0
}