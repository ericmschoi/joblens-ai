package com.joblens.resume;

import com.joblens.config.JoblensProperties;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * Cheap checks that run before any parsing, so a hostile or useless file is rejected before it can
 * reach a parser.
 *
 * <p>The declared filename and the client-supplied content type are not trusted. Only the file's
 * own bytes decide whether it is treated as a PDF.
 */
@Service
public class PdfValidationService {

    /** {@code %PDF-}, the header every PDF must begin with. */
    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46, 0x2D};

    private final JoblensProperties.Resume limits;

    public PdfValidationService(JoblensProperties properties) {
        this.limits = properties.resume();
    }

    /**
     * @throws ApiException with a user-facing message when the upload cannot be a usable PDF
     */
    public void validate(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ApiException(ErrorCode.FILE_MISSING, "No file was received.");
        }

        if (content.length > limits.maxFileSizeBytes()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "This file is larger than the %d MB limit.".formatted(limits.maxFileSizeBytes() / (1024 * 1024)));
        }

        if (!hasPdfSignature(content)) {
            throw new ApiException(ErrorCode.FILE_TYPE_NOT_SUPPORTED,
                    "This file is not a PDF, whatever its name suggests.");
        }
    }

    private static boolean hasPdfSignature(byte[] content) {
        if (content.length < PDF_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PDF_SIGNATURE.length; i++) {
            if (content[i] != PDF_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }
}
