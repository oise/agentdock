package agentdock.acp

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

internal class IdeTerminalBridgeImpl(private val project: Project) : IdeTerminalBridge {
    override fun openInTerminal(workingDir: String, title: String, command: String) {
        val tabState = TerminalTabState().apply {
            myTabName = title
            myIsUserDefinedTabTitle = true
            myWorkingDirectory = workingDir
            myShellCommand = listOf(command)
        }
        TerminalToolWindowManager.getInstance(project).createNewSession(tabState)
    }

    override fun resolveShellPath(): String? =
        runCatching { TerminalProjectOptionsProvider.getInstance(project).shellPath }.getOrNull()
}
