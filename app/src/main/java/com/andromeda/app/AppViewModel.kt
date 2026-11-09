package com.andromeda.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andromeda.app.data.FileKind
import com.andromeda.app.data.Node
import com.andromeda.app.data.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Dialog {
    object NewProject : Dialog()
    class NewFile(val dir: Node, val kind: FileKind) : Dialog()
    class NewFolder(val dir: Node) : Dialog()
    class Rename(val node: Node) : Dialog()
    class Delete(val node: Node) : Dialog()
}

/** One visible row of the explorer tree. */
class TreeRow(val node: Node, val depth: Int)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val prefs = app.getSharedPreferences("andromeda", Context.MODE_PRIVATE)

    var ws by mutableStateOf<Workspace?>(null); private set
    var projects by mutableStateOf<List<Node>>(emptyList()); private set
    var project by mutableStateOf<Node?>(null); private set
    var active by mutableStateOf<Node?>(null); private set
    var sidebarOpen by mutableStateOf(true)
    var dialog by mutableStateOf<Dialog?>(null)
    var error by mutableStateOf<String?>(null)

    val tabs = mutableStateListOf<Node>()
    private val children = mutableStateMapOf<Uri, List<Node>>()
    private val expanded = mutableStateListOf<Uri>()

    init {
        val saved = prefs.getString(KEY_ROOT, null)?.let(Uri::parse)
        if (saved != null && hasAccess(saved)) openRoot(saved)
    }

    private fun hasAccess(uri: Uri) =
        ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }

    /** Called with the folder the user picked on first launch (or when changing folder). */
    fun setRoot(uri: Uri) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        prefs.edit().putString(KEY_ROOT, uri.toString()).apply()
        tabs.clear(); active = null; project = null; children.clear(); expanded.clear()
        openRoot(uri)
    }

    private fun openRoot(uri: Uri) {
        val root = DocumentFile.fromTreeUri(ctx, uri) ?: return
        ws = Workspace(ctx, root)
        refreshProjects(selectName = prefs.getString(KEY_PROJECT, null))
    }

    private fun io(block: suspend () -> Unit) = viewModelScope.launch {
        try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { error = e.message ?: "Something went wrong" }
    }

    fun refreshProjects(selectName: String? = null) {
        val w = ws ?: return
        io {
            val list = w.projects()
            withContext(Dispatchers.Main) {
                projects = list
                val pick = list.firstOrNull { it.name == selectName } ?: project?.let { p -> list.firstOrNull { it == p } } ?: list.firstOrNull()
                if (pick != null) selectProject(pick) else project = null
            }
        }
    }

    fun selectProject(p: Node) {
        project = p
        prefs.edit().putString(KEY_PROJECT, p.name).apply()
        tabs.clear(); active = null; children.clear(); expanded.clear()
        expanded.add(p.uri)
        load(p)
    }

    fun load(dir: Node) {
        val w = ws ?: return
        io {
            val list = w.children(dir.doc)
            withContext(Dispatchers.Main) { children[dir.uri] = list }
        }
    }

    fun toggle(dir: Node) {
        if (dir.uri in expanded) expanded.remove(dir.uri) else { expanded.add(dir.uri); load(dir) }
    }

    fun isExpanded(dir: Node) = dir.uri in expanded

    /** Flattened tree below the current project, in display order. */
    fun rows(): List<TreeRow> {
        val p = project ?: return emptyList()
        val out = ArrayList<TreeRow>()
        fun walk(dir: Node, depth: Int) {
            for (n in children[dir.uri].orEmpty()) {
                out.add(TreeRow(n, depth))
                if (n.isDir && n.uri in expanded) walk(n, depth + 1)
            }
        }
        walk(p, 0)
        return out
    }

    fun open(n: Node) {
        if (n.isDir || n.kind == FileKind.OTHER) return
        if (n !in tabs) tabs.add(n)
        active = n
    }

    fun close(n: Node) {
        val i = tabs.indexOf(n)
        if (i < 0) return
        tabs.removeAt(i)
        if (active == n) active = tabs.getOrNull(i.coerceAtMost(tabs.size - 1))
    }

    /** Opens a file of the current project by its title (used by [[wiki links]] and Atlas file links). */
    fun openByTitle(title: String) {
        val w = ws ?: return
        val p = project ?: return
        io {
            val n = w.findByTitle(p.doc, title)
            withContext(Dispatchers.Main) { if (n != null) open(n) else error = "No file named \"$title\"" }
        }
    }

    fun create(dir: Node, name: String, kind: FileKind?) {
        val w = ws ?: return
        io {
            val made = if (kind == null) w.createFolder(dir.doc, name) else w.createFile(dir.doc, name, kind)
            val list = w.children(dir.doc)
            withContext(Dispatchers.Main) {
                children[dir.uri] = list
                if (dir.uri !in expanded) expanded.add(dir.uri)
                if (made != null && kind != null) open(list.firstOrNull { it == made } ?: made)
            }
        }
    }

    fun createProject(name: String) {
        val w = ws ?: return
        io {
            val made = w.createProject(name)
            val list = w.projects()
            withContext(Dispatchers.Main) {
                projects = list
                (list.firstOrNull { it == made } ?: made)?.let { selectProject(it) }
            }
        }
    }

    fun rename(node: Node, newTitle: String) {
        val w = ws ?: return
        io {
            val parent = node.doc.parentFile
            val renamed = w.rename(node, newTitle)
            val list = parent?.let { w.children(it) }
            val plist = if (parent?.uri == w.root.uri) w.projects() else null
            withContext(Dispatchers.Main) {
                val i = tabs.indexOf(node)
                if (renamed != null && i >= 0) { tabs[i] = renamed; if (active == node) active = renamed }
                if (parent != null && list != null) children[parent.uri] = list
                if (plist != null) { projects = plist; if (project == node) renamed?.let { project = it } }
            }
        }
    }

    fun delete(node: Node) {
        val w = ws ?: return
        io {
            val parent = node.doc.parentFile
            val wasProject = parent?.uri == w.root.uri
            w.delete(node)
            val list = parent?.let { w.children(it) }
            withContext(Dispatchers.Main) {
                tabs.filter { it == node || it.doc.uri.toString().startsWith(node.uri.toString()) }.forEach { close(it) }
                if (parent != null && list != null) children[parent.uri] = list
                if (wasProject) refreshProjects()
            }
        }
    }

    companion object {
        private const val KEY_ROOT = "root_uri"
        private const val KEY_PROJECT = "last_project"
    }
}
