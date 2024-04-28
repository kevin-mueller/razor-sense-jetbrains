package com.kevinmueller.razorsensejetbrains

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.patterns.PatternCondition
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ProcessingContext


/**
 * Pattern which only accepts PsiElements, which either are a string literal or a comment of the current language.
 * It also accepts if the element's parent matches these conditions.
 *
 * @author jansorg
 */
internal class RazorCompletionPattern : PatternCondition<PsiElement>("razorClassPattern()") {
    override fun accepts(psi: PsiElement, context: ProcessingContext): Boolean {
        val language: Language = PsiUtilCore.findLanguageFromElement(psi)
        val definition = LanguageParserDefinitions.INSTANCE.forLanguage(language) ?: return false

        // suggest completions in string and comment literals
        val tokens = TokenSet.orSet(
            definition.stringLiteralElements,
            definition.commentTokens
        )

        val node = psi.node ?: return false

        return tokens.contains(node.elementType)
    }
}