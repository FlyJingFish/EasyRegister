package com.flyjingfish.universal_register.utils

import com.flyjingfish.universal_register.bean.SearchClass
import com.flyjingfish.universal_register.bean.WovenClass
import com.flyjingfish.universal_register.config.RootStringConfig
import org.gradle.api.Project
import org.objectweb.asm.commons.Method
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern

object RegisterClassUtils {
    var enable = true
    var mode = RootStringConfig.MODE.defaultValue
    private val configJsonFileList = mutableListOf<File>()
    private val searchWovenClasses = mutableListOf<WovenClass>()
    private val searchClasses = mutableListOf<SearchClass>()
    private val wovenClasses = HashSet<String>()
    fun addClass(moduleName:String,className:String,superClassName:String?){
        for (searchClass in searchClasses) {
            val matchExtends = if (searchClass.extendsClass.isNotEmpty()){
                if (searchClass.extendsClass == superClassName){
                    1
                }else{
                    2
                }
            }else{
                0
            }

            val matchName = if (searchClass.regex.isNotEmpty()){
                val classnameArrayPattern: Pattern = Pattern.compile(searchClass.regex)
                val matcher: Matcher = classnameArrayPattern.matcher(slashToDot(className))
                if (matcher.find()){
                    1
                }else{
                    2
                }
            }else{
                0
            }

            if ((matchExtends == 0 && matchName == 1)||
                (matchName == 0 && matchExtends == 1)||
                (matchName == 1 && matchExtends == 1)){
                searchClass.addClass(moduleName, className)
            }

        }
    }

    fun clear(moduleName: String){
        for (searchWovenClass in searchWovenClasses) {
            searchWovenClass.clear(moduleName)
        }
    }

    fun getClasses():List<WovenClass>{
        return searchWovenClasses
    }

    fun addConfigJsonFile(file:File){
        configJsonFileList.add(file)
    }

    fun clearConfigJsonFile(){
        configJsonFileList.clear()
    }

    private fun exportHintFile(project: Project){
        val childProjects = project.childProjects
        if (childProjects.isEmpty()){
            return
        }
        childProjects.forEach { (_,value)->
            JsonUtils.exportConfigJson(value)
            exportHintFile(value)
        }
    }


    fun initConfig(project: Project){
        val searchCls = JsonUtils.getJson(configJsonFileList)
        var hadOldCount = 0
        for (searchCl in searchCls) {
            var hasOld = false
            for (searchWovenClass in searchWovenClasses) {
                if (searchCl == searchWovenClass){
                    hasOld = true
                }
            }
            if (hasOld){
                hadOldCount++
            }
        }
        if ((hadOldCount != searchCls.size && searchWovenClasses.isNotEmpty())){
            searchWovenClasses.clear()
            exportHintFile(project)
            throw IllegalArgumentException("The config json file has changed, please clean project")
        }
        if (searchWovenClasses.isEmpty()){
            searchWovenClasses.clear()
            wovenClasses.clear()
            searchClasses.clear()
            searchWovenClasses.addAll(searchCls)
            for (searchCl in searchCls) {
                wovenClasses.add(searchCl.wovenClass)
                searchClasses.add(searchCl.searchClass)
            }
        }


    }

    fun initConfig(jsons:List<String>){
        val searchCls = JsonUtils.getJson4Str(jsons)
        if (searchWovenClasses.isEmpty()){
            searchWovenClasses.clear()
            wovenClasses.clear()
            searchClasses.clear()
            searchWovenClasses.addAll(searchCls)
            for (searchCl in searchCls) {
                wovenClasses.add(searchCl.wovenClass)
                searchClasses.add(searchCl.searchClass)
            }
        }
    }

    fun isWovenClass(className: String): Boolean {
        return className in wovenClasses
    }

    fun getWovenClass(className: String,methodName:String,methodDesc:String): WovenClass? {
        for (searchWovenClass in searchWovenClasses) {
            if (searchWovenClass.wovenClass == slashToDot(className)){
                val method = Method.getMethod(searchWovenClass.wovenMethod)
                if (method.name == methodName && method.descriptor == methodDesc){
                    return searchWovenClass
                }
            }
        }
        return null
    }

    fun isDebugMode(buildTypeName :String?,variantName :String):Boolean{
        return if (mode == "auto" || mode == "debug"){
            if (mode == "auto"){
                if (buildTypeName != null){
                    buildTypeName.lowercase() == "debug"
                }else{
                    variantName.lowercase().contains("debug")
                }
            }else{
                true
            }
        }else{
            false
        }
    }
}