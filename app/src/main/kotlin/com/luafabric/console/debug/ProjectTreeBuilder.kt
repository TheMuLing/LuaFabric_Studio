package com.luafabric.console.debug

import java.io.File

/**
 * 项目文件树：扫描 luaDir + luaExtDir，合并为相对路径树（目录在前，按名排序）。
 * 同一文件在两根下出现多次时去重（seen 记录）。
 */
object ProjectTreeBuilder {

    class Node(
        val name: String,
        val isDir: Boolean,
        val relativePath: String,
        val children: MutableList<Node>
    )

    fun build(luaDir: String?, luaExtDir: String?): List<Node> {
        val root = Node("", true, "", mutableListOf())
        val seen = HashSet<String>()
        for (base in listOfNotNull(luaDir, luaExtDir).filter { it.isNotBlank() }) {
            val baseFile = File(base)
            if (!baseFile.isDirectory) continue
            baseFile.walkTopDown().forEach { f ->
                if (f == baseFile) return@forEach
                val rel = f.relativeTo(baseFile).path.replace('\\', '/')
                if (seen.add(rel)) {
                    insert(root, rel, f.isDirectory)
                }
            }
        }
        return sortNodes(root.children)
    }

    private fun insert(node: Node, rel: String, isDir: Boolean) {
        val idx = rel.indexOf('/')
        if (idx < 0) {
            node.children.add(Node(rel, isDir, join(node.relativePath, rel), mutableListOf()))
            return
        }
        val name = rel.substring(0, idx)
        val rest = rel.substring(idx + 1)
        val child = node.children.firstOrNull { it.name == name }
            ?: Node(name, true, join(node.relativePath, name), mutableListOf()).also { node.children.add(it) }
        insert(child, rest, isDir)
    }

    private fun join(parent: String, name: String): String =
        if (parent.isEmpty()) name else "$parent/$name"

    private fun sortNodes(nodes: List<Node>): List<Node> {
        val sorted = nodes.sortedWith(compareBy({ !it.isDir }, { it.name }))
        for (n in sorted) {
            n.children.clear()
            n.children.addAll(sortNodes(n.children))
        }
        return sorted
    }
}
