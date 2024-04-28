package com.kevinmueller.razorsensejetbrains

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.NotNull


internal class RazorCompletionProvider :
    CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        @NotNull parameters: CompletionParameters,
        @NotNull context: ProcessingContext,
        @NotNull result: CompletionResultSet
    ) {
        
        result.addElement(LookupElementBuilder.create("test"))
    }
}