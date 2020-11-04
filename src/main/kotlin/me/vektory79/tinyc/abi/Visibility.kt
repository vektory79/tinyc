package me.vektory79.tinyc.abi

import me.vektory79.tinyc.R
import me.vektory79.tinyc.TinycErrorException
import me.vektory79.tinyc.get
import org.objectweb.asm.Opcodes

enum class Visibility(private val scope: Int) {
    PRIVATE(0),
    PROTECTED(1),
    INTERNAL(2),
    PUBLIC(3);

    infix fun lessThan(other: Visibility): Boolean {
        return this.scope < other.scope
    }

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