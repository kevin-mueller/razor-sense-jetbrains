package com.kevinmueller.razorsensejetbrains

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.icons.AllIcons.Icons
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
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
            val projectSourceDirectory = completion.key.substringBeforeLast("/")

            //TODO: this matches too much...
            if (parameters.originalFile.virtualFile.path.contains(projectSourceDirectory)) {
                for (cssClassNameForFile in completion.value.AllCssClassNames) {
                    for (x in cssClassNameForFile.AllCssClassNames) {
                        val psiElement =
                            findPsiElementFromLineNumber(parameters.editor.project!!, cssClassNameForFile.FilePath, 5)

                        val lookupElement = if (psiElement != null) LookupElementBuilder.createWithSmartPointer(
                            x,
                            psiElement
                        ) else LookupElementBuilder.create(x)
                        result.addElement(
                            lookupElement
                                .withTypeText(cssClassNameForFile.FileName, AllIcons.Xml.Css_class, true)
                        )
                    }
                }
            }
        }
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