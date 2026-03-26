package io.github.bovinemagnet.antoraconfluence.engine.model

import io.github.bovinemagnet.antoraconfluence.antora.AntoraPage

enum class NodeType {
    COMPONENT, VERSION, MODULE, PAGE
}

data class HierarchyNode(
    val canonicalKey: String,
    val title: String,
    val nodeType: NodeType,
    val children: MutableList<HierarchyNode> = mutableListOf(),
    val sourcePage: AntoraPage? = null,
    var htmlContent: String? = null,
    var confluencePageId: String? = null
)
