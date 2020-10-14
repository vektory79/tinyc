package me.vektory79.tinyc

import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>) : Unit = exitProcess(CommandLine(Tinyc()).setCommandName("tinyc").execute(*args))