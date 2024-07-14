package ru.raslav.wirelessscan.utils

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase

import java.io.File

import ru.raslav.wirelessscan.report
import java.io.FileOutputStream
import java.util.regex.Pattern

private const val DB_NAME = "oui.db"
private const val OUI_SIZE = 2539520L
private val DIGITS = arrayOf(6, 7, 9)

private const val TABLE = "OUI"
private const val COLUMN_MAC = "MAC"
private const val COLUMN_LABEL = "label"
private const val COLUMN_DESC = "description"

class OuiManager private constructor(context: Context) {
    companion object {
        private lateinit var instance: OuiManager

        fun init(context: Context) {
            instance = OuiManager(context)
        }

        fun find(bssid: String): Manufacturer = instance.find(bssid)
    }
    private val db: SQLiteDatabase
    private val tmpDbPath = context.filesDir.absolutePath + "/tmp.db"
    private val dbPath = context.filesDir.absolutePath + "/$DB_NAME"
    private val txtPath = context.filesDir.absolutePath + "/oui.txt"

    init {
        context.verifyFile()
        db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
        //Thread(::parseTxt).start()
    }

    private fun find(bssid: String): Manufacturer {
        val mac = bssid.replace(":", "").uppercase()
        DIGITS.map { mac.take(it) }.forEach { digits ->
            db.find(digits)?.let { return it }
        }
        return Manufacturer.Unknown
    }

    @SuppressLint("Range")
    private fun SQLiteDatabase.find(digits: String): Manufacturer? {
        val cursor = rawQuery("select * from $TABLE where $COLUMN_MAC=?;", arrayOf(digits))
        val manufacturer = cursor.takeIf { it.moveToFirst() }?.run {
            Manufacturer(
                digits,
                label = getString(cursor.getColumnIndex(COLUMN_LABEL)),
                description = getString(cursor.getColumnIndex(COLUMN_DESC)),
            )
        }
        cursor.close()
        return manufacturer
    }

    private fun Context.verifyFile() {
        val file = File(dbPath)
        if (!file.exists() || file.length() != OUI_SIZE) {
            copyOui()
        }
    }

    private fun Context.copyOui() {
        val stream = assets.open(DB_NAME)
        val dst = FileOutputStream(dbPath)
        stream.copyTo(dst)
        dst.close()
    }

    private fun parseTxt() {
        // https://www.wireshark.org/download/automated/data/manuf
        // 08:45:D1         	Cisco       	Cisco Systems, Inc
        // 00:55:DA:90/28   	QuantumCommu	Quantum Communication Technology Co., Ltd.,Anhui
        // 00:1B:C5:0B:90/36	DenkiKogyo  	Denki Kogyo Company, Limited
        val file = File(txtPath)
        if (!file.exists()) {
            return report("no OUI text file")
        }
        val delimiter = Pattern.compile(" *\t")
        file.readText(Charsets.UTF_8)
            .split('\n')
            .map { line ->
                val parts = line.split(delimiter)
                if (parts.size != 3) {
                    return report("invalid line: $line")
                }
                val address = parts.first().split('/') // "08:45:D1" or "00:55:DA:90/28" or "00:1B:C5:0B:90/36"
                var mac = address.first().replace(":", "") // "0845D1" or "0055DA90" or "001BC50B90"
                address.getOrNull(1) // null or 28 or 36
                    ?.toIntOrNull() // null then "0845D1"
                    ?.let { it / 4 } // 28 or 36 -> 7 or 9
                    ?.let { mac = mac.take(it) } // "0055DA90" or "001BC50B90" -> "0055DA9" or "001BC50B9"
                Node(mac, parts[1], parts[2].replace('\'', '’')) // ' -> ’
            }
            .writeTo(tmpDbPath)
    }

    private fun List<Node>.writeTo(path: String) {
        SQLiteDatabase.openOrCreateDatabase(path, null).run {
            execSQL("create table $TABLE ($COLUMN_MAC TEXT NOT NULL, $COLUMN_LABEL TEXT NOT NULL, $COLUMN_DESC TEXT NOT NULL)")
            val part = mutableListOf<String>()
            for (node in this@writeTo) {
                part.add("('${node.digits}', '${node.label}', '${node.description}')")
                if (part.size == 100) {
                    insert(part)
                    part.clear()
                }
            }
            insert(part)
            close()
        }
    }

    private fun SQLiteDatabase.insert(part: List<String>) {
        if (part.isNotEmpty()) {
            execSQL("insert into $TABLE ($COLUMN_MAC, $COLUMN_LABEL, $COLUMN_DESC) values ${part.joinToString()}")
        }
    }
}

private class Node(
    val digits: String,
    val label: String,
    val description: String,
)
