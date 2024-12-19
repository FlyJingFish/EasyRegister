package io.github.flyjingfish.easy_register.plugin

import io.github.flyjingfish.easy_register.utils.hintCleanFile
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class HintCleanTask : DefaultTask() {
    @TaskAction
    fun taskAction() {
        if (File(hintCleanFile(project)).exists()){
            throw IllegalArgumentException("The config json file has changed, please clean project")
        }
    }
}