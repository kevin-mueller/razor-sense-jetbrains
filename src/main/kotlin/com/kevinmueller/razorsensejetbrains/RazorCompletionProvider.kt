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

        // TODO: How to get actual project path? (Use the current file and parse the path?)
        val currentProjectPath = parameters.editor.project?.projectFilePath ?: return
        
        for (completion in cssCompletionService.cssFilesByProjectPath[currentProjectPath]!!) {
            result.addElement(LookupElementBuilder.create(completion))
        }
    }
}