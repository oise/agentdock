package agentdock.acp

import com.intellij.util.EnvironmentUtil
import java.io.File

internal object AcpProcessEnvironment {
    fun baseEnvironment(): Map<String, String> =
        enrichedEnvironment(runCatching { EnvironmentUtil.getEnvironmentMap() }.getOrElse { System.getenv() })

    internal fun enrichedEnvironment(source: Map<String, String>): Map<String, String> {
        val env = source.toMutableMap()
        val key = pathKey(env)
        val merged = mergedPath(
            existingPath = env[key].orEmpty(),
            extraEntries = commonExecutableDirectories()
        )
        if (merged.isNotBlank()) {
            env[key] = merged
        }
        return env
    }

    internal fun mergedPath(
        existingPath: String,
        extraEntries: List<File>
    ): String {
        val entries = mutableListOf<String>()
        fun addPath(path: String?) {
            path.orEmpty()
                .split(File.pathSeparator)
                .map { it.trim() }
                .filterTo(entries) { it.isNotBlank() }
        }

        addPath(existingPath)
        entries += extraEntries
            .filter { it.isDirectory }
            .map { it.absolutePath }

        val seen = linkedSetOf<String>()
        return entries
            .filter { seen.add(normalizePathEntry(it)) }
            .joinToString(File.pathSeparator)
    }

    internal fun pathKey(env: Map<String, String>): String =
        env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"

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
        runCatching { File(path).absoluteFile.normalize().path }.getOrDefault(path)
}
