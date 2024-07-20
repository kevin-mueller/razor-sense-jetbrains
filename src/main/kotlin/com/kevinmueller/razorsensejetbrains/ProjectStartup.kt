package com.kevinmueller.razorsensejetbrains

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import kotlin.system.measureTimeMillis


class ProjectStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = project.service<CssCompletionService>()

        val time = measureTimeMillis {
            service.loadCompletions()
        }

        VirtualFileManager.getInstance().addAsyncFileListener({ events ->
            var shouldUpdate = false;
            for (event in events) {
                if (event.file?.extension == "css" || event.file?.extension == "html" || event.file?.extension == "cshtml") {
                    shouldUpdate = true;
                    break;
                }
            }
            null
        }, {
            
        })
    }
}