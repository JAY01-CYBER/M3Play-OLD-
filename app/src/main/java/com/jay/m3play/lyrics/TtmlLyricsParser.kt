/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.lyrics

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import kotlin.math.roundToLong

/** Reads provider TTML without fetching external entities or flattening syllable spaces. */
internal object TtmlLyricsParser {
    fun parse(xml: String): List<LyricsEntry> {
        if (xml.length > 2_000_000 || xml.contains("<!DOCTYPE", true) || xml.contains("<!ENTITY", true)) return emptyList()
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        return document.getAllElements().asSequence()
            .filter { it.localName() == "p" && it.parents().any { parent -> parent.localName() == "body" } }
            .take(2000)
            .flatMap { paragraph ->
                val backgrounds = paragraph.getAllElements().filter { it !== paragraph && it.role() == "x-bg" }
                (listOfNotNull(readLine(paragraph, paragraph, false)) +
                    backgrounds.mapNotNull { readLine(it, paragraph, true) }).asSequence()
            }.sorted().toList()
    }

    private fun readLine(element: Element, paragraph: Element, background: Boolean): LyricsEntry? {
        val text = StringBuilder()
        val words = mutableListOf<LyricsWord>()
        fun visit(node: Node, begin: Long? = null, end: Long? = null) {
            when (node) {
                is TextNode -> {
                    val value = node.wholeText
                    // Ignore XML indentation; real spaces between spans are significant.
                    if (value.isBlank() && (value.contains('\n') || value.contains('\r'))) return
                    val start = text.length
                    text.append(value)
                    if (begin != null && value.isNotBlank()) words += LyricsWord(begin, start, text.length, end)
                }
                is Element -> {
                    if (node !== element && node.role() in setOf("x-bg", "x-translation", "x-roman")) return
                    if (node.localName() == "br") { text.append('\n'); return }
                    val timed = node.localName() == "span"
                    val start = if (timed) time(node.attr("begin")) ?: begin else begin
                    val finish = if (timed) time(node.attr("end")) ?: end else end
                    node.childNodes().forEach { visit(it, start, finish) }
                }
            }
        }
        visit(element)
        if (text.isBlank()) return null
        val start = if (background) time(element.attr("begin")) ?: words.minOfOrNull { it.time }
            ?: time(paragraph.attr("begin")) else time(paragraph.attr("begin")) ?: words.minOfOrNull { it.time }
        if (start == null) return null
        val end = time(element.attr("end")) ?: time(paragraph.attr("end"))
            ?: words.mapNotNull { it.endTime }.maxOrNull()
        return LyricsEntry(start, text.toString(), words, end?.takeIf { it > start })
    }

    private fun Element.localName() = normalName().substringAfter(':')
    private fun Element.role() = attributes().firstOrNull { it.key.substringAfter(':') == "role" }?.value.orEmpty()

    internal fun time(value: String): Long? {
        if (value.isBlank()) return null
        val input = value.trim()
        val seconds = when {
            input.endsWith("ms") -> input.dropLast(2).toDoubleOrNull()?.div(1000)
            input.endsWith("s") -> input.dropLast(1).toDoubleOrNull()
            else -> {
                val parts = input.split(':')
                if (parts.size !in 1..3) return null
                var total = 0.0
                for (part in parts) total = total * 60 + (part.toDoubleOrNull() ?: return null)
                total
            }
        } ?: return null
        return (seconds * 1000).takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE }?.roundToLong()
    }
}
