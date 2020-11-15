package me.vektory79.tinyc.abi

import me.vektory79.tinyc.R
import me.vektory79.tinyc.TinycErrorException
import me.vektory79.tinyc.get
import org.objectweb.asm.Opcodes

enum class Visibility {
    PRIVATE,
    PROTECTED,
    INTERNAL,
    PUBLIC;

    infix fun significantChangeIn(other: Visibility): Boolean =
        (this != PRIVATE && other == PRIVATE)
                || (this == PUBLIC && other != PUBLIC)
                || ((this == INTERNAL || this == PROTECTED) && (other == INTERNAL || other == PROTECTED) && this != other)

    companion object {
        fun fromAccess(access: Int): Visibility {
            return when (access and (Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED or Opcodes.ACC_PUBLIC)) {
                0 -> INTERNAL
                Opcodes.ACC_PRIVATE -> PRIVATE
                Opcodes.ACC_PROTECTED -> PROTECTED
                Opcodes.ACC_PUBLIC -> PUBLIC
                // TODO: Exception message should have class or/and member name
                else -> throw TinycErrorException(
                    7,
                    R["IncorrectAccessFlagsError", Integer.toBinaryString(access).padStart(Int.SIZE_BITS, '0')]
                )
            }
        }
    }
}