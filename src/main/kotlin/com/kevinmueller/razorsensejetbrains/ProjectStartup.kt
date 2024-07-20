package com.kevinmueller.razorsensejetbrains

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity


class ProjectStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = project.service<CssCompletionService>()

        service.loadCompletions()
    }
}