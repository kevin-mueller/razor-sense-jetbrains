package com.kevinmueller.razorsensejetbrains

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.XmlPatterns


class RazorCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            psiElement()
                .withParent(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute("class"))),
            RazorCompletionProvider()
        )
    }
}