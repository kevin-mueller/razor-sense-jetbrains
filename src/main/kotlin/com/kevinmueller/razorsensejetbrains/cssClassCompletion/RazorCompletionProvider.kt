package com.kevinmueller.razorsensejetbrains.cssClassCompletion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.NotNull

internal class RazorCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        @NotNull parameters: CompletionParameters,
        @NotNull context: ProcessingContext,
        @NotNull result: CompletionResultSet
    ) {
        val cssCompletionService = parameters.editor.project?.service<CssCompletionService>() ?: return

        for (completion in cssCompletionService.cssCompletionItemsByProjectPath) {

            //TODO: this matches too much...
            //      not sure if http parsed classes match here 
            if (!fileIsFromProjectPath(completion.key, parameters.originalFile)) continue
            
            for (cssClassNameForFile in completion.value.getReferencedCssClasses(parameters.originalFile.name)) {
                for (cssClassName in cssClassNameForFile.cssClassNames) {
                    val lookupElement = LookupElementBuilder.create(cssClassName)
                    result.addElement(
                        lookupElement
                            .withIcon(AllIcons.Xml.Css_class)
                            .withTypeText(cssClassNameForFile.fileName, true)
                    )
                }
            }
        }

        result.stopHere()
    }

    private fun fileIsFromProjectPath(projectPath: String, originalFile: PsiFile): Boolean {
        var currentDirectory = originalFile.containingDirectory
        val projectFileName = projectPath.substringAfterLast("/")

        while (currentDirectory != null) {
            val projectFile = currentDirectory.findFile(projectFileName)
            if (projectFile != null)
                return true

            currentDirectory = currentDirectory.parent
        }

        return false
    }
}