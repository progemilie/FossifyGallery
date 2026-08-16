package org.fossify.gallery.helpers

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * The plain integers and byte runs an image container is built out of, in the widths and byte orders
 * the formats write them in. Shared by the JPEG, PNG and WebP walkers behind [ContainerMetadata];
 * there is nothing here about metadata, only about bytes.
 */

internal const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8
private const val BUFFER_SIZE = 8192

internal fun ByteArray.startsWith(prefix: ByteArray, at: Int = 0): Boolean {
    if (size - at < prefix.size) return false
    return prefix.indices.all { this[at + it] == prefix[it] }
}

/** Steps over [count] bytes, failing outright rather than coming up short the way skip() may. */
internal fun DataInputStream.skipFully(count: Int) = readFully(ByteArray(count))

internal fun OutputStream.writeUnsignedShort(value: Int) {
    write((value ushr BITS_PER_BYTE) and BYTE_MASK)
    write(value and BYTE_MASK)
}

internal fun OutputStream.writeInt(value: Int) {
    for (shift in (Int.SIZE_BITS - BITS_PER_BYTE) downTo 0 step BITS_PER_BYTE) {
        write((value ushr shift) and BYTE_MASK)
    }
}

internal fun OutputStream.writeIntLittleEndian(value: Int) {
    for (shift in 0 until Int.SIZE_BITS step BITS_PER_BYTE) {
        write((value ushr shift) and BYTE_MASK)
    }
}

internal fun RandomAccessFile.readIntLittleEndian(): Int {
    val bytes = ByteArray(Int.SIZE_BYTES).also { readFully(it) }
    return bytes.indices.fold(0) { value, index ->
        value or ((bytes[index].toInt() and BYTE_MASK) shl (index * BITS_PER_BYTE))
    }
}

/** Passes [length] bytes straight through, or steps over them when there is nowhere to write. */
internal fun InputStream.passThrough(out: OutputStream?, length: Int) =
    copyBytes(length, out) { buffer, count -> read(buffer, 0, count) }

internal fun OutputStream.writeFrom(handle: RandomAccessFile, length: Int) =
    copyBytes(length, this) { buffer, count -> handle.read(buffer, 0, count) }

private inline fun copyBytes(length: Int, out: OutputStream?, read: (ByteArray, Int) -> Int) {
    val buffer = ByteArray(BUFFER_SIZE)
    var left = length
    while (left > 0) {
        val count = read(buffer, minOf(buffer.size, left))
        if (count <= 0) break
        out?.write(buffer, 0, count)
        left -= count
    }
}
