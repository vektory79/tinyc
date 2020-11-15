package me.vektory79.tinyc.abi

import org.objectweb.asm.Opcodes

abstract class AbiElement<in T : AbiElement<T>>(
    val access: Int,
    val name: String,
    val signature: String?,
) {
    val visibility = Visibility.fromAccess(access)

    val static: Boolean
        get() = access and Opcodes.ACC_STATIC != 0

    open infix fun significantChangeIn(other: T?): Boolean =
        other == null
                || signature != other.signature
                || other.visibility significantChangeIn visibility
                || (access and Opcodes.ACC_ABSTRACT == 0 && other.access and Opcodes.ACC_ABSTRACT != 0)
                || (access and Opcodes.ACC_FINAL == 0 && other.access and Opcodes.ACC_FINAL != 0)
                || (access and Opcodes.ACC_STATIC != other.access and Opcodes.ACC_STATIC)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AbiElement<*>

        if (access != other.access) return false
        if (name != other.name) return false
        if (signature != other.signature) return false

        return true
    }

    override fun hashCode(): Int {
        var result = access
        result = 31 * result + name.hashCode()
        result = 31 * result + (signature?.hashCode() ?: 0)
        return result
    }

}