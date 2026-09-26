package com.luafabric.studio.falling.ui.manual

/**
 * 手册索引清单条目，对应 assets/doc/manual.json 中的一项
 */
data class ManualPost(
    val title: String,
    val subtitle: String,
    val category: String,
    val tags: List<String>,
    val file: String,
    val order: Int
)

/**
 * 手册索引清单，对应 assets/doc/manual.json
 */
data class ManualIndex(
    val posts: List<ManualPost>
)
