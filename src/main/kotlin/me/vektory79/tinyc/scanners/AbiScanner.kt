package me.vektory79.tinyc.scanners

import me.vektory79.tinyc.R
import me.vektory79.tinyc.TinycErrorException
import me.vektory79.tinyc.get
import me.vektory79.tinyc.path
import org.objectweb.asm.*
import java.nio.file.Files
import java.nio.file.Path

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
        signature: String?,
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
            callback.sourceFile(path(source))
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
        if (access and Opcodes.ACC_PRIVATE != 0)
            return
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
        callback.field(access, name, descriptor, signature,)
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
