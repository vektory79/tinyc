package me.vektory79.tinyc

import me.vektory79.tinyc.abi.FileInfoSource
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

val R: ResourceBundle = ResourceBundle.getBundle("me.vektory79.tinyc.Messages")

operator fun ResourceBundle.get(key: String): String {
    return getString(key)
}

operator fun ResourceBundle.get(key: String, arg1: Any?, vararg args: Any?): String {
    return String.format(this[key], arg1, *args)
}

class TinycErrorException(val errorCode: Int, message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

fun path(first: String, vararg more: String): Path = Paths.get(first, *more)
fun path(first: URI): Path = Paths.get(first)

fun Path.createDirs() {
    try {
        Files.createDirectories(this)
    } catch (e: IOException) {
        throw TinycErrorException(2, R["CreateDirError", this])
    }
}

val Path.exists
    get() = Files.exists(this)

operator fun Path.div(value: Path): Path = this.resolve(value)
operator fun Path.div(value: String): Path = this.resolve(value)

fun compile(config: Tinyc, forCompile: List<FileInfoSource>) {
    val sourceList = config.buildDir / "source-list.lst"
    if (sourceList.exists) {
        Files.delete(sourceList)
    }

    if (forCompile.isEmpty()) {
        return
    }

    Files.createDirectories(sourceList.parent)
    val file = BufferedWriter(FileWriter(sourceList.toFile(), StandardCharsets.UTF_8))
    file.use {
        forCompile.forEach {
            file.append(it.fullPath.toString()).append('\n')
        }
    }

    val exitValue = ProcessExecutor()
        .command(
            config.javac.toString(),
            "@$sourceList",
            "-sourcepath",
            config.sourceRoot.toString(),
            "-d",
            config.destDir.toString(),
            "-g",
            "-target",
            "11",
            "-encoding",
            "UTF-8",
            * if (config.classpath != null) arrayOf("-classpath", "${config.destDir}:${config.classpath}") else arrayOf(
                "-classpath",
                config.destDir.toString()
            )
        )
        .redirectOutput(Slf4jStream.of(Tinyc::class.java).asInfo())
        .readOutput(true)
        .execute()
        .exitValue

    if (exitValue != 0) {
        throw TinycErrorException(1, R["CompilationError"])
    }
}
