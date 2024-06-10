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

        for (completion in cssCompletionService.cssFilesByProjectPath.entries) {
            val projectSourceDirectory = completion.key.substringBeforeLast("/")
            
            //TODO: this matches too much...
            if (parameters.originalFile.virtualFile.path.contains(projectSourceDirectory))
                result.addAllElements(completion.value.map { x -> LookupElementBuilder.create(x) })
        }
    }
}