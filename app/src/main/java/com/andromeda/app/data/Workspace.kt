package com.andromeda.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

enum class FileKind(val ext: String) {
    MARKDOWN("md"),
    ATLAS("atlas"),
    OTHER("")
}

/** A file or folder inside the workspace. Equality is by uri. */
class Node(val doc: DocumentFile, val name: String, val isDir: Boolean) {
    val uri: Uri get() = doc.uri
    val kind: FileKind = when {
        isDir -> FileKind.OTHER
        name.endsWith(".md", true) -> FileKind.MARKDOWN
        name.endsWith(".atlas", true) -> FileKind.ATLAS
        else -> FileKind.OTHER
    }
    val title: String = if (!isDir && kind != FileKind.OTHER) name.substringBeforeLast('.') else name
    override fun equals(other: Any?) = other is Node && other.uri == uri
    override fun hashCode() = uri.hashCode()
}

/**
 * All file access goes through here (Storage Access Framework). Call from Dispatchers.IO.
 * Top-level folders of [root] are projects.
 */
class Workspace(private val ctx: Context, val root: DocumentFile) {

    private fun toNode(d: DocumentFile) = Node(d, d.name ?: "?", d.isDirectory)

    fun children(dir: DocumentFile): List<Node> =
        dir.listFiles()
            .map(::toNode)
            .filter { !it.name.startsWith(".") && it.name != ATTACH_DIR }
            .filter { it.isDir || it.kind != FileKind.OTHER }
            .sortedWith(compareByDescending<Node> { it.isDir }.thenBy { it.name.lowercase() })

    fun projects(): List<Node> = children(root).filter { it.isDir }

    fun createProject(name: String): Node? = root.createDirectory(uniqueName(root, clean(name)))?.let(::toNode)

    fun createFolder(parent: DocumentFile, name: String): Node? =
        parent.createDirectory(uniqueName(parent, clean(name)))?.let(::toNode)

    fun createFile(dir: DocumentFile, title: String, kind: FileKind): Node? {
        val fileName = uniqueName(dir, clean(title) + "." + kind.ext)
        val f = dir.createFile("application/octet-stream", fileName) ?: return null
        val node = toNode(f)
        write(node, if (kind == FileKind.ATLAS) AtlasJson.encode(AtlasDoc()) else "")
        return node
    }

    fun rename(node: Node, newTitle: String): Node? {
        val parent = node.doc.parentFile
        val target = if (node.isDir) clean(newTitle) else clean(newTitle) + "." + node.kind.ext
        val name = if (parent != null && target != node.name) uniqueName(parent, target) else target
        return if (node.doc.renameTo(name)) toNode(node.doc.parentFile?.findFile(name) ?: node.doc) else null
    }

    fun delete(node: Node): Boolean = node.doc.delete()

    fun read(node: Node): String =
        ctx.contentResolver.openInputStream(node.uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

    fun write(node: Node, text: String) {
        ctx.contentResolver.openOutputStream(node.uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** Every markdown/atlas file below [dir], recursively. */
    fun allFiles(dir: DocumentFile): List<Node> =
        children(dir).flatMap { if (it.isDir) allFiles(it.doc) else listOf(it) }

    fun findByTitle(project: DocumentFile, title: String): Node? =
        allFiles(project).firstOrNull { it.title.equals(title, true) }

    /** Copies a picked image into the project's attachment folder, returns the stored file name. */
    fun importImage(project: DocumentFile, source: Uri): String? {
        val dir = project.findFile(ATTACH_DIR) ?: project.createDirectory(ATTACH_DIR) ?: return null
        val base = "img_" + System.currentTimeMillis() + ".img"
        val out = dir.createFile("application/octet-stream", base) ?: return null
        ctx.contentResolver.openInputStream(source)?.use { input ->
            ctx.contentResolver.openOutputStream(out.uri, "wt")?.use { input.copyTo(it) }
        }
        return base
    }

    fun attachment(project: DocumentFile, fileName: String): DocumentFile? =
        project.findFile(ATTACH_DIR)?.findFile(fileName)

    fun readBytes(file: DocumentFile): ByteArray? =
        ctx.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }

    /** The project (top-level folder) that contains [node]. */
    fun projectOf(node: Node): DocumentFile {
        var cur: DocumentFile = node.doc
        while (true) {
            val p = cur.parentFile ?: return root
            if (p.uri == root.uri) return cur
            cur = p
        }
    }

    private fun clean(s: String) = s.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Untitled" }

    private fun uniqueName(dir: DocumentFile, wanted: String): String {
        if (dir.findFile(wanted) == null) return wanted
        val dot = wanted.lastIndexOf('.')
        val stem = if (dot > 0) wanted.substring(0, dot) else wanted
        val ext = if (dot > 0) wanted.substring(dot) else ""
        var i = 2
        while (dir.findFile("$stem $i$ext") != null) i++
        return "$stem $i$ext"
    }

    companion object {
        const val ATTACH_DIR = "_attachments"
    }
}
