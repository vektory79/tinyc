package me.vektory79.tinyc.scanners

import org.objectweb.asm.*
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import java.nio.file.Files
import java.nio.file.Path

interface UsageScannerCallback {
    fun baseType(className: String)
    fun typeUsage(className: String)
    fun field(owner: String, name: String)
    fun method(owner: String, name: String, descriptor: String)
}

fun classUsageScan(classFilePath: Path, callback: UsageScannerCallback) {
    val inputStream = Files.newInputStream(classFilePath)
    inputStream.use {
        val reader = ClassReader(inputStream)
        val sourceFinder = ClassUsageScanner(callback)
        reader.accept(sourceFinder, ClassReader.EXPAND_FRAMES)
    }
}

class ClassUsageScanner(private val callback: UsageScannerCallback) : ClassVisitor(Opcodes.ASM9) {
    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        SignatureUsageScanner.parse(signature, callback)
        if (superName != null) {
            callback.baseType(superName)
        }
        interfaces?.forEach {
            callback.baseType(it)
        }
    }

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        SignatureUsageScanner.parse(descriptor, callback)
        return null
    }

    override fun visitTypeAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? {
        SignatureUsageScanner.parse(descriptor, callback)
        return null
    }

    // TODO: Do implement support for the records
    override fun visitRecordComponent(
        name: String,
        descriptor: String,
        signature: String?
    ): RecordComponentVisitor? {
        return null
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
    ): FieldVisitor {
        SignatureUsageScanner.parse(descriptor, callback)
        SignatureUsageScanner.parse(signature, callback)
        return FieldUsageScanner(callback)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        SignatureUsageScanner.parse(descriptor, callback)
        SignatureUsageScanner.parse(signature, callback)
        exceptions?.forEach { callback.typeUsage(it) }
        return MethodUsageScanner(callback)
    }
}

class FieldUsageScanner(private val callback: UsageScannerCallback) : FieldVisitor(Opcodes.ASM9) {
    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        SignatureUsageScanner.parse(descriptor, callback)
        return null
    }

    override fun visitTypeAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? {
        SignatureUsageScanner.parse(descriptor, callback)
        return null
    }
}

class MethodUsageScanner(private val callback: UsageScannerCallback) : MethodVisitor(Opcodes.ASM9) {

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        SignatureUsageScanner.parse(descriptor, callback)
        return null
    }

    override fun visitTypeAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? = visitTryCatchAnnotation(typeRef, typePath, descriptor, visible)

    override fun visitParameterAnnotation(
        parameter: Int,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? {
        SignatureUsageScanner.parse(descriptor, callback)
        return null
    }

    override fun visitTypeInsn(opcode: Int, type: String) = callback.typeUsage(type)

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        SignatureUsageScanner.parse(descriptor, callback)
        callback.typeUsage(owner)
        callback.field(owner, name)
    }

    override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean
    ) {
        SignatureUsageScanner.parse(descriptor, callback)
        callback.typeUsage(owner)
        callback.method(owner, name, descriptor)
    }

    override fun visitInvokeDynamicInsn(
        name: String,
        descriptor: String,
        bootstrapMethodHandle: Handle,
        vararg bootstrapMethodArguments: Any
    ) {
        SignatureUsageScanner.parse(descriptor, callback)
        SignatureUsageScanner.parse(bootstrapMethodHandle.desc, callback)
        callback.typeUsage(bootstrapMethodHandle.owner)
        callback.method(
            bootstrapMethodHandle.owner,
            bootstrapMethodHandle.name,
            bootstrapMethodHandle.desc
        )
        callback.field(
            bootstrapMethodHandle.owner,
            bootstrapMethodHandle.name
        )
    }

    override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) =
        SignatureUsageScanner.parse(descriptor, callback)

    override fun visitInsnAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? = visitTryCatchAnnotation(typeRef, typePath, descriptor, visible)

    override fun visitTryCatchAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? {
        SignatureUsageScanner.parse(descriptor, callback)
        return null
    }

    override fun visitLocalVariable(
        name: String?,
        descriptor: String?,
        signature: String?,
        start: Label?,
        end: Label?,
        index: Int
    ) {
        SignatureUsageScanner.parse(descriptor, callback)
        SignatureUsageScanner.parse(signature, callback)
    }

    override fun visitLocalVariableAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        start: Array<out Label>?,
        end: Array<out Label>?,
        index: IntArray?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? {
        SignatureUsageScanner.parse(descriptor, callback)
        return null
    }
}

class SignatureUsageScanner(private val callback: UsageScannerCallback) : SignatureVisitor(Opcodes.ASM9) {
    override fun visitClassBound(): SignatureVisitor = this

    override fun visitInterfaceBound(): SignatureVisitor = this

    override fun visitSuperclass(): SignatureVisitor = this

    override fun visitInterface(): SignatureVisitor = this

    override fun visitParameterType(): SignatureVisitor = this

    override fun visitReturnType(): SignatureVisitor = this

    override fun visitExceptionType(): SignatureVisitor = this

    override fun visitArrayType(): SignatureVisitor = this

    override fun visitClassType(name: String?) {
        if (name != null) {
            callback.typeUsage(name)
        }
    }

    companion object {
        fun parse(signature: String?, callback: UsageScannerCallback) {
            if (signature != null) {
                SignatureReader(signature).accept(SignatureUsageScanner(callback))
            }
        }
    }
}