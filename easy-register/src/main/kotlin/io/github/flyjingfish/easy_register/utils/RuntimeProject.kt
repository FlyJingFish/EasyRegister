package io.github.flyjingfish.easy_register.utils

import org.gradle.api.Project
import java.io.File
import java.io.Serializable

data class RuntimeProject(
    val buildDir: File,
    val rootProjectBuildDir: File,
    val layoutBuildDirectory: File,
    val name: String
): Serializable {
    companion object {
        fun get(project: Project): RuntimeProject {
            return RuntimeProject(
                buildDir = project.buildDir,
                rootProjectBuildDir = project.rootProject.buildDir,
                layoutBuildDirectory = project.layout.buildDirectory.asFile.get(),
                name = project.name
            )
        }

    }
}