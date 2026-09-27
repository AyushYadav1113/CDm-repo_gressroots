package com.grassroots.cdm.verification;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Test helper for generating live TLS test keystores and starting local HTTPS servers.
 */
public class TlsTestHelper {

    public static final char[] TEST_PASSWORD = "changeit".toCharArray();

    /**
     * Generates a PKCS12 test keystore using keytool.
     */
    public static File createTestKeystore(File keystoreFile,
                                         String alias,
                                         String dname,
                                         String sanExt,
                                         int validityDays,
                                         String startDate) throws Exception {
        if (keystoreFile.exists()) {
            keystoreFile.delete();
        }
        File parent = keystoreFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        String javaHome = System.getProperty("java.home");
        String keytoolPath = javaHome + File.separator + "bin" + File.separator + "keytool";
        if (!new File(keytoolPath).exists()) {
            keytoolPath = "keytool";
        }

        List<String> command = new ArrayList<>(List.of(
                keytoolPath,
                "-genkeypair",
                "-alias", alias,
                "-keystore", keystoreFile.getAbsolutePath(),
                "-storetype", "PKCS12",
                "-storepass", new String(TEST_PASSWORD),
                "-keypass", new String(TEST_PASSWORD),
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", String.valueOf(validityDays),
                "-dname", dname != null ? dname : "CN=localhost, O=Grassroots, C=US"
        ));

        if (sanExt != null && !sanExt.isBlank()) {
            command.add("-ext");
            command.add("san=" + sanExt);
        }

        if (startDate != null && !startDate.isBlank()) {
            command.add("-startdate");
            command.add(startDate);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        int exit = p.waitFor();
        if (exit != 0) {
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("keytool failed (" + exit + "): " + output);
        }

        return keystoreFile;
    }

    /**
     * Loads a KeyStore from a file.
     */
    public static KeyStore loadKeyStore(File keystoreFile) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keystoreFile)) {
            ks.load(fis, TEST_PASSWORD);
        }
        return ks;
    }

    /**
     * Extracts X509Certificate from a keystore by alias.
     */
    public static X509Certificate getCertificate(KeyStore ks, String alias) throws Exception {
        Certificate cert = ks.getCertificate(alias);
        return (X509Certificate) cert;
    }

    /**
     * Starts an HttpsServer bound to localhost:0 using the given keystore.
     */
    public static HttpsServer startHttpsServer(KeyStore keyStore) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, TEST_PASSWORD);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());

        HttpsServer server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                params.setNeedClientAuth(false);
            }
        });

        server.createContext("/", exchange -> {
            byte[] response = "{\"status\":\"healthy\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
            exchange.close();
        });

        server.start();
        return server;
    }

    /**
     * Starts a raw ServerSocket that accepts TCP connections and immediately sends non-TLS junk,
     * triggering a TLS protocol error on the client.
     */
    public static ServerSocket startFaultyTlsServer() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        Thread thread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    // Send non-TLS raw text bytes and close
                    OutputStream os = socket.getOutputStream();
                    os.write("HTTP/1.1 400 Bad Request\r\n\r\nThis is not a TLS handshake".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        return serverSocket;
    }
}
