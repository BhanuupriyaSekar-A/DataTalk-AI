package com.datatalk.datatalkai.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts an ngrok tunnel when the application is ready so the local app gets a public https URL,
 * and stops it on shutdown. Requires the ngrok CLI with an authtoken configured.
 * Disable with NGROK_ENABLED=false.
 */
@Component
@ConditionalOnProperty(name = "app.ngrok.enabled", havingValue = "true")
public class NgrokTunnel {

    private static final Logger log = LoggerFactory.getLogger(NgrokTunnel.class);
    private static final String NGROK_API = "http://127.0.0.1:4040/api/tunnels";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.ngrok.command:ngrok}")
    private String command;

    @Value("${app.ngrok.domain:}")
    private String domain;

    @Value("${server.port:8080}")
    private int port;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    private Process process;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread starter = new Thread(this::startTunnel, "ngrok-starter");
        starter.setDaemon(true);
        starter.start();
    }

    private void startTunnel() {
        String existing = findPublicUrl();
        if (existing != null) {
            logUrl(existing, "reusing running ngrok tunnel");
            return;
        }
        List<String> cmd = new ArrayList<>(List.of(command, "http", String.valueOf(port), "--log", "stdout"));
        if (!domain.isBlank()) {
            cmd.add("--url=" + domain);
        }
        try {
            File logFile = new File("logs/ngrok.log");
            logFile.getParentFile().mkdirs();
            process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile)
                    .start();
        } catch (Exception e) {
            log.warn("ngrok could not be started ({}). Install ngrok or set NGROK_ENABLED=false.", e.getMessage());
            return;
        }
        for (int i = 0; i < 20; i++) {
            sleep(1000);
            if (!process.isAlive()) {
                log.warn("ngrok exited (code {}). See logs/ngrok.log - is the authtoken set or the domain already online elsewhere?",
                        process.exitValue());
                return;
            }
            String url = findPublicUrl();
            if (url != null) {
                logUrl(url, "ngrok tunnel started");
                return;
            }
        }
        log.warn("ngrok started but no public URL was reported within 20 s. See logs/ngrok.log.");
    }

    /** Returns the https public URL of a running tunnel that forwards to this app's port, or null. */
    private String findPublicUrl() {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(NGROK_API)).timeout(Duration.ofSeconds(2)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            for (JsonNode tunnel : mapper.readTree(response.body()).path("tunnels")) {
                String publicUrl = tunnel.path("public_url").asText("");
                String addr = tunnel.path("config").path("addr").asText("");
                if (publicUrl.startsWith("https://") && addr.endsWith(":" + port)) {
                    return publicUrl;
                }
            }
        } catch (Exception ignored) {
            // ngrok not running yet
        }
        return null;
    }

    private void logUrl(String url, String message) {
        log.info("==> {}: public URL {}{}/login", message, url, contextPath);
    }

    @PreDestroy
    public void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
            log.info("ngrok tunnel stopped");
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
