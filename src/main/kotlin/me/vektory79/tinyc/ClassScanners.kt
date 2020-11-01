package me.vektory79.tinyc

import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.*
import org.objectweb.asm.signature.SignatureVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

interface AbiScannerCallback {
    fun sourceFile(name: Path?)

    fun clazz(
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    )

    fun field(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?
    )

    fun method(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exception: Array<out String>?
    )
}

fun scanAbi(classFilePath: Path, callback: AbiScannerCallback) {
    val inputStream = Files.newInputStream(classFilePath)
    inputStream.use {
        val reader = ClassReader(inputStream)
        val sourceFinder = ClassAbiScanner(classFilePath, callback)
        reader.accept(sourceFinder, ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES)
    }
}

class ClassAbiScanner(
    private val classFilePath: Path,
    private val callback: AbiScannerCallback
) : ClassVisitor(Opcodes.ASM9) {
    override fun visitSource(source: String?, debug: String?) {
        if (source != null) {
            callback.sourceFile(Paths.get(source))
        } else {
            throw TinycErrorException(4, R["DebugInfoMissing", classFilePath.toString()])
        }
    }

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        if (access and Opcodes.ACC_PRIVATE == 0) return
        callback.clazz(access, name, signature, superName, interfaces)
    }

    // TODO: Do implement support for the sealed classes
    override fun visitPermittedSubclass(permittedSubclass: String?) {
    }

    // TODO: Do implement support for the records
    override fun visitRecordComponent(
        name: String?,
        descriptor: String?,
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
    ): FieldVisitor? {
        if (access and Opcodes.ACC_PRIVATE > 0) return null
        val constMask = Opcodes.ACC_STATIC and Opcodes.ACC_FINAL
        if (access and constMask == constMask && value != null) return null
        callback.field(access, name, descriptor, signature)
        return null
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor? {
        if (access and Opcodes.ACC_PRIVATE > 0) return null
        callback.method(access, name, descriptor, signature, exceptions)
        return null
    }
}

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
        reader.accept(sourceFinder, ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES)
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