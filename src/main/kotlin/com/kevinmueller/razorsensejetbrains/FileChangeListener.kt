package com.kevinmueller.razorsensejetbrains

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class FileChangeListener : AsyncFileListener {
    override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
        val relevantEvents = events.filter { event ->
            event.file?.extension == "css" || event.file?.extension == "html" || event.file?.extension == "cshtml"
        }

        if (relevantEvents.isEmpty()) {
            return null
        }

        val activeProject = ProjectUtil.getActiveProject()
        val service = activeProject?.service<CssCompletionService>()
        service ?: return null 

        return object : AsyncFileListener.ChangeApplier {
            override fun beforeVfsChange() {
                // Actions to perform before the VFS change is applied
            }

            override fun afterVfsChange() {
                //TODO: optimize this? no need to update everything, only changed file.
                service.loadCompletions()
            }
        }
    }

}
