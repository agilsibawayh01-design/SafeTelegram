package com.safetelegram.guard.domain

fun interface ArgoSearchDetector {
    fun isArgoSearchVisible(root: ScreenNode): Boolean
}

class SignatureArgoSearchDetector(
    private val maxDepth: Int = 25
) : ArgoSearchDetector {

    override fun isArgoSearchVisible(root: ScreenNode): Boolean {
        var foundUsername = false
        var foundDisplayName = false
        var foundDescription = false

        fun walk(node: ScreenNode, depth: Int) {
            if (foundUsername || depth > maxDepth) return

            sequenceOf(node.text, node.contentDescription)
                .filterNotNull()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { raw ->
                    val lower = raw.lowercase()
                    if (lower == ArgoSearchSignatures.USERNAME) foundUsername = true
                    if (normalize(lower) == ArgoSearchSignatures.NORMALIZED_DISPLAY_NAME) foundDisplayName = true
                    if (lower.contains(ArgoSearchSignatures.DESCRIPTION_FRAGMENT)) foundDescription = true
                }

            for (i in 0 until node.childCount) {
                if (foundUsername) return
                node.childAt(i)?.let { walk(it, depth + 1) }
            }
        }

        walk(root, 0)
        return foundUsername || (foundDisplayName && foundDescription)
    }

    private fun normalize(value: String): String =
        value.filter { it.isLetter() || it.isWhitespace() }
            .trim()
            .replace(Regex("\\s+"), " ")
}
