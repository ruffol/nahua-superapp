package com.nahuanotoca.superapp.core.rag

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lector de EPUB sin dependencias nuevas: el formato es un ZIP con un OPF que declara el orden de
 * lectura (spine). Cada capítulo del spine se convierte en una "página" para reutilizar el mismo
 * pipeline de fragmentación y citas que los PDFs.
 *
 * Puro Kotlin/JVM para poder probarlo sin Android.
 */
object EpubParser {

    data class Chapter(val index: Int, val title: String, val text: String)

    private const val MAX_ENTRY_BYTES = 24 * 1024 * 1024

    fun parse(input: InputStream): List<Chapter> {
        val entries = readEntries(input)
        if (entries.isEmpty()) throw IllegalStateException("El EPUB está vacío o dañado")

        val container = entries.entries
            .firstOrNull { it.key.equals("META-INF/container.xml", ignoreCase = true) }
            ?.value
            ?: throw IllegalStateException("El EPUB no incluye META-INF/container.xml")

        val opfPath = findRootFile(container)
            ?: throw IllegalStateException("El EPUB no declara el archivo OPF")
        val opf = entries[opfPath] ?: throw IllegalStateException("Falta el OPF " + opfPath)

        val document = parseXml(opf)
        val base = opfPath.substringBeforeLast('/', "")

        val manifest = mutableMapOf<String, String>()
        document.getElementsByTagName("*").let { nodes ->
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                if (element.tagNameOf() != "item") continue
                val id = element.getAttribute("id")
                val href = element.getAttribute("href")
                if (id.isNotBlank() && href.isNotBlank()) manifest[id] = href
            }
        }

        val spine = mutableListOf<String>()
        document.getElementsByTagName("*").let { nodes ->
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                if (element.tagNameOf() != "itemref") continue
                val idref = element.getAttribute("idref")
                if (idref.isNotBlank()) spine += idref
            }
        }

        val chapters = mutableListOf<Chapter>()
        spine.forEach { idref ->
            val href = manifest[idref] ?: return@forEach
            val path = resolve(base, href)
            val bytes = entries[path] ?: entries.entries
                .firstOrNull { it.key.equals(path, ignoreCase = true) }
                ?.value
                ?: return@forEach

            val html = String(bytes, Charsets.UTF_8)
            val text = htmlToText(html)
            if (text.length < 80) return@forEach
            chapters += Chapter(
                index = chapters.size + 1,
                title = chapterTitle(html, chapters.size + 1),
                text = text,
            )
        }

        if (chapters.isEmpty()) throw IllegalStateException("El EPUB no tiene capítulos con texto")
        return chapters
    }

    /** Convierte XHTML a texto plano respetando párrafos. */
    fun htmlToText(html: String): String {
        var text = html.replace(
            Regex("(?is)<(script|style|nav)[^>]*>.*?</\\1>"),
            " ",
        )
        text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?i)</(p|div|h[1-6]|li|tr|blockquote|section|article)>"), "\n")
        text = text.replace(Regex("<[^>]+>"), " ")
        text = decodeEntities(text)
        return text
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" ?\\n ?"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun chapterTitle(html: String, number: Int): String {
        val heading = Regex("(?is)<h[12][^>]*>(.*?)</h[12]>").find(html)?.groupValues?.get(1)
        val title = heading?.let { htmlToText(it) }?.takeIf { it.isNotBlank() && it.length < 120 }
        return title ?: ("Capítulo " + number)
    }

    private fun findRootFile(container: ByteArray): String? {
        val document = runCatching { parseXml(container) }.getOrNull() ?: return null
        val nodes = document.getElementsByTagName("*")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            if (element.tagNameOf() == "rootfile") {
                val path = element.getAttribute("full-path")
                if (path.isNotBlank()) return path
            }
        }
        return null
    }

    /** localName es null cuando el parser no es namespace-aware; se cae al tagName sin prefijo. */
    private fun Element.tagNameOf(): String = (localName ?: tagName).substringAfter(':')

    private fun parseXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = false }
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(bytes))

    private fun resolve(base: String, href: String): String {
        val clean = href.substringBefore('#')
        if (base.isBlank()) return clean
        return if (clean.startsWith("/")) clean.trimStart('/') else base + "/" + clean
    }

    private fun readEntries(input: InputStream): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                val buffer = zip.readBytes()
                total += buffer.size
                if (total > MAX_ENTRY_BYTES) break
                if (name.endsWith(".xml", true) || name.endsWith(".opf", true) ||
                    name.endsWith(".xhtml", true) || name.endsWith(".html", true) ||
                    name.endsWith(".htm", true)
                ) {
                    result[name] = buffer
                }
            }
        }
        return result
    }

    private fun decodeEntities(text: String): String {
        var out = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&hellip;", "…")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")

        out = Regex("&#x([0-9a-fA-F]{1,6});").replace(out) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else ""
        }
        out = Regex("&#(\\d{1,7});").replace(out) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else ""
        }
        return out
    }
}