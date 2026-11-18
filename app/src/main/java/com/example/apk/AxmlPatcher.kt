package com.example.apk

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Patches strings in an Android Binary XML (`AXML`) string pool chunk (`RES_STRING_POOL_TYPE = 0x0001`).
 * Used to give each generated APK a unique package name, explicit MainActivity class name, and custom label.
 */
object AxmlPatcher {

    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val UTF8_FLAG = 0x00000100

    fun replaceStrings(axml: ByteArray, map: Map<String, String>): ByteArray {
        if (map.isEmpty() || axml.size < 36) return axml

        val inBuf = ByteBuffer.wrap(axml).order(ByteOrder.LITTLE_ENDIAN)
        val xmlType = inBuf.getShort(0).toInt() and 0xFFFF
        val xmlHeaderSize = inBuf.getShort(2).toInt() and 0xFFFF
        if (xmlType != RES_XML_TYPE || xmlHeaderSize < 8) return axml

        val poolOffset = xmlHeaderSize
        val poolType = inBuf.getShort(poolOffset).toInt() and 0xFFFF
        val poolHeaderSize = inBuf.getShort(poolOffset + 2).toInt() and 0xFFFF
        val poolSize = inBuf.getInt(poolOffset + 4)
        val stringCount = inBuf.getInt(poolOffset + 8)
        val styleCount = inBuf.getInt(poolOffset + 12)
        val flags = inBuf.getInt(poolOffset + 16)
        val stringsStart = inBuf.getInt(poolOffset + 20)

        if (poolType != RES_STRING_POOL_TYPE || styleCount != 0 || stringCount <= 0) {
            return axml
        }

        val isUtf8 = (flags and UTF8_FLAG) != 0
        val offsetsStart = poolOffset + poolHeaderSize
        val stringsDataStart = poolOffset + stringsStart

        val originalStrings = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val relOffset = inBuf.getInt(offsetsStart + i * 4)
            val strPos = stringsDataStart + relOffset
            val decoded = if (isUtf8) {
                readUtf8String(axml, strPos)
            } else {
                readUtf16LeString(inBuf, strPos)
            }
            originalStrings.add(decoded)
        }

        var changed = false
        val updatedStrings = originalStrings.map { old ->
            val replacement = map[old]
            if (replacement != null && replacement != old) {
                changed = true
                replacement
            } else {
                old
            }
        }
        if (!changed) return axml

        // Encode updated strings in UTF-16LE (standard for AndroidManifest.xml string pools)
        val newOffsets = IntArray(stringCount)
        val stringsBlob = ByteArrayOutputStream()
        for (i in 0 until stringCount) {
            newOffsets[i] = stringsBlob.size()
            val s = updatedStrings[i]
            val utf16Bytes = s.toByteArray(Charsets.UTF_16LE)
            val charLen = s.length
            // u16 char length (for strings < 0x8000 chars)
            stringsBlob.write(charLen and 0xFF)
            stringsBlob.write((charLen ushr 8) and 0xFF)
            stringsBlob.write(utf16Bytes)
            // u16 null terminator
            stringsBlob.write(0)
            stringsBlob.write(0)
        }

        // Pad string data to 4-byte alignment
        while (stringsBlob.size() % 4 != 0) {
            stringsBlob.write(0)
        }

        val newStringsBytes = stringsBlob.toByteArray()
        val newStringsStart = poolHeaderSize + stringCount * 4
        val newPoolSize = newStringsStart + newStringsBytes.size
        val tailOffset = poolOffset + poolSize
        val tailLength = axml.size - tailOffset
        if (tailLength < 0) return axml

        val newTotalSize = xmlHeaderSize + newPoolSize + tailLength
        val outBytes = ByteArray(newTotalSize)
        val outBuf = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Copy XML header and update total size
        System.arraycopy(axml, 0, outBytes, 0, xmlHeaderSize)
        outBuf.putInt(4, newTotalSize)

        // 2. Write String Pool header (clear UTF8 flag since we emit UTF-16LE)
        System.arraycopy(axml, poolOffset, outBytes, poolOffset, poolHeaderSize)
        val newFlags = flags and UTF8_FLAG.inv()
        outBuf.putInt(poolOffset + 4, newPoolSize)
        outBuf.putInt(poolOffset + 12, 0) // styleCount = 0
        outBuf.putInt(poolOffset + 16, newFlags)
        outBuf.putInt(poolOffset + 20, newStringsStart)
        outBuf.putInt(poolOffset + 24, 0) // stylesStart = 0

        // 3. Write string offsets
        for (i in 0 until stringCount) {
            outBuf.putInt(poolOffset + poolHeaderSize + i * 4, newOffsets[i])
        }

        // 4. Write string data
        System.arraycopy(
            newStringsBytes,
            0,
            outBytes,
            poolOffset + newStringsStart,
            newStringsBytes.size
        )

        // 5. Copy remaining AXML chunks unchanged
        System.arraycopy(
            axml,
            tailOffset,
            outBytes,
            poolOffset + newPoolSize,
            tailLength
        )

        return outBytes
    }

    fun readStrings(axml: ByteArray): List<String> {
        if (axml.size < 36) return emptyList()
        val inBuf = ByteBuffer.wrap(axml).order(ByteOrder.LITTLE_ENDIAN)
        val xmlType = inBuf.getShort(0).toInt() and 0xFFFF
        val xmlHeaderSize = inBuf.getShort(2).toInt() and 0xFFFF
        if (xmlType != RES_XML_TYPE || xmlHeaderSize < 8) return emptyList()

        val poolOffset = xmlHeaderSize
        val poolType = inBuf.getShort(poolOffset).toInt() and 0xFFFF
        val poolHeaderSize = inBuf.getShort(poolOffset + 2).toInt() and 0xFFFF
        val stringCount = inBuf.getInt(poolOffset + 8)
        val flags = inBuf.getInt(poolOffset + 16)
        val stringsStart = inBuf.getInt(poolOffset + 20)
        if (poolType != RES_STRING_POOL_TYPE || stringCount <= 0) return emptyList()

        val isUtf8 = (flags and UTF8_FLAG) != 0
        val offsetsStart = poolOffset + poolHeaderSize
        val stringsDataStart = poolOffset + stringsStart

        return (0 until stringCount).map { i ->
            val relOffset = inBuf.getInt(offsetsStart + i * 4)
            val strPos = stringsDataStart + relOffset
            if (isUtf8) {
                readUtf8String(axml, strPos)
            } else {
                readUtf16LeString(inBuf, strPos)
            }
        }
    }

    private fun readUtf16LeString(buf: ByteBuffer, offset: Int): String {
        val first = buf.getShort(offset).toInt() and 0xFFFF
        val (charLen, charStart) = if ((first and 0x8000) != 0) {
            val second = buf.getShort(offset + 2).toInt() and 0xFFFF
            (((first and 0x7FFF) shl 16) or second) to (offset + 4)
        } else {
            first to (offset + 2)
        }
        val bytes = ByteArray(charLen * 2)
        for (i in bytes.indices) {
            bytes[i] = buf.get(charStart + i)
        }
        return String(bytes, Charsets.UTF_16LE)
    }

    private fun readUtf8String(bytes: ByteArray, offset: Int): String {
        var pos = offset
        val u16LenFirst = bytes[pos].toInt() and 0xFF
        pos += if ((u16LenFirst and 0x80) != 0) 2 else 1

        val utf8LenFirst = bytes[pos].toInt() and 0xFF
        val byteLen: Int
        if ((utf8LenFirst and 0x80) != 0) {
            val second = bytes[pos + 1].toInt() and 0xFF
            byteLen = ((utf8LenFirst and 0x7F) shl 8) or second
            pos += 2
        } else {
            byteLen = utf8LenFirst
            pos += 1
        }
        return String(bytes, pos, byteLen, Charsets.UTF_8)
    }
}
