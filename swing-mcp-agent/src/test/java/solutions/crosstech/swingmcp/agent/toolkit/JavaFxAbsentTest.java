package solutions.crosstech.swingmcp.agent.toolkit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the agent still works in an application that has no JavaFX.
 *
 * <p>This is the failure mode that would matter most in the field: the agent is
 * attached to somebody else's running application, and the overwhelming
 * majority of those applications are Swing with no JavaFX anywhere. If merely
 * introducing a second toolkit made the agent fail to start there, every
 * existing user would break at once.</p>
 *
 * <p>JavaFX is a {@code provided} dependency, so it <em>is</em> on the test
 * classpath and the condition cannot be reproduced by ordinary means. The test
 * therefore reloads the toolkit classes through a class loader that hides every
 * {@code javafx.*} class, which is as close to a JavaFX-free JVM as can be
 * arranged from inside one that has it.</p>
 */
class JavaFxAbsentTest {

    /**
     * Loads {@code solutions.crosstech.swingmcp.*} afresh so those classes bind
     * to this loader, delegates the JDK to the parent, and denies all of JavaFX.
     */
    private static final class NoJavaFxClassLoader extends ClassLoader {

        NoJavaFxClassLoader() {
            super(NoJavaFxClassLoader.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("javafx.")) {
                throw new ClassNotFoundException("JavaFX is not present in this JVM: " + name);
            }
            if (!name.startsWith("solutions.crosstech.swingmcp.")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> existing = findLoadedClass(name);
                if (existing != null) {
                    return existing;
                }
                byte[] bytes = readBytes(name);
                if (bytes == null) {
                    return super.loadClass(name, resolve);
                }
                Class<?> defined = defineClass(name, bytes, 0, bytes.length);
                if (resolve) {
                    resolveClass(defined);
                }
                return defined;
            }
        }

        private byte[] readBytes(String name) {
            String path = name.replace('.', '/') + ".class";
            try (InputStream in = getParent().getResourceAsStream(path)) {
                return in == null ? null : in.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        }
    }

    @Test
    @DisplayName("with JavaFX hidden, the registry starts and reports Swing only")
    void registryStartsWithoutJavaFx() throws Exception {
        NoJavaFxClassLoader loader = new NoJavaFxClassLoader();

        // Sanity check that the loader really is hiding JavaFX.
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("javafx.application.Platform", false, loader));

        Class<?> registryClass = Class.forName(
            "solutions.crosstech.swingmcp.agent.toolkit.ToolkitRegistry", true, loader);
        Object registry = registryClass.getDeclaredConstructor().newInstance();

        Method all = registryClass.getMethod("all");
        List<?> toolkits = (List<?>) all.invoke(registry);
        assertEquals(1, toolkits.size(), "expected Swing only, got " + describe(toolkits));

        Method id = toolkits.get(0).getClass().getMethod("id");
        assertEquals("swing", id.invoke(toolkits.get(0)));
    }

    @Test
    @DisplayName("with JavaFX hidden, loadJavaFx returns null instead of throwing")
    void loadJavaFxReturnsNullWhenAbsent() throws Exception {
        NoJavaFxClassLoader loader = new NoJavaFxClassLoader();
        Class<?> registryClass = Class.forName(
            "solutions.crosstech.swingmcp.agent.toolkit.ToolkitRegistry", true, loader);
        Method load = registryClass.getDeclaredMethod("loadJavaFx");
        load.setAccessible(true);
        assertEquals(null, load.invoke(null),
            "a missing optional toolkit must be a normal condition, not an error");
    }

    @Test
    @DisplayName("with JavaFX hidden, an fx- uid is rejected with a usable message")
    void fxUidRejectedWhenJavaFxAbsent() throws Exception {
        NoJavaFxClassLoader loader = new NoJavaFxClassLoader();
        Class<?> registryClass = Class.forName(
            "solutions.crosstech.swingmcp.agent.toolkit.ToolkitRegistry", true, loader);
        Object registry = registryClass.getDeclaredConstructor().newInstance();
        Method forUid = registryClass.getMethod("forUid", String.class);

        InvocationTargetException wrapper = assertThrows(InvocationTargetException.class,
            () -> forUid.invoke(registry, "fx-1"));
        Throwable cause = wrapper.getCause();
        assertTrue(cause instanceof IllegalArgumentException, "got " + cause);
        assertTrue(cause.getMessage().contains("comp-*"),
            "the error should name the prefixes that do work: " + cause.getMessage());
    }

    private static String describe(List<?> toolkits) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Object t : toolkits) {
            sb.append(t.getClass().getMethod("id").invoke(t)).append(' ');
        }
        return sb.toString().trim();
    }
}
