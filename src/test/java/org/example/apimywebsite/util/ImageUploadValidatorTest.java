package org.example.apimywebsite.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2 fix: proves ImageUploadValidator checks actual, fully-decoded image content - not just
 * Content-Type/filename/extension, and not a handwritten magic-byte comparison. All "valid
 * image" fixtures are real, genuinely decodable files (generated with Pillow at authoring time,
 * see src/test/resources/images), not hand-built byte arrays that merely start with the right
 * signature.
 */
class ImageUploadValidatorTest {

    private final ImageUploadValidator validator = new ImageUploadValidator();

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = ImageUploadValidatorTest.class.getClassLoader()
                .getResourceAsStream("images/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test fixture: images/" + name);
            }
            return in.readAllBytes();
        }
    }

    private ResponseStatusException assertRejected(MultipartFile file) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> validator.validate(file));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Only image files are allowed.", ex.getReason());
        return ex;
    }

    // ---- every confirmed supported format succeeds ----

    @Test
    void validate_realJpeg_succeeds() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", resource("test.jpg"));
        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void validate_realJpeg_withJpgAlias_succeeds() throws Exception {
        // "image/jpg" is a non-standard but real-world alias some clients send.
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpg", resource("test.jpg"));
        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void validate_realPng_succeeds() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", resource("test.png"));
        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void validate_realGif_succeeds() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "photo.gif", "image/gif", resource("test.gif"));
        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void validate_realWebp_succeeds() throws Exception {
        // Regression guard: the JDK's built-in javax.imageio has no WEBP reader at all - this
        // only passes because the TwelveMonkeys imageio-webp plugin is on the classpath and
        // ImageIO.getImageReaders() picks it up via SPI.
        MultipartFile file = new MockMultipartFile("file", "photo.webp", "image/webp", resource("test.webp"));
        assertDoesNotThrow(() -> validator.validate(file));
    }

    // ---- content, not extension/filename, decides the outcome ----

    @Test
    void validate_realImageBytes_withMisleadingFilename_succeedsBasedOnContent() throws Exception {
        // Filename claims .txt, but the declared Content-Type and actual bytes are a real PNG -
        // the filename itself must play no role in the decision.
        MultipartFile file = new MockMultipartFile("file", "not-an-image.txt", "image/png", resource("test.png"));
        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void validate_nonImageBytes_renamedToJpg_isRejected() throws Exception {
        byte[] html = "<html><body>not an image</body></html>".getBytes();
        MultipartFile file = new MockMultipartFile("file", "totally-a-photo.jpg", "image/jpeg", html);
        assertRejected(file);
    }

    @Test
    void validate_htmlBytesDeclaredAsJpeg_isRejected() throws Exception {
        byte[] html = "<html><script>alert(document.cookie)</script></html>".getBytes();
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", html);
        assertRejected(file);
    }

    @Test
    void validate_executableBytesDeclaredAsImage_isRejected() throws Exception {
        // "MZ" DOS/PE executable header, followed by arbitrary bytes.
        byte[] exe = new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", exe);
        assertRejected(file);
    }

    // ---- truncated/corrupt data with a valid-looking header is rejected ----

    @Test
    void validate_truncatedJpeg_withValidHeader_isRejected() throws Exception {
        // Real JPEG signature/APP0 header, but no actual scan/image data or EOI marker - a
        // format-sniff-only check would accept this; full decode must not.
        MultipartFile file = new MockMultipartFile("file", "broken.jpg", "image/jpeg", resource("truncated.jpg"));
        assertRejected(file);
    }

    @Test
    void validate_truncatedPng_withValidSignature_isRejected() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "broken.png", "image/png", resource("truncated.png"));
        assertRejected(file);
    }

    // ---- declared MIME type must agree with the detected format ----

    @Test
    void validate_realPngBytes_declaredAsJpeg_isRejected() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", resource("test.png"));
        assertRejected(file);
    }

    @Test
    void validate_realJpegBytes_declaredAsPng_isRejected() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", resource("test.jpg"));
        assertRejected(file);
    }

    // ---- SVG is not supported (no sanitization/safe-delivery design exists) ----

    @Test
    void validate_svgDeclaredContentType_isRejected() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();
        MultipartFile file = new MockMultipartFile("file", "photo.svg", "image/svg+xml", svg);
        assertRejected(file);
    }

    // ---- basic input hygiene ----

    @Test
    void validate_emptyFile_isRejected() {
        MultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        assertRejected(file);
    }

    @Test
    void validate_nullFile_isRejected() {
        assertRejected(null);
    }

    @Test
    void validate_missingContentType_isRejected() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "mystery", null, resource("test.png"));
        assertRejected(file);
    }

    @Test
    void validate_unsupportedButRealContentType_isRejectedBeforeAnyDecodeAttempt() {
        // application/pdf is not on the allowlist at all - rejected at the Content-Type stage,
        // regardless of what the bytes actually are.
        byte[] bytes = "%PDF-1.4".getBytes();
        MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", bytes);
        assertRejected(file);
    }

    // ---- SEC-006: the width/height budget check itself, pinned directly with plain int inputs.
    // Deliberately not exercised via a crafted/huge image file here: any file small enough for a
    // fast unit test to construct is also small/incomplete enough to be rejected by the existing
    // "must fully decode" check regardless of this new one, which would prove nothing about this
    // specific bound; and a file large enough to genuinely decode at these dimensions would mean
    // allocating the very huge pixel buffer this check exists to avoid ever allocating. Package-
    // private exceedsPixelBudget lets the exact invariant be pinned without either problem. The
    // "real image, real decode" fixtures above already cover every in-bounds success path. ----

    @Test
    void exceedsPixelBudget_atOrUnderBothCaps_isFalse() {
        assertFalse(ImageUploadValidator.exceedsPixelBudget(1, 1));
        // Exactly at both caps: 10,000 x 4,000 = 40,000,000px exactly.
        assertFalse(ImageUploadValidator.exceedsPixelBudget(10_000, 4_000));
    }

    @Test
    void exceedsPixelBudget_oneSideOverTheDimensionCap_isTrue() {
        assertTrue(ImageUploadValidator.exceedsPixelBudget(10_001, 1));
        assertTrue(ImageUploadValidator.exceedsPixelBudget(1, 10_001));
    }

    @Test
    void exceedsPixelBudget_bothSidesUnderDimensionCap_butTotalPixelsOverBudget_isTrue() {
        // 9000 x 9000 = 81M px: each side is comfortably under the 10,000px cap individually,
        // but the total is well over the 40M total-pixel cap.
        assertTrue(ImageUploadValidator.exceedsPixelBudget(9_000, 9_000));
    }

    @Test
    void exceedsPixelBudget_typicalHighResPhoto_isFalse() {
        // 6000x4000 (24MP) comfortably represents a real high-resolution phone/camera photo -
        // must never be rejected by this bound.
        assertFalse(ImageUploadValidator.exceedsPixelBudget(6000, 4000));
    }

    @Test
    void exceedsPixelBudget_zeroOrNegativeDimensions_isTrue() {
        assertTrue(ImageUploadValidator.exceedsPixelBudget(0, 100));
        assertTrue(ImageUploadValidator.exceedsPixelBudget(100, 0));
        assertTrue(ImageUploadValidator.exceedsPixelBudget(-1, 100));
    }
}
