@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package me.vektory79.tinyc

import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.logging.LogManager

/**
 * The main class with parsed console parameters and the processing logic.
 */
@Command(
    mixinStandardHelpOptions = true,
    resourceBundle = "me.vektory79.tinyc.Messages",
    version = ["1.0.0"]
)
class Tinyc : Runnable {
    private companion object {
        private val LOG = LoggerFactory.getLogger(Tinyc::class.java)
        private const val DIR_PLACEHOLDER = "<directory>"
        private const val PATH_PLACEHOLDER = "<path>"
    }

    /**
     * The classpath that passing to the javac for source compilation.
     */
    @field:Option(names = ["-c", "--classpath"], paramLabel = PATH_PLACEHOLDER, descriptionKey = "classpath")
    var classpath: String? = null

    /**
     * The Java sources root directory.
     */
    @field:Option(names = ["-s", "--sourceroot"], paramLabel = DIR_PLACEHOLDER, descriptionKey = "sourceroot")
    var sourceRoot: Path = Paths.get(".").toAbsolutePath().normalize()

    @field:Option(names = ["-b", "--builddir"], paramLabel = DIR_PLACEHOLDER, descriptionKey = "builddir")
    private var _buildDir: Path? = null

    /**
     * The directory where to store build artifacts.
     */
    val buildDir: Path
        get() = _buildDir!!

    @field:Option(names = ["-d", "--destination"], paramLabel = DIR_PLACEHOLDER, descriptionKey = "destination")
    private var _destDir: Path? = null

    /**
     * The directory where to store compiled classes.
     */
    val destDir: Path
        get() = _destDir!!

    private var _javaHome: Path? = null
    val javaHome: Path
        get() = _javaHome!!

    private var _javac: Path? = null

    /**
     * Path to the javac executable file.
     */
    val javac: Path
        get() = _javac!!

    /**
     * The main processing function, tha do all the work.
     */
    override fun run() {
        try {
            checkEnvironment()

            // Scan all sources
            val javaFiles = SourceFileStructure(sourceRoot)
            javaFiles.rebuild()

            // Scan all already compiled class files
            val classFiles = ClassesFileStricture(destDir)
            classFiles.rebuild()
            // Associate already compiled class files with respective sources
            classFiles.associate(javaFiles)
            // Cleanup the orphaned class files and directories
            classFiles.cleanup()

            // Get the list of sources, that needs to be compiled
            val forCompile = javaFiles.forCompile()
            compile(this, forCompile)

            println(R["CompilationResult", forCompile.size])
        } catch (e: TinycErrorException) {
            // If any error occurred, then print it and return respective error code
            if (e.cause != null) {
                LOG.error(e.message, e)
            } else {
                LOG.error(e.message)
            }
            System.exit(e.errorCode)
        }
    }

    internal fun checkEnvironment(): Tinyc {
        _buildDir = _buildDir ?: Paths.get("..", "build").toAbsolutePath().normalize()
        _destDir = _destDir ?: buildDir.resolve("classes").toAbsolutePath().normalize()

        _javaHome = Paths.get(System.getenv("JAVA_HOME") ?: throw TinycErrorException(5, R["JavaHomeNotSet"]))
        _javac = javaHome.resolve("bin")
        _javac = if (SystemUtils.IS_OS_WINDOWS) javac.resolve("javac.exe") else javac.resolve("javac")

        if (!Files.exists(javac)) {
            throw TinycErrorException(6, R["JavacNotFound", javac])
        }

        if (!Files.exists(sourceRoot)) {
            throw TinycErrorException(3, R["IncorrectSourceDir", sourceRoot])
        }
        destDir.createDirs()

        initLog()
        return this
    }

    /**
     * Hack the logger for more readable output.
     */
    private fun initLog() {
        val logProperties = Thread.currentThread().contextClassLoader.getResourceAsStream("log.properties")
        logProperties?.use {
            LogManager.getLogManager().readConfiguration(logProperties)
        }
    }
}