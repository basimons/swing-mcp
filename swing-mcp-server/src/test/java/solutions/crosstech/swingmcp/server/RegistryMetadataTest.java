package solutions.crosstech.swingmcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards against a repeat of the MCP Registry rejecting {@code server.json} for a
 * {@code description} over its 100-character limit, and keeps the MCPB manifest's
 * one-liner in step with it.
 */
class RegistryMetadataTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private File findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir"));
        for (int i = 0; i <= 5; i++) {
            if (dir == null) {
                break;
            }
            File candidate = dir.resolve("server.json").toFile();
            if (candidate.isFile()) {
                return dir.toFile();
            }
            dir = dir.getParent();
        }
        fail("Could not locate repo root containing server.json within 5 levels of "
                + System.getProperty("user.dir"));
        return null; // unreachable
    }

    @Test
    void serverJsonDescriptionFitsRegistryLimit() throws IOException {
        File repoRoot = findRepoRoot();
        JsonNode serverJson = MAPPER.readTree(new File(repoRoot, "server.json"));

        assertEquals("io.github.crosstech-solutions-bv/swing-mcp", serverJson.path("name").asText());

        String description = serverJson.path("description").asText();
        assertFalse(description.isBlank(), "description must not be blank");
        assertTrue(description.length() <= 100,
                "MCP Registry rejects descriptions over 100 characters (was " + description.length() + ")");

        String title = serverJson.path("title").asText();
        assertFalse(title.isBlank(), "title must not be blank");
        assertTrue(title.length() <= 100, "title must not exceed 100 characters (was " + title.length() + ")");
    }

    @Test
    void manifestOneLinerMatchesServerJson() throws IOException {
        File repoRoot = findRepoRoot();
        JsonNode serverJson = MAPPER.readTree(new File(repoRoot, "server.json"));
        JsonNode manifest = MAPPER.readTree(new File(repoRoot, "mcpb/manifest.json"));

        assertEquals(serverJson.path("description").asText(), manifest.path("description").asText(),
                "manifest.json and server.json one-liners must match");
    }
}
