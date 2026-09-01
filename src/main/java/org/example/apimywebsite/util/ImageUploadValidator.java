package org.example.apimywebsite.util;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

// H2 fix: centralized real-content image validation, used by every upload path that reaches
// CloudinaryService.uploadImage (post images, profile pictures, stories). Deliberately does not
// rely on file.getContentType(), the filename, or a handwritten magic-byte comparison alone -
// the declared Content-Type must be on an explicit allowlist AND the actual bytes must be fully
// decodable by a real image decoder (javax.imageio, plus the TwelveMonkeys WEBP plugin for the
// one format the JDK has no built-in reader for) AND the decoder's detected format must agree
// with the declared Content-Type.
@Component
public class ImageUploadValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    private static final String REJECTION_MESSAGE = "Only image files are allowed.";

    // SEC-006 fix: bounds on the pixel grid a single upload may decode into, checked via
    // ImageReader.getWidth/getHeight - header-only reads that do not touch pixel data - before
    // the full decodeAndDetectFormat below ever calls read(0). Faceboard is a social app (post
    // images, profile pictures, stories), not a professional-photography tool: 10000px per side
    // comfortably covers any real camera/phone photo a user would upload (well beyond typical
    // phone-camera output, e.g. 12-16MP defaults, and even most "high-res mode" shots), and
    // 40 megapixels total bounds worst-case decode memory to a known, sane amount regardless of
    // aspect ratio, while still being generous enough that no legitimate upload should ever hit
    // it. Byte-size limits (multipart max-file-size) already bound realistically-compressed
    // photos; these bounds specifically stop a small-byte-size file crafted to decode into a
    // huge pixel grid (the actual decompression-bomb vector, independent of file size).
    private static final int MAX_IMAGE_DIMENSION = 10_000;
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw reject();
        }

        String declaredContentType = normalizeContentType(file.getContentType());
        if (declaredContentType == null || !ALLOWED_CONTENT_TYPES.contains(declaredContentType)) {
            // Covers missing Content-Type, non-image types, and explicitly-excluded types such
            // as image/svg+xml (no sanitization/safe-delivery design exists for SVG here).
            throw reject();
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw reject();
        }

        String detectedFormat = decodeAndDetectFormat(bytes);
        if (detectedFormat == null || !formatMatchesDeclaredType(detectedFormat, declaredContentType)) {
            throw reject();
        }
    }

    // Selects an ImageReader the same way ImageIO always does (each registered SPI sniffs the
    // actual stream, not the filename/header alone), then fully decodes the pixel data via
    // read(0) rather than stopping at header/format recognition - a truncated or otherwise
    // corrupt file that merely starts with a valid signature throws here and is rejected, even
    // though a format-sniff-only check would have accepted it.
    private String decodeAndDetectFormat(byte[] bytes) {
        try (ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                String formatName = reader.getFormatName();
                reader.setInput(iis, true, true);

                // Header-only metadata reads - safe to call on an untrusted/malicious file
                // before ever touching pixel data via read(0) below.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (exceedsPixelBudget(width, height)) {
                    return null;
                }

                reader.read(0);
                return formatName == null ? null : formatName.toUpperCase(Locale.ROOT);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            // Any decode failure (IIOException, truncated-stream errors, malformed internal
            // structure, etc.) - the specific exception type varies by format plugin, but the
            // outcome is always the same: this is not a valid, fully-decodable image.
            return null;
        }
    }

    // Package-private (not private) so ImageUploadValidatorTest can pin this exact bound
    // directly with plain int inputs, without needing to construct a real decodable image at
    // the target size (which would mean actually allocating a huge pixel buffer just to prove a
    // check whose entire point is to avoid ever allocating one).
    static boolean exceedsPixelBudget(int width, int height) {
        return width <= 0 || height <= 0
                || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                || (long) width * height > MAX_IMAGE_PIXELS;
    }

    private boolean formatMatchesDeclaredType(String detectedFormat, String declaredContentType) {
        return switch (detectedFormat) {
            case "JPEG", "JPG" -> declaredContentType.equals("image/jpeg") || declaredContentType.equals("image/jpg");
            case "PNG" -> declaredContentType.equals("image/png");
            case "GIF" -> declaredContentType.equals("image/gif");
            case "WEBP" -> declaredContentType.equals("image/webp");
            default -> false;
        };
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int parameterStart = contentType.indexOf(';');
        String base = parameterStart >= 0 ? contentType.substring(0, parameterStart) : contentType;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException reject() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, REJECTION_MESSAGE);
    }
}
