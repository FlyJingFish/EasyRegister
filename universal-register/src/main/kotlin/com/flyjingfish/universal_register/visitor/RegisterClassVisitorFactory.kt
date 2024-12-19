package com.flyjingfish.universal_register.visitor

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import com.flyjingfish.universal_register.utils.RegisterClassUtils
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassVisitor

abstract class MyClassVisitorFactory : AsmClassVisitorFactory<MyParameters> {
    override fun createClassVisitor(
        classContext: ClassContext,
        nextVisitor: ClassVisitor
    ): ClassVisitor {
        // 创建自定义的 ClassVisitor
        return RegisterClassVisitor(nextVisitor)
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        // 指定哪些类可以被修改，例如过滤某些包名
        return RegisterClassUtils.isWovenClass(classData.className)
    }
}

// 自定义参数，用于传递配置
interface MyParameters : InstrumentationParameters {
    @get:Input
    val myConfig: Property<String>
}
