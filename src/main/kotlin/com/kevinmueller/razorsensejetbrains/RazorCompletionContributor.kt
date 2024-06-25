package com.kevinmueller.razorsensejetbrains

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.XmlPatterns


class RazorCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().with(RazorCompletionPattern()), //TODO: this overrides the existing completion
            RazorCompletionProvider()
        )
    }
}