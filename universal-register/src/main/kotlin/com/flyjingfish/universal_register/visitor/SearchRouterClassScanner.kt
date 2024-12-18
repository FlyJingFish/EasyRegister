package com.flyjingfish.universal_register.visitor

import com.flyjingfish.universal_register.utils.RouterClassUtils
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

class SearchRouterClassScanner(private val moduleName:String) : ClassVisitor(Opcodes.ASM9) {
    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<String>?
    ) {
        super.visit(version, access, name, signature, superName, interfaces)
        RouterClassUtils.addClass(moduleName,name,superName)
    }
}