package com.kevinmueller.razorsensejetbrains

import com.intellij.patterns.PatternCondition
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.xml.XmlAttributeImpl
import com.intellij.psi.xml.XmlToken
import com.intellij.util.ProcessingContext

internal class RazorCompletionPattern : PatternCondition<PsiElement>("razorClassPattern()") {
    override fun accepts(psi: PsiElement, context: ProcessingContext): Boolean {
        val node = psi.node as XmlToken
        val isStringLiteral = node.prevSibling.text == "\"" && node.nextSibling.text == "\""   
        
        if (!isStringLiteral)
            return false
        
        return (node.parent.parent as XmlAttributeImpl).name == "Class"
    }
}