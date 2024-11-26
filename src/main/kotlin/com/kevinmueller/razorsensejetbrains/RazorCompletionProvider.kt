package com.kevinmueller.razorsensejetbrains

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
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
            if (fileIsFromProjectPath(completion.key, parameters.originalFile)) {
                for (cssClassNameForFile in completion.value.getReferencedCssClassNames()) {
                    for (x in cssClassNameForFile.cssClassNames) {
                        val psiElement =
                            findPsiElementFromLineNumber(parameters.editor.project!!, cssClassNameForFile.filePath, 5)

                        val lookupElement = if (psiElement != null) LookupElementBuilder.createWithSmartPointer(
                            x,
                            psiElement
                        ) else LookupElementBuilder.create(x)
                        result.addElement(
                            lookupElement
                                .withIcon(AllIcons.Xml.Css_class)
                                .withTypeText(cssClassNameForFile.fileName, true)
                        )
                    }
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

    private fun findPsiElementFromLineNumber(
        project: Project,
        filePath: String,
        lineNumber: Int,
        columnNumber: Int = 0
    ): PsiElement? {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (virtualFile == null) {
            println("File not found: $filePath")
            return null
        }

        val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return null

        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val offset = lineStartOffset + columnNumber

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null

        return psiFile.findElementAt(offset)
    }
}