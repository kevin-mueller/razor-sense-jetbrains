package com.kevinmueller.razorsensejetbrains.cssClassCompletion

fun isInArtifactFolder(path: String): Boolean {
    return path.contains("/bin/") || path.contains("/obj/")
}