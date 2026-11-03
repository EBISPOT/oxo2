package uk.ac.ebi.spot.oxo.downloader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sun.net.httpserver.HttpServer;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DownloadMappingsFailureTest {
    @Test
    void refusedOntologyDownloadFailsTheCommand(@TempDir Path tmp) throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        Path config = tmp.resolve("config.json");
        Files.writeString(config, """
                {"mapping_registries":[{"id":"ols_mappings", "url":"http://127.0.0.1:%d/sssom.tgz"}]}
                """.formatted(port));

        assertThrows(IllegalStateException.class, () -> DownloadMappings.main(new String[]{
                "--config", config.toString(), "--download-dir", tmp.resolve("downloads").toString(),
                "--threads", "1"
        }));
    }

    @Test
    void httpErrorIsNotSavedAsAMappingFile(@TempDir Path tmp) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mappings.tsv", exchange -> {
            byte[] body = "Service unavailable".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            Path config = tmp.resolve("config.json");
            Files.writeString(config, """
                    {"mapping_registries":[{"id":"ols_mappings", "url":"http://127.0.0.1:%d/mappings.tsv"}]}
                    """.formatted(server.getAddress().getPort()));
            assertThrows(IllegalStateException.class, () -> DownloadMappings.main(new String[]{
                    "--config", config.toString(), "--download-dir", tmp.resolve("downloads").toString(),
                    "--threads", "1"
            }));
            assertFalse(Files.exists(tmp.resolve("downloads/ols_mappings.tsv")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void successfulLocalDownloadStillCompletes(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("source.tsv"), "subject_id\tobject_id\nEFO:1\tMONDO:1\n");
        Path config = tmp.resolve("config.json");
        Files.writeString(config, """
                {"mapping_registries":[{"id":"ols_mappings", "url":"source.tsv"}]}
                """);
        DownloadMappings.main(new String[]{"--config", config.toString(),
                "--download-dir", tmp.resolve("downloads").toString(),
                "--base-dir", tmp.toString(), "--threads", "1"});
        assertEquals(Files.readString(tmp.resolve("source.tsv")),
                Files.readString(tmp.resolve("downloads/ols_mappings.tsv")));
    }
}
