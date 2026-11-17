package com.nahuanotoca.superapp.core.util

import java.io.InputStream
import java.security.MessageDigest

/** Huella SHA-256 de un archivo, para deduplicar importaciones y versionar documentos. */
object Digest {

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
