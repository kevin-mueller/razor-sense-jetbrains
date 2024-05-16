package com.kevinmueller.razorsensejetbrains

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns


class RazorCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().with(RazorCompletionPattern()),
            RazorCompletionProvider()
        )
    }
}