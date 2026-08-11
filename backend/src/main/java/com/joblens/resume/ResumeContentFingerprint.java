package com.joblens.resume;

import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.resume.model.CandidateProfile;
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
 * carrying the reviewed content rather than something edited afterwards. Analysis will recompute it
 * over the payload it receives and refuse to score anything that does not match.
 *
 * <p>It is an integrity check against accidental drift between steps, not a security control: it is
 * unkeyed, and the client holds both the content and the digest.
 */
@Component
public class ResumeContentFingerprint {

    private final ObjectMapper objectMapper;

    public ResumeContentFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String of(String rawText, CandidateProfile profile) {
        byte[] canonical = objectMapper.writeValueAsBytes(new Fingerprinted(rawText, profile));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "The request could not be completed.", e);
        }
    }

    public boolean matches(String expectedFingerprint, String rawText, CandidateProfile profile) {
        return MessageDigest.isEqual(
                of(rawText, profile).getBytes(StandardCharsets.UTF_8),
                expectedFingerprint == null ? new byte[0] : expectedFingerprint.getBytes(StandardCharsets.UTF_8));
    }

    /** Records serialise in declaration order, so the same content always produces the same bytes. */
    private record Fingerprinted(String rawText, CandidateProfile profile) {}
}
