package com.flyjingfish.universal_register.plugin

import com.flyjingfish.universal_register.utils.hintCleanFile
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