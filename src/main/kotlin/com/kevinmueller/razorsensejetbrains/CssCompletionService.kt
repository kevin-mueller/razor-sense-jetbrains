package com.kevinmueller.razorsensejetbrains

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class CssCompletionService(private val project: Project) {
    fun getCompletions(): Array<String> {
        return arrayOf("test-1", "test-2")
    }
}