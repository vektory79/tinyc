package me.vektory79.tinyc.abi

import java.io.Serializable
import java.nio.file.Path
import java.util.stream.Stream

class Package(val index: FileIndex, val path: Path) : Serializable {
    internal val sources = HashMap<Path, FileInfoSource>()
    internal val compiled = HashMap<Path, FileInfoCompiled>()

    fun addSource(name: Path) {
        sources[name] = FileInfoSource(this, name)
    }

    fun getSource(name: Path): FileInfoSource? = sources[name]

    fun walkSources(): Stream<FileInfoSource> = sources.entries.stream().map { it.value }

    fun addCompiled(value: FileInfoCompiled) {
        compiled[value.name] = value
    }

    fun getCompiled(name: Path): FileInfoCompiled? = compiled[name]

    fun walkCompiled(): Stream<FileInfoCompiled> = compiled.entries.stream().map { it.value }
}