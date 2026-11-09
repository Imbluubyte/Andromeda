package com.andromeda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small hand-rolled Markdown renderer: headings, lists, tasks, quotes, code, rules, inline styles, [[wiki links]]. */
sealed class MdBlock {
    class Heading(val level: Int, val text: String) : MdBlock()
    class Para(val text: String) : MdBlock()
    class Quote(val text: String) : MdBlock()
    class Item(val indent: Int, val marker: String, val text: String) : MdBlock()
    class Code(val text: String) : MdBlock()
    object Rule : MdBlock()
    object Gap : MdBlock()
}

fun parseBlocks(src: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = src.replace("\r\n", "\n").split('\n')
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()
        when {
            t.startsWith("```") -> {
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) { sb.appendLine(lines[i]); i++ }
                out.add(MdBlock.Code(sb.toString().trimEnd('\n')))
            }
            t.isEmpty() -> out.add(MdBlock.Gap)
            Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(t) -> out.add(MdBlock.Rule)
            t.startsWith("#") && Regex("^#{1,6} .*").matches(t) -> {
                val lvl = t.takeWhile { it == '#' }.length
                out.add(MdBlock.Heading(lvl, t.substring(lvl).trim()))
            }
            t.startsWith(">") -> out.add(MdBlock.Quote(t.removePrefix(">").trim()))
            else -> {
                val indent = line.takeWhile { it == ' ' || it == '\t' }.length / 2
                val task = Regex("^[-*+] \\[([ xX])] (.*)").find(t)
                val bullet = Regex("^[-*+] (.*)").find(t)
                val num = Regex("^(\\d+)[.)] (.*)").find(t)
                when {
                    task != null -> out.add(MdBlock.Item(indent, if (task.groupValues[1] == " ") "☐" else "☑", task.groupValues[2]))
                    bullet != null -> out.add(MdBlock.Item(indent, "•", bullet.groupValues[1]))
                    num != null -> out.add(MdBlock.Item(indent, num.groupValues[1] + ".", num.groupValues[2]))
                    else -> out.add(MdBlock.Para(t))
                }
            }
        }
        i++
    }
    return out
}

private val CODE_BG = Ad.raised

fun inlineText(src: String, onWiki: (String) -> Unit): AnnotatedString = buildAnnotatedString { appendInline(src, onWiki) }

private fun AnnotatedString.Builder.appendInline(src: String, onWiki: (String) -> Unit) {
    var i = 0
    val n = src.length
    fun styled(style: SpanStyle, inner: String) = withStyle(style) { appendInline(inner, onWiki) }
    while (i < n) {
        if (src.startsWith("[[", i)) {
            val e = src.indexOf("]]", i + 2)
            if (e > 0) {
                val raw = src.substring(i + 2, e)
                val target = raw.substringBefore('|').substringBefore('#').trim()
                val label = if (raw.contains('|')) raw.substringAfter('|') else raw
                withLink(
                    LinkAnnotation.Clickable("wiki", TextLinkStyles(SpanStyle(color = Ad.accent, textDecoration = TextDecoration.Underline))) { onWiki(target) }
                ) { append(label) }
                i = e + 2; continue
            }
        }
        if (src[i] == '[') {
            val close = src.indexOf("](", i + 1)
            val end = if (close > 0) src.indexOf(')', close + 2) else -1
            if (close > 0 && end > 0) {
                withLink(
                    LinkAnnotation.Url(src.substring(close + 2, end), TextLinkStyles(SpanStyle(color = Ad.link, textDecoration = TextDecoration.Underline)))
                ) { append(src.substring(i + 1, close)) }
                i = end + 1; continue
            }
        }
        if (src.startsWith("**", i)) {
            val e = src.indexOf("**", i + 2)
            if (e > 0) { styled(SpanStyle(fontWeight = FontWeight.Bold), src.substring(i + 2, e)); i = e + 2; continue }
        }
        if (src.startsWith("~~", i)) {
            val e = src.indexOf("~~", i + 2)
            if (e > 0) { styled(SpanStyle(textDecoration = TextDecoration.LineThrough), src.substring(i + 2, e)); i = e + 2; continue }
        }
        if (src[i] == '`') {
            val e = src.indexOf('`', i + 1)
            if (e > 0) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = CODE_BG, color = Ad.accent)) { append(src.substring(i + 1, e)) }
                i = e + 1; continue
            }
        }
        val wordStart = src[i] != '_' || i == 0 || !src[i - 1].isLetterOrDigit()
        if ((src[i] == '*' || src[i] == '_') && wordStart && i + 1 < n && src[i + 1] != ' ') {
            val e = src.indexOf(src[i], i + 1)
            if (e > i + 1) { styled(SpanStyle(fontStyle = FontStyle.Italic), src.substring(i + 1, e)); i = e + 1; continue }
        }
        append(src[i]); i++
    }
}

@Composable
fun MarkdownPreview(text: String, onWiki: (String) -> Unit, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 32.dp, vertical = 20.dp)) {
        itemsIndexed(blocks) { _, b -> BlockView(b, onWiki) }
    }
}

@Composable
private fun BlockView(b: MdBlock, onWiki: (String) -> Unit) {
    when (b) {
        is MdBlock.Heading -> {
            val size = when (b.level) { 1 -> 28; 2 -> 23; 3 -> 20; 4 -> 18; else -> 16 }
            Text(
                inlineText(b.text, onWiki), color = Ad.text, fontSize = size.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }
        is MdBlock.Para -> Text(inlineText(b.text, onWiki), color = Ad.text, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(vertical = 2.dp))
        is MdBlock.Quote -> Box(Modifier.padding(vertical = 3.dp).fillMaxWidth()) {
            Box(Modifier.width(3.dp).height(22.dp).background(Ad.accent))
            Text(inlineText(b.text, onWiki), color = Ad.muted, fontSize = 16.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 14.dp))
        }
        is MdBlock.Item -> Column(Modifier.padding(start = (b.indent * 20).dp, top = 1.dp, bottom = 1.dp)) {
            androidx.compose.foundation.layout.Row {
                Text(b.marker, color = Ad.accent, fontSize = 16.sp, modifier = Modifier.width(26.dp))
                Text(inlineText(b.text, onWiki), color = Ad.text, fontSize = 16.sp, lineHeight = 24.sp)
            }
        }
        is MdBlock.Code -> Text(
            b.text, color = Ad.text, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Ad.raised).padding(12.dp)
        )
        MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Ad.border)
        MdBlock.Gap -> Box(Modifier.height(8.dp))
    }
}
