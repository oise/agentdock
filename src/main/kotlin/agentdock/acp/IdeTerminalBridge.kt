package agentdock.acp

import com.intellij.openapi.project.Project

internal interface IdeTerminalBridge {
    fun openInTerminal(workingDir: String, title: String, command: String)

    fun resolveShellPath(): String?
}

internal fun Project.ideTerminalBridge(): IdeTerminalBridge? =
    getService(IdeTerminalBridge::class.java)
