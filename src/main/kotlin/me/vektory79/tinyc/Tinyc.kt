@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package me.vektory79.tinyc

import me.vektory79.tinyc.abi.FileIndex
import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.logging.LogManager

/**
 * The main class with parsed console parameters and the processing logic.
 */
@Command(
    mixinStandardHelpOptions = true,
    resourceBundle = "me.vektory79.tinyc.Messages",
    version = ["1.0.0"]
)
class Tinyc : Callable<Int> {
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
    
    internal var compiledFilesPhase1 = 0
    internal var compiledFilesPhase2 = 0

    /**
     * The main processing function, tha do all the work.
     */
    override fun call(): Int {
        try {
            checkEnvironment()

            // Scan all sources
            val index = FileIndex.createNew(sourceRoot, destDir)
            val forCompile1 = index.compileListPhase1()
            compile(this, forCompile1)

            println(R["CompilationPhase1", forCompile1.size])
            compiledFilesPhase1 = forCompile1.size

            val forCompile2 = index.compileListPhase2(forCompile1)
            compile(this, forCompile2)

            println(R["CompilationPhase2", forCompile2.size])
            compiledFilesPhase2 = forCompile2.size
            println(R["CompilationResult", compiledFilesPhase1 + compiledFilesPhase2])
            return 0
        } catch (e: TinycErrorException) {
            // If any error occurred, then print it and return respective error code
            if (e.cause != null) {
                LOG.error(e.message, e)
            } else {
                LOG.error(e.message)
            }
            return e.errorCode
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