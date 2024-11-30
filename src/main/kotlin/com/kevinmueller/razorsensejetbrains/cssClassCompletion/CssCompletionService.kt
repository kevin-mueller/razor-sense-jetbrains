package com.kevinmueller.razorsensejetbrains.cssClassCompletion

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.jetbrains.rdclient.util.idea.toIOFile
import com.jetbrains.rider.projectView.workspace.*
import org.jsoup.Jsoup
import java.io.File
import java.net.URI
import kotlin.system.measureTimeMillis

@Service(Service.Level.PROJECT)
class CssCompletionService(private val solutionProject: Project) {
    var cssCompletionItemsByProjectPath: Map<String, CssClasses> = emptyMap()

    fun loadCompletions() {
        val totalCssClassNames: Int
        val executionTime = measureTimeMillis {
            totalCssClassNames = updateCompletions()
        }

        var totalFiles = 0
        for (item in cssCompletionItemsByProjectPath) {
            totalFiles += item.value.getTotalFileCount()
        }

        StatusBar.Info.set(
            "Parsed $totalCssClassNames css classes from $totalFiles files in $executionTime ms", solutionProject
        )
    }

    private fun updateCompletions(): Int {
        val projects = WorkspaceModel.getInstance(solutionProject).findProjects()

        val cssCompletionItemsByProjectPath = mutableMapOf<String, CssClasses>()
        var totalCssClassNames = 0
        for (project in projects) {
            if (project.url == null) continue

            val localCssClassNames = getLocalCssClassNamesFromProject(project)

            val indexHtmlFileInfo = getIndexHtmlInfoFromProject(project)

            val remoteCssClassNames =
                getRemoteCssClassNamesFromUrls(indexHtmlFileInfo.referencedCssFilePaths)
            val allCssClassNames = localCssClassNames.union(remoteCssClassNames)

            totalCssClassNames += allCssClassNames.flatMap { x -> x.cssClassNames }.count()
            val cssClasses = CssClasses(
                allCssClassNames, indexHtmlFileInfo
            )

            cssCompletionItemsByProjectPath[project.url!!.presentableUrl] = cssClasses
        }
        this.cssCompletionItemsByProjectPath = cssCompletionItemsByProjectPath

        return totalCssClassNames
    }

    private fun getIndexHtmlInfoFromProject(project: ProjectModelEntity): IndexHtmlFileInfo {
        if (project.url == null) return IndexHtmlFileInfo(emptyList(), false)

        var foundIndexHtmlFile = false

        val referencedCssFilePaths = mutableListOf<String>()
        VfsUtil.collectChildrenRecursively(project.getVirtualFileAsParent()!!).filter {
            (it.extension == "html" || it.extension == "cshtml") && it.path.contains("wwwroot") && !isInArtifactFolder(
                it
            )
        }.forEach {
            foundIndexHtmlFile = true

            val parsed = Jsoup.parse(it.toIOFile())
            val allLinkTags = parsed.select("link")

            for (linkTag in allLinkTags) {
                if (!linkTag.hasAttr("href"))
                    continue

                val href = linkTag.attribute("href")
                if (href?.value?.endsWith(".css") == true) {
                    referencedCssFilePaths.add(href.value)
                }
            }
        }

        for (projectDependency in getProjectDependencies(project)) referencedCssFilePaths.addAll(
            getIndexHtmlInfoFromProject(projectDependency).referencedCssFilePaths
        )

        return IndexHtmlFileInfo(referencedCssFilePaths, foundIndexHtmlFile)
    }

    private fun getLocalCssClassNamesFromProject(project: ProjectModelEntity): MutableList<CssClassesWithFileReference> {
        val cssFiles = getAllCssFilesFromProject(project)
        return getCssClassNamesFromCssFiles(cssFiles).toMutableList()
    }

    private fun getRemoteCssClassNamesFromUrls(
        referencedCssFilePaths: List<String>
    ): List<CssClassesWithFileReference> {
        val cssFiles = referencedCssFilePaths.filter { x -> x.startsWith("https://") || x.startsWith("http://") }
        return getCssClassNamesFromCssFiles(cssFiles)
    }

    private fun getAllCssFilesFromProject(project: ProjectModelEntity): List<String> {
        if (project.url == null) return emptyList()

        val cssFiles = mutableListOf<String>()
        VfsUtil.collectChildrenRecursively(
            project.parentEntity?.getVirtualFileAsContentRoot()!!
        ).filter { it.extension == "css" && !isInArtifactFolder(it) }.forEach {
            cssFiles.add(it.path)
        }

        val packageDependencies = getPackageDependencies(project)
        if (packageDependencies.isNotEmpty()) {
            cssFiles.addAll(getCssFilesFromPackageDependencies(packageDependencies))
        }

        val projectDependencies = getProjectDependencies(project)
        if (projectDependencies.isNotEmpty()) {
            for (projectDependency in projectDependencies) cssFiles.addAll(getAllCssFilesFromProject(projectDependency))
        }

        return cssFiles
    }

    private fun getCssClassNamesFromCssFiles(cssFilePaths: List<String>): List<CssClassesWithFileReference> {
        val cssClassNames = mutableListOf<CssClassesWithFileReference>()
        for (cssFile in cssFilePaths) {
            if (cssClassNames.any { x -> x.fileName == cssFile }) {
                continue
            }

            var cssContent = ""
            try {
                cssContent =
                    if (cssFile.startsWith("https://") || cssFile.startsWith("http://")) URI(cssFile).toURL().readText()
                    else File(cssFile).readText()
            } catch (_: Exception) {
                // ignored
            }

            val classPattern = Regex("\\.([a-zA-Z0-9_-]+)\\s*\\{")

            val classNames = classPattern.findAll(cssContent).map { it.groupValues[1] }.toSet().sorted().toSet()

            val fileName = File(cssFile).name
            cssClassNames.add(
                CssClassesWithFileReference(
                    classNames,
                    fileName,
                    cssFile,
                    fileName.endsWith(".razor.css")
                )
            )
        }

        return cssClassNames
    }

    private fun getProjectDependencies(project: ProjectModelEntity): List<ProjectModelEntity> {
        return getTargetFrameworkFolder(project)?.childrenEntities?.firstOrNull { p -> p.isProjectsFolder() }?.childrenEntities
            ?: emptyList()
    }

    private fun getPackageDependencies(project: ProjectModelEntity): List<ProjectModelEntity> {
        return getTargetFrameworkFolder(project)?.childrenEntities?.firstOrNull { p -> p.isPackagesFolder() }?.childrenEntities
            ?: emptyList()
    }

    private fun getTargetFrameworkFolder(project: ProjectModelEntity): ProjectModelEntity? {
        val dependencies = project.childrenEntities.firstOrNull { p -> p.isDependenciesFolder() }

        return dependencies?.childrenEntities?.firstOrNull { p -> p.isTargetFrameworkFolder() }
    }

    private fun getCssFilesFromPackageDependencies(packageDependencies: List<ProjectModelEntity>): List<String> {
        val cssFiles = mutableListOf<String>()
        for (packageDependency in packageDependencies) {
            // cannot use VFS, because its null for package dependencies
            var cssFilesFromPackage = packageDependency.getFile()?.walkTopDown()?.filter { x -> x.extension == "css" }
                ?.map { x -> x.invariantSeparatorsPath }?.toList()

            if (cssFilesFromPackage == null)
                cssFilesFromPackage = emptyList()

            cssFiles.addAll(cssFilesFromPackage)
        }

        return cssFiles
    }

    private fun isInArtifactFolder(file: VirtualFile): Boolean {
        return file.path.contains("/bin/") || file.path.contains("/obj/")
    }
}

class CssClasses(
    private val cssClassesFileReferences: Set<CssClassesWithFileReference>,
    private val indexHtmlFileInfo: IndexHtmlFileInfo
) {
    fun getReferencedCssClasses(fileName: String?): Set<CssClassesWithFileReference> {
        if (indexHtmlFileInfo.hasIndexHtmlFile) {
            val result = mutableSetOf<CssClassesWithFileReference>()
            for (cssClassNameFileReference in cssClassesFileReferences) {
                if (cssClassNameFileReference.isScopedCssFile && fileName != null) {
                    if (cssClassNameFileReference.fileName.startsWith(fileName)) {
                        result.add(cssClassNameFileReference)
                    }
                } else {
                    if (indexHtmlFileInfo.referencedCssFilePaths.any { relativeFilePath ->
                            cssClassNameFileReference.filePath.contains(
                                relativeFilePath
                            )
                        }) {
                        result.add(cssClassNameFileReference)
                    }
                }
            }
            return result
        } else {
            return cssClassesFileReferences.toSet()
        }
    }

    fun getTotalFileCount(): Int {
        return cssClassesFileReferences.distinctBy { x -> x.filePath }.count()
    }
}

class CssClassesWithFileReference(
    val cssClassNames: Set<String>,
    val fileName: String,
    val filePath: String,
    val isScopedCssFile: Boolean
)

class IndexHtmlFileInfo(val referencedCssFilePaths: List<String>, val hasIndexHtmlFile: Boolean)