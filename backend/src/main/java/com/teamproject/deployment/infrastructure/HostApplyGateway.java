package com.teamproject.deployment.infrastructure;

import com.teamproject.common.exception.ApplicationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Writes signed domain/TLS change requests into the host control directory and
 * reads the root-owned applier's result files back. Never carries key material
 * into logs or return values.
 */
@Component
public class HostApplyGateway {

    private final Path controlRoot;
    private final byte[] hmacKey;

    public HostApplyGateway(
            @Value("${app.host-apply.control-root:/var/lib/gearvia/control}") String controlRoot,
            @Value("${app.host-apply.hmac-key:${HOST_APPLY_REQUEST_HMAC_KEY:}}") String hmacKey) {
        this.controlRoot = Path.of(controlRoot);
        this.hmacKey = hmacKey == null ? new byte[0] : hmacKey.getBytes(StandardCharsets.UTF_8);
    }

    public void writeCandidate(String requestId, byte[] certificatePem, byte[] privateKeyPem) {
        try {
            Path dir = controlRoot.resolve("candidates").resolve(requestId);
            Files.createDirectories(dir);
            Files.write(dir.resolve("fullchain.pem"), certificatePem);
            Files.write(dir.resolve("privkey.pem"), privateKeyPem);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Removes a candidate's certificate and private key once the job no longer needs them. */
    public void deleteCandidate(String requestId) {
        Path dir = controlRoot.resolve("candidates").resolve(requestId);
        try {
            Files.deleteIfExists(dir.resolve("fullchain.pem"));
            Files.deleteIfExists(dir.resolve("privkey.pem"));
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
            // best effort; a stale candidate directory is harmless
        }
    }

    public void submit(String requestId, String publicUrl, String certificateMode) {
        if (hmacKey.length == 0) {
            throw new ApplicationException("DEPLOYMENT_HOST_KEY_MISSING", HttpStatus.SERVICE_UNAVAILABLE,
                    "호스트 적용 서명 키가 구성되지 않았습니다.");
        }
        try {
            Path requests = controlRoot.resolve("requests");
            Files.createDirectories(requests);
            String body = "requestId=" + requestId + "\npublicUrl=" + publicUrl
                    + "\ncertificateMode=" + certificateMode
                    + "\nsignature=" + sign(requestId, publicUrl, certificateMode) + "\n";
            Path tmp = requests.resolve(requestId + ".env.tmp");
            Files.writeString(tmp, body);
            Files.move(tmp, requests.resolve(requestId + ".env"),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** HMAC-SHA256 over "requestId\npublicUrl\ncertificateMode" (no trailing newline). */
    public String sign(String requestId, String publicUrl, String certificateMode) {
        byte[] data = (requestId + "\n" + publicUrl + "\n" + certificateMode).getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }

    public Optional<HostApplyResult> readResult(String requestId) {
        Path file = controlRoot.resolve("results").resolve(requestId + ".env");
        if (!Files.isReadable(file)) {
            return Optional.empty();
        }
        String status = "";
        String code = "";
        String issuer = "";
        String notAfter = "";
        String sans = "";
        try {
            for (String line : Files.readAllLines(file)) {
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq);
                String value = line.substring(eq + 1);
                switch (key) {
                    case "status" -> status = value;
                    case "code" -> code = value;
                    case "certificateIssuer" -> issuer = value;
                    case "certificateNotAfter" -> notAfter = value;
                    case "certificateSans" -> sans = value;
                    default -> { }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Optional.of(new HostApplyResult(status, code, issuer, notAfter, sans));
    }

    public record HostApplyResult(String status, String code, String certificateIssuer,
            String certificateNotAfter, String certificateSans) {
        public boolean succeeded() {
            return "OK".equals(code);
        }
    }
}
