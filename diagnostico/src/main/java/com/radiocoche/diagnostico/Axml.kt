package com.radiocoche.diagnostico

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/**
 * Lector mínimo del AndroidManifest.xml binario que va dentro de una APK.
 * Sirve para ver qué actividades, servicios y avisos (broadcasts) declara cada app de fábrica.
 */
object Axml {

    private val TAGS = setOf(
        "manifest", "uses-permission", "permission", "application",
        "activity", "activity-alias", "service", "receiver", "provider",
        "intent-filter", "action", "category", "data", "meta-data"
    )

    private val ATTRS = setOf(
        "package", "sharedUserId", "versionName",
        "name", "exported", "enabled", "permission", "process", "protectionLevel",
        "authorities", "targetActivity", "launchMode", "persistent", "priority",
        "scheme", "host", "mimeType", "path", "pathPrefix", "value"
    )

    fun dump(apkPath: String): String {
        val bytes = ZipFile(apkPath).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: return "  (sin manifiesto)\n"
            zip.getInputStream(entry).use { it.readBytes() }
        }
        return parse(bytes)
    }

    private fun parse(data: ByteArray): String {
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val out = StringBuilder()
        var strings: List<String> = emptyList()
        var depth = 0
        var pos = 8
        val end = minOf(b.getInt(4), data.size)

        while (pos + 8 <= end) {
            val type = b.getShort(pos).toInt() and 0xFFFF
            val headerSize = b.getShort(pos + 2).toInt() and 0xFFFF
            val size = b.getInt(pos + 4)
            if (size <= 0) break

            when (type) {
                0x0001 -> strings = readStringPool(b, pos, headerSize)
                0x0102 -> {
                    val ext = pos + headerSize
                    val name = str(strings, b.getInt(ext + 4))
                    val attrStart = b.getShort(ext + 8).toInt() and 0xFFFF
                    val attrSize = b.getShort(ext + 10).toInt() and 0xFFFF
                    val attrCount = b.getShort(ext + 12).toInt() and 0xFFFF
                    if (name in TAGS) {
                        out.append("  ").append("  ".repeat(depth)).append(name)
                        for (i in 0 until attrCount) {
                            val a = ext + attrStart + i * attrSize
                            val attrName = str(strings, b.getInt(a + 4))
                            if (attrName !in ATTRS) continue
                            val raw = b.getInt(a + 8)
                            val dataType = b.get(a + 15).toInt() and 0xFF
                            val value = b.getInt(a + 16)
                            out.append(' ').append(attrName).append('=')
                                .append(formatValue(strings, raw, dataType, value))
                        }
                        out.append('\n')
                    }
                    depth++
                }
                0x0103 -> if (depth > 0) depth--
            }
            pos += size
        }
        return out.toString()
    }

    private fun formatValue(strings: List<String>, raw: Int, dataType: Int, value: Int): String = when {
        raw != -1 -> str(strings, raw)
        dataType == 0x03 -> str(strings, value)
        dataType == 0x12 -> (value != 0).toString()
        dataType == 0x10 -> value.toString()
        dataType == 0x11 -> "0x" + Integer.toHexString(value)
        dataType == 0x01 -> "@0x" + Integer.toHexString(value)
        else -> "?" + Integer.toHexString(value)
    }

    private fun str(strings: List<String>, i: Int): String =
        if (i in strings.indices) strings[i] else ""

    private fun readStringPool(b: ByteBuffer, pos: Int, headerSize: Int): List<String> {
        val count = b.getInt(pos + 8)
        val flags = b.getInt(pos + 16)
        val stringsStart = b.getInt(pos + 20)
        val utf8 = (flags and 0x100) != 0
        val list = ArrayList<String>(count)
        for (i in 0 until count) {
            val off = pos + stringsStart + b.getInt(pos + headerSize + i * 4)
            list.add(
                try {
                    if (utf8) readUtf8(b, off) else readUtf16(b, off)
                } catch (e: Exception) {
                    ""
                }
            )
        }
        return list
    }

    private fun readUtf8(b: ByteBuffer, start: Int): String {
        var off = start
        // longitud en caracteres (se ignora)
        if ((b.get(off).toInt() and 0x80) != 0) off += 2 else off += 1
        var len = b.get(off).toInt() and 0xFF
        if ((len and 0x80) != 0) {
            len = ((len and 0x7F) shl 8) or (b.get(off + 1).toInt() and 0xFF)
            off += 2
        } else {
            off += 1
        }
        val bytes = ByteArray(len)
        for (k in 0 until len) bytes[k] = b.get(off + k)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readUtf16(b: ByteBuffer, start: Int): String {
        var off = start
        var len = b.getShort(off).toInt() and 0xFFFF
        off += 2
        if ((len and 0x8000) != 0) {
            len = ((len and 0x7FFF) shl 16) or (b.getShort(off).toInt() and 0xFFFF)
            off += 2
        }
        val chars = CharArray(len)
        for (k in 0 until len) chars[k] = b.getChar(off + k * 2)
        return String(chars)
    }
}
