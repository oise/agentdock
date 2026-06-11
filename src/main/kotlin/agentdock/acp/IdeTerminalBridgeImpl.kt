package agentdock.acp

import com.intellij.terminal.ui.TerminalWidget
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

internal class IdeTerminalBridgeImpl(private val project: Project) : IdeTerminalBridge {
    override fun openInTerminal(workingDir: String, title: String, command: String) {
        val manager = TerminalToolWindowManager.getInstance(project)
        val errors = mutableListOf<Throwable>()
        val tabState = TerminalTabState().apply {
            myTabName = title
            myIsUserDefinedTabTitle = true
            myWorkingDirectory = workingDir
            myShellCommand = listOf(command)
        }
        if (openNewSession(manager, tabState, errors)) return
        if (openShellWidget(manager, workingDir, title, command, errors)) return
        throw IllegalStateException(
            "Unable to open IDE terminal session: ${errors.joinToString("; ") { it.javaClass.simpleName + ": " + it.message }}"
        )
    }

    override fun resolveShellPath(): String? =
        runCatching { TerminalProjectOptionsProvider.getInstance(project).shellPath }.getOrNull()

    private fun openNewSession(
        manager: TerminalToolWindowManager,
        tabState: TerminalTabState,
        errors: MutableList<Throwable>
    ): Boolean =
        runCatching {
            manager.javaClass
                .getMethod("createNewSession", TerminalTabState::class.java)
                .invoke(manager, tabState)
        }.onFailure { errors += it }.isSuccess

    private fun openShellWidget(
        manager: TerminalToolWindowManager,
        workingDir: String,
        title: String,
        command: String,
        errors: MutableList<Throwable>
    ): Boolean =
        runCatching {
            val widget = manager.javaClass
                .getMethod(
                    "createShellWidget",
                    String::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
                .invoke(manager, workingDir, title, true, true)
            val shellWidget = ShellTerminalWidget.toShellJediTermWidgetOrThrow(widget as TerminalWidget)
            shellWidget.executeCommand(command)
        }.onFailure { errors += it }.isSuccess
}
