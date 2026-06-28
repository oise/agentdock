package agentdock.acp

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AcpPlatformCompatibilityTest {
    @Test
    fun `local Linux and macOS use unix npm launch binaries`() = withOsName("Linux") {
        val adapter = npmAdapter()

        val launchPath = resolveAdapterLaunchPath("/tmp/agent", adapter, AcpExecutionTarget.LOCAL).orEmpty()
            .replace("\\", "/")

        assertEquals("/tmp/agent/node_modules/.bin/tool", launchPath)
        assertFalse(launchPath.endsWith(".cmd"))
    }

    @Test
    fun `JavaScript launch files use node on unix local hosts`() = withOsName("Mac OS X") {
        val adapter = AcpAdapterConfig.AdapterInfo(
            id = "tool",
            name = "Tool",
            distribution = AcpAdapterConfig.Distribution(
                type = AcpAdapterConfig.DistributionType.NPM,
                version = "latest",
                packageName = "tool"
            ),
            launchPath = "dist/index.js"
        )

        val command = buildAdapterLaunchCommand("/tmp/agent", adapter, "/tmp/project", AcpExecutionTarget.LOCAL)

        assertEquals("node", command.first())
    }

    @Test
    fun `process environment appends common executable dirs and removes duplicates`() {
        val existing = createTempDirectory("agentdock-existing").toFile()
        val common = createTempDirectory("agentdock-common").toFile()
        val path = listOf(existing.absolutePath, common.absolutePath).joinToString(File.pathSeparator)

        val env = AcpProcessEnvironment.enrichedEnvironment(
            source = mapOf("PATH" to path),
            commonExecutableDirs = listOf(common)
        )

        assertEquals(path, env["PATH"])
    }

    @Test
    fun `process environment keeps current values and adds missing shell values`() {
        val currentPath = createTempDirectory("agentdock-current-path").toFile()
        val shellPath = createTempDirectory("agentdock-shell-path").toFile()

        val env = AcpProcessEnvironment.mergedBaseEnvironment(
            current = mapOf(
                "PATH" to currentPath.absolutePath,
                "TOKEN" to "current-token"
            ),
            shell = mapOf(
                "PATH" to shellPath.absolutePath,
                "TOKEN" to "shell-token",
                "SHELL_ONLY" to "shell-value"
            ),
            commonExecutableDirs = emptyList()
        )

        assertEquals("current-token", env["TOKEN"])
        assertEquals("shell-value", env["SHELL_ONLY"])
        assertEquals(
            listOf(currentPath.absolutePath, shellPath.absolutePath).joinToString(File.pathSeparator),
            env["PATH"]
        )
    }

    @Test
    fun `process environment prepends runtime path entries before base path`() {
        val existing = createTempDirectory("agentdock-existing").toFile()
        val runtime = createTempDirectory("agentdock-runtime").toFile()

        val env = AcpProcessEnvironment.withPrependedPathEntries(
            source = mapOf("PATH" to existing.absolutePath),
            extraEntries = listOf(runtime)
        )

        assertEquals(
            listOf(runtime.absolutePath, existing.absolutePath).joinToString(File.pathSeparator),
            env["PATH"]
        )
    }

    @Test
    fun `process builder environment keeps existing variables while prepending runtime path`() {
        val existing = createTempDirectory("agentdock-existing").toFile()
        val runtime = createTempDirectory("agentdock-runtime").toFile()
        val builder = ProcessBuilder("node")
        val environment = builder.environment()
        environment.clear()
        environment["AGENTDOCK_CUSTOM"] = "keep-me"
        environment["PATH"] = existing.absolutePath

        AcpProcessEnvironment.applyTo(builder, listOf(runtime))

        assertEquals("keep-me", environment["AGENTDOCK_CUSTOM"])
        assertEquals(runtime.absolutePath, environment["PATH"]?.split(File.pathSeparator)?.firstOrNull())
        assertEquals(existing.absolutePath, environment["PATH"]?.split(File.pathSeparator)?.getOrNull(1))
    }

    @Test
    fun `process environment detects path key case insensitively`() {
        assertEquals("Path", AcpProcessEnvironment.pathKey(mapOf("Path" to "/usr/bin")))
        assertEquals("PATH", AcpProcessEnvironment.pathKey(emptyMap()))
    }

    @Test
    fun `process environment does not append unix executable dirs on windows hosts`() = withOsName("Windows 11") {
        val env = AcpProcessEnvironment.enrichedEnvironment(source = mapOf("Path" to "base-path"))

        assertEquals("base-path", env["Path"])
    }

    private fun npmAdapter(): AcpAdapterConfig.AdapterInfo {
        return AcpAdapterConfig.AdapterInfo(
            id = "tool",
            name = "Tool",
            distribution = AcpAdapterConfig.Distribution(
                type = AcpAdapterConfig.DistributionType.NPM,
                version = "latest",
                packageName = "tool"
            ),
            launchBinary = AcpAdapterConfig.PlatformBinary(
                win = "node_modules/.bin/tool.cmd",
                unix = "node_modules/.bin/tool"
            )
        )
    }

    private fun withOsName(value: String, block: () -> Unit) {
        val previous = System.getProperty("os.name")
        try {
            System.setProperty("os.name", value)
            block()
        } finally {
            System.setProperty("os.name", previous)
        }
    }
}
