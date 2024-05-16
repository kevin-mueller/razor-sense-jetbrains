package com.kevinmueller.razorsensejetbrains

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.NotNull

internal class RazorCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        @NotNull parameters: CompletionParameters,
        @NotNull context: ProcessingContext,
        @NotNull result: CompletionResultSet
    ) {

        val cssCompletionService = parameters.editor.project?.service<CssCompletionService>() ?: return

        for (completion in cssCompletionService.getCompletions()) {
            result.addElement(LookupElementBuilder.create(completion))
        }
    }
}