package solutions.crosstech.swingmcp.server.session;

import solutions.crosstech.swingmcp.common.enums.AttachMode;
import solutions.crosstech.swingmcp.common.enums.CommandType;
import solutions.crosstech.swingmcp.server.domain.SessionInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Session for a Swing application launched by the MCP server with the agent
 * preloaded via {@code -javaagent}.
 */
public final class LaunchedAppSession implements AppSession {

    private final Process process;
    private final AgentConnection connection;
    private final SessionInfo info;
    private final Path outputFile;

    public LaunchedAppSession(Process process, AgentConnection connection, int agentPort, String description) {
        this(process, connection, agentPort, description, null);
    }

    /**
     * @param outputFile the file the process's stdout and stderr are redirected to, or null
     */
    public LaunchedAppSession(Process process, AgentConnection connection, int agentPort, String description,
                              Path outputFile) {
        this.process = process;
        this.connection = connection;
        this.info = new SessionInfo(AttachMode.LAUNCHED, process.pid(), agentPort, description);
        this.outputFile = outputFile;
    }

    @Override
    public SessionInfo info() {
        return info;
    }

    @Override
    public Optional<Path> outputFile() {
        return Optional.ofNullable(outputFile);
    }

    @Override
    public Object send(CommandType type, Map<String, Object> params) {
        return connection.send(type, params);
    }

    @Override
    public boolean isAlive() {
        return process.isAlive() && connection.isOpen();
    }

    /**
     * Terminates the launched process after closing the agent connection.
     * Requests graceful termination first, then forcibly kills the process if
     * it has not exited within a short grace period — so a frozen or modal
     * Swing app is actually stopped rather than left orphaned.
     */
    @Override
    public void close() throws IOException {
        try {
            connection.close();
        } finally {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
