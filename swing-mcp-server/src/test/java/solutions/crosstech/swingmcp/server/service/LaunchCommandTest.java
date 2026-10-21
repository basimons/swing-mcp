package solutions.crosstech.swingmcp.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Issue #25: the launched process gets extra environment variables, and the
 * {@code -javaagent} argument goes after the java launcher, not after whatever
 * the first token happens to be.
 */
class LaunchCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void agentGoesAfterPlainJava() {
        assertEquals(1, ApplicationService.agentArgIndex(List.of("java", "-jar", "app.jar")));
    }

    @Test
    void agentGoesAfterJavaBehindAWrapper() {
        assertEquals(3, ApplicationService.agentArgIndex(
            List.of("env", "HDF5_PLUGIN_PATH=./plugins", "java", "-jar", "app.jar")));
    }

    @Test
    void javaWithPathOrExeSuffixIsRecognised() {
        assertEquals(1, ApplicationService.agentArgIndex(List.of("/usr/lib/jvm/temurin-21/bin/java", "-jar", "a.jar")));
        assertEquals(1, ApplicationService.agentArgIndex(List.of("C:\\Program Files\\Java\\bin\\javaw.exe", "-jar", "a.jar")));
        assertEquals(1, ApplicationService.agentArgIndex(List.of("JAVA.EXE", "-jar", "a.jar")));
    }

    @Test
    void argumentsNamedLikeJavaDoNotCount() {
        assertEquals(1, ApplicationService.agentArgIndex(List.of("java", "-jar", "java-app.jar")));
        assertEquals(1, ApplicationService.agentArgIndex(List.of("./run.sh", "-Dx=<y>", "app")));
    }

    @Test
    void processBuilderAppliesEnvironmentAndWorkingDir() {
        ProcessBuilder pb = ApplicationService.processBuilder(List.of("java", "-version"), tempDir.toString(),
            Map.of("HDF5_PLUGIN_PATH", "./plugins"));
        assertEquals("./plugins", pb.environment().get("HDF5_PLUGIN_PATH"));
        assertEquals(tempDir.toFile(), pb.directory());
    }

    @Test
    void blankVariableNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ApplicationService.processBuilder(List.of("java"), null, Map.of(" ", "x")));
    }

    /** End to end: a real child JVM sees the variable. */
    @Test
    void launchedProcessSeesTheVariable() throws Exception {
        Path source = tempDir.resolve("PrintEnv.java");
        Files.writeString(source, "public class PrintEnv { public static void main(String[] a) "
            + "{ System.out.print(System.getenv(a[0])); } }");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = ApplicationService.processBuilder(
            List.of(java, source.toString(), "SWING_MCP_LAUNCH_TEST"), null,
            Map.of("SWING_MCP_LAUNCH_TEST", "hello from env"));
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        Process process = pb.start();
        assertEquals(true, process.waitFor(60, TimeUnit.SECONDS), "child JVM did not exit");
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("hello from env", out);
    }
}
