package io.atomofiron.wirelessscan.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase

import java.io.File

import io.atomofiron.wirelessscan.R
import io.atomofiron.wirelessscan.I
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream


class OuiManager(private val co: Context) {
    private val OUI_SIZE: Long = 1419264
    private val db: SQLiteDatabase
    private val filePath = co.filesDir.absolutePath + "/oui.db"

    init {
        verifyFile()
        db = SQLiteDatabase.openDatabase(filePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun close() = db.close()

    fun find(bssid: String): String {
        if (!verifyFile())
            return "<no oui base>"

        var manuf = ""
        for (i in 0..2) {
            val mac = bssid.substring(0, 8 + (6 - i * 3)).toUpperCase()
            val cursor = db.rawQuery("select * from base where bssid=?;", arrayOf(mac))

            if (cursor.moveToFirst())
                manuf = cursor.getString(cursor.getColumnIndex("manuf")) ?: ""
            cursor.close()

            if (!manuf.isEmpty())
                return manuf.replace("\n", "")
        }

        return "<unknown>"
    }

    private fun verifyFile(): Boolean {
        val file = File(filePath)
        return file.exists() && file.length() == OUI_SIZE || copyOui()
    }

    private fun copyOui(): Boolean {
        var inp: InputStream? = null
        var out: OutputStream? = null

        try {
            inp = co.resources.openRawResource(R.raw.oui)
            out = FileOutputStream(filePath)

            val buffer = ByteArray(1024)
            var read = inp.read(buffer)
            while (read != -1) {
                out.write(buffer, 0, read)
                read = inp.read(buffer)
            }

            out.flush()
            inp.close()
            out.close()

            return true
        } catch (e: Exception) {
            I.log("copy oui error: ${e.message}")
        } finally {
            inp?.close()
            out?.close()
        }

        return false
    }

}

