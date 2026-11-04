package com.datatalk.datatalkai.ai;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Locale;

/**
 * Creates the HTTP client used for AI calls.
 * trustStoreType: "auto" (Windows certificate store on Windows, JVM default elsewhere),
 * "default" (JVM cacerts) or a KeyStore type such as "Windows-ROOT".
 * Using the OS store avoids PKIX errors on corporate networks that inspect TLS traffic.
 */
public final class AiHttpClients {

    private AiHttpClients() {
    }

    public static HttpClient create(int connectTimeoutSeconds, String trustStoreType) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        String type = resolveType(trustStoreType);
        if (type != null) {
            try {
                KeyStore ks = KeyStore.getInstance(type);
                ks.load(null, null);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ks);
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, tmf.getTrustManagers(), null);
                builder.sslContext(ctx);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot load trust store type " + type + " for AI calls", e);
            }
        }
        return builder.build();
    }

    static String resolveType(String trustStoreType) {
        String t = trustStoreType == null ? "auto" : trustStoreType.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("auto")) {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows") ? "Windows-ROOT" : null;
        }
        return t.equalsIgnoreCase("default") ? null : t;
    }
}
