package com.joblens.document;

import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * A digest of exactly what the user confirmed.
 *
 * <p>Nothing is stored server-side, so the fingerprint is how a later request can prove it is
 * carrying the reviewed content rather than something edited afterwards. Analysis recomputes it over
 * the payload it receives and refuses to score anything that does not match.
 *
 * <p>It is an integrity check against accidental drift between steps, not a security control: it is
 * unkeyed, and the client holds both the content and the digest.
 */
@Component
public class ContentFingerprint {

    private final ObjectMapper objectMapper;

    public ContentFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param structured the reviewed structure, whatever document it belongs to. Records serialise
     *        in declaration order, so the same content always produces the same bytes.
     */
    public String of(String rawText, Object structured) {
        byte[] canonical = objectMapper.writeValueAsBytes(new Fingerprinted(rawText, structured));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "The request could not be completed.", e);
        }
    }

    public boolean matches(String expectedFingerprint, String rawText, Object structured) {
        return MessageDigest.isEqual(
                of(rawText, structured).getBytes(StandardCharsets.UTF_8),
                expectedFingerprint == null ? new byte[0] : expectedFingerprint.getBytes(StandardCharsets.UTF_8));
    }

    private record Fingerprinted(String rawText, Object structured) {}
}
