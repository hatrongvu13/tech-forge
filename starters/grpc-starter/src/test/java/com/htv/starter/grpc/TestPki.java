package com.htv.starter.grpc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Sinh CA và chứng chỉ PEM bằng openssl cho test mTLS thật. Chỉ dùng cho test, khóa chỉ sống trong thư mục tạm. */
final class TestPki {

    final Path dir;
    final Path caCert;
    private final Path caKey;

    private TestPki(Path dir, String caName) throws Exception {
        this.dir = dir;
        this.caCert = dir.resolve(caName + ".pem");
        this.caKey = dir.resolve(caName + ".key");
        run("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", caKey.toString(),
                "-out", caCert.toString(), "-subj", "/CN=" + caName, "-days", "1");
    }

    /** Một cặp chứng chỉ + khóa (PKCS#8 PEM) do CA cấp. */
    record Identity(Path cert, Path key) {
    }

    static boolean opensslAvailable() {
        try {
            Process p = new ProcessBuilder("openssl", "version").redirectErrorStream(true).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    static TestPki create(String caName) throws Exception {
        return new TestPki(Files.createTempDirectory("htv-pki-"), caName);
    }

    /** Cấp chứng chỉ với {@code CN=cn}, có SAN {@code localhost} và {@code 127.0.0.1} (dùng được cho cả server). */
    Identity issue(String cn) throws Exception {
        Path key = dir.resolve(cn + ".raw.key");
        Path pkcs8 = dir.resolve(cn + ".key");
        Path csr = dir.resolve(cn + ".csr");
        Path cert = dir.resolve(cn + ".pem");
        Path ext = dir.resolve(cn + ".ext");
        Files.writeString(ext, "subjectAltName=DNS:localhost,IP:127.0.0.1\n");
        run("openssl", "req", "-newkey", "rsa:2048", "-nodes", "-keyout", key.toString(), "-out", csr.toString(),
                "-subj", "/CN=" + cn);
        run("openssl", "pkcs8", "-topk8", "-nocrypt", "-in", key.toString(), "-out", pkcs8.toString());
        run("openssl", "x509", "-req", "-in", csr.toString(), "-CA", caCert.toString(), "-CAkey", caKey.toString(),
                "-CAcreateserial", "-out", cert.toString(), "-days", "1", "-extfile", ext.toString());
        return new Identity(cert, pkcs8);
    }

    private void run(String... command) throws Exception {
        Process p = new ProcessBuilder(List.of(command)).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(30, TimeUnit.SECONDS) || p.exitValue() != 0) {
            throw new IllegalStateException("openssl failed: " + String.join(" ", command) + "\n" + out);
        }
    }
}
