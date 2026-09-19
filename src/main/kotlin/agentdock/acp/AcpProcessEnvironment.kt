package agentdock.acp

import com.intellij.util.EnvironmentUtil
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

internal object AcpProcessEnvironment {
    private const val SHELL_ENV_TIMEOUT_SECONDS = 5L
    private const val SHELL_ENV_BEGIN_MARKER = "__AGENT_DOCK_ENV_BEGIN__"
    private const val SHELL_ENV_END_MARKER = "__AGENT_DOCK_ENV_END__"
    private val transientShellVariables = setOf("_", "PWD", "OLDPWD", "SHLVL")

    private val unixShellEnvironment: Map<String, String>? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadUnixShellEnvironment()
    }

    fun baseEnvironment(): Map<String, String> {
        val platformEnvironment = EnvironmentUtil.getEnvironmentMap()
        if (AcpExecutionMode.isWindowsHost()) return enrichedEnvironment(platformEnvironment)

        val shellEnvironment = unixShellEnvironment ?: return enrichedEnvironment(platformEnvironment)
        return mergedBaseEnvironment(
            current = platformEnvironment,
            shell = shellEnvironment
        )
    }

    fun withPrependedPathEntries(extraEntries: List<File>): Map<String, String> =
        withPrependedPathEntries(baseEnvironment(), extraEntries)

    fun applyTo(builder: ProcessBuilder, extraPathEntries: List<File> = emptyList()) {
        val environment = builder.environment()
        val base = baseEnvironment()
        val merged = withPrependedPathEntries(
            source = environment,
            extraEntries = extraPathEntries,
            fallbackPath = base[pathKey(base)].orEmpty()
        )
        environment.putAll(merged)
    }

    internal fun enrichedEnvironment(
        source: Map<String, String>,
        commonExecutableDirs: List<File> = commonExecutableDirectories()
    ): Map<String, String> {
        return enrichedEnvironment(
            source = source,
            suffixPath = "",
            commonExecutableDirs = commonExecutableDirs
        )
    }

    internal fun mergedBaseEnvironment(
        current: Map<String, String>,
        shell: Map<String, String>,
        commonExecutableDirs: List<File> = commonExecutableDirectories()
    ): Map<String, String> {
        val env = current.toMutableMap()
        shell.forEach { (key, value) ->
            if (value.isNotBlank() && env.keys.none { it.equals(key, ignoreCase = true) }) {
                env[key] = value
            }
        }
        val currentPath = env[pathKey(env)].orEmpty()
        val shellPath = shell[pathKey(shell)].orEmpty()
        return enrichedEnvironment(
            source = env.apply { this[pathKey(this)] = shellPath },
            suffixPath = currentPath,
            commonExecutableDirs = commonExecutableDirs
        )
    }

    private fun loadUnixShellEnvironment(): Map<String, String>? {
        val shell = sequenceOf(
            System.getenv("SHELL"),
            EnvironmentUtil.getValue("SHELL"),
            "/bin/sh"
        )
            .filterNotNull()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() && File(it).canExecute() }
            ?: return null
        val envCommand = sequenceOf("/usr/bin/env", "/bin/env")
            .firstOrNull { File(it).canExecute() }
            ?: return null
        val command = "printf '\\0%s\\0' '$SHELL_ENV_BEGIN_MARKER'; " +
            "$envCommand -0; printf '%s\\0' '$SHELL_ENV_END_MARKER'"

        return runCatching {
            val process = ProcessBuilder(shell, "-l", "-i", "-c", command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val output = ByteArrayOutputStream()
            val outputThread = Thread {
                process.inputStream.use { it.copyTo(output) }
            }.apply {
                isDaemon = true
                name = "agentdock-shell-environment"
                start()
            }

            if (!process.waitFor(SHELL_ENV_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.inputStream.close()
                outputThread.join(1_000L)
                return@runCatching null
            }
            outputThread.join(1_000L)
            if (process.exitValue() != 0 || outputThread.isAlive) return@runCatching null

            parseShellEnvironmentOutput(output.toString(StandardCharsets.UTF_8))
                .takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    internal fun parseShellEnvironmentOutput(output: String): Map<String, String> {
        val entries = output.split('\u0000')
        val start = entries.indexOf(SHELL_ENV_BEGIN_MARKER)
        if (start < 0) return emptyMap()
        val end = entries.subList(start + 1, entries.size)
            .indexOf(SHELL_ENV_END_MARKER)
            .takeIf { it >= 0 }
            ?.plus(start + 1)
            ?: -1
        if (end < 0) return emptyMap()

        return buildMap {
            entries.subList(start + 1, end).forEach { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0) return@forEach
                val name = entry.substring(0, separator)
                val value = entry.substring(separator + 1)
                if (name !in transientShellVariables &&
                    EnvironmentUtil.isValidName(name) && EnvironmentUtil.isValidValue(value)
                ) {
                    put(name, value)
                }
            }
        }
    }

    private fun enrichedEnvironment(
        source: Map<String, String>,
        suffixPath: String,
        commonExecutableDirs: List<File>
    ): Map<String, String> {
        val env = source.toMutableMap()
        val key = pathKey(env)
        val merged = mergedPath(
            prefixEntries = emptyList(),
            existingPath = env[key].orEmpty(),
            suffixPath = suffixPath,
            suffixEntries = commonExecutableDirs
        )
        if (merged.isNotBlank()) {
            env[key] = merged
        }
        return env
    }

    internal fun withPrependedPathEntries(
        source: Map<String, String>,
        extraEntries: List<File>
    ): Map<String, String> =
        withPrependedPathEntries(source, extraEntries, fallbackPath = "")

    private fun withPrependedPathEntries(
        source: Map<String, String>,
        extraEntries: List<File>,
        fallbackPath: String
    ): Map<String, String> {
        val env = source.toMutableMap()
        val key = pathKey(env)
        val merged = mergedPath(
            prefixEntries = extraEntries,
            existingPath = env[key].orEmpty(),
            suffixPath = fallbackPath,
            suffixEntries = emptyList()
        )
        if (merged.isNotBlank()) {
            env[key] = merged
        }
        return env
    }

    internal fun pathKey(env: Map<String, String>): String =
        env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"

    internal fun mergedPath(
        prefixEntries: List<File>,
        existingPath: String,
        suffixPath: String = "",
        suffixEntries: List<File>
    ): String {
        val entries = mutableListOf<String>()

        fun addEntry(path: String) {
            val trimmed = path.trim()
            if (trimmed.isNotBlank()) entries += trimmed
        }

        prefixEntries
            .filter { it.isDirectory }
            .forEach { addEntry(it.absolutePath) }

        existingPath
            .split(File.pathSeparator)
            .forEach(::addEntry)

        suffixPath
            .split(File.pathSeparator)
            .forEach(::addEntry)

        suffixEntries
            .filter { it.isDirectory }
            .forEach { addEntry(it.absolutePath) }

        val seen = linkedSetOf<String>()
        return entries
            .filter { seen.add(normalizePathEntry(it)) }
            .joinToString(File.pathSeparator)
    }

    private fun commonExecutableDirectories(): List<File> {
        if (AcpExecutionMode.isWindowsHost()) return emptyList()
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
        return buildList {
            home?.let {
                add(File(it, ".local/bin"))
                add(File(it, ".docker/bin"))
            }
            add(File("/opt/homebrew/bin"))
            add(File("/usr/local/bin"))
            add(File("/usr/bin"))
            add(File("/bin"))
            add(File("/usr/sbin"))
            add(File("/sbin"))
            add(File("/Applications/Docker.app/Contents/Resources/bin"))
        }
    }

    private fun normalizePathEntry(path: String): String =
        File(path).absoluteFile.normalize().path
}
