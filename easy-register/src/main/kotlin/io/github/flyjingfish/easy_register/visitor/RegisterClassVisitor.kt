package io.github.flyjingfish.easy_register.visitor

import io.github.flyjingfish.easy_register.utils.RegisterClassUtils
import io.github.flyjingfish.easy_register.utils.addPublic
import io.github.flyjingfish.easy_register.utils.getWovenClassName
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter

class RegisterClassVisitor(cv: ClassVisitor?) : ClassVisitor(Opcodes.ASM9, cv) {
    companion object{
        const val INVOKE_METHOD = "init"
    }
    private lateinit var className:String
    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        super.visit(version, access, name, signature, superName, interfaces)
        className = name
    }

    override fun visitMethod(
        access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?
    ): MethodVisitor {
        val wovenClass = RegisterClassUtils.getWovenClass(className,name,descriptor)
        val isCallClassMethod = RegisterClassUtils.isCallClassMethod(className,name,descriptor)
        val newAccess = if (isCallClassMethod){
            access.addPublic(true)
        }else{
            access
        }
        return if (wovenClass != null){
            val mv = super.visitMethod(newAccess, name, descriptor, signature, exceptions)
            MyMethodAdapter(mv, newAccess, name, descriptor,wovenClass.insertBefore)
        }else{
            super.visitMethod(newAccess, name, descriptor, signature, exceptions)
        }
//        else if (name == "register" && descriptor == "(Ljava/lang/String;)V"){
//            super.visitMethod(access.addPublic(true), name, descriptor, signature, exceptions)
//        }
    }

    inner class MyMethodAdapter(mv: MethodVisitor, access: Int, private val mName: String, private val mDesc: String,
                                private val insertBefore: Boolean) :
        AdviceAdapter(Opcodes.ASM9, mv, access, mName, mDesc) {
        override fun onMethodEnter() {
            super.onMethodEnter()
            if (insertBefore){
                insertCode()
            }
        }
        override fun visitInsn(opcode: Int) {
            if (!insertBefore && opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                insertCode()
            }
            super.visitInsn(opcode)
        }

        private fun insertCode(){
            val argTypes = Type.getArgumentTypes(mDesc)
            for ((index,_) in argTypes.withIndex()) {
                mv.visitVarInsn(Opcodes.ALOAD, index)
            }
            mv.visitMethodInsn(
                INVOKESTATIC,
                getWovenClassName(className,mName, mDesc),
                INVOKE_METHOD,
                mDesc,
                false
            )
        }
    }
}
