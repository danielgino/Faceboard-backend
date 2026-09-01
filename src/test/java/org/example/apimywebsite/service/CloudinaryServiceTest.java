package org.example.apimywebsite.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.example.apimywebsite.util.Constants;
import org.example.apimywebsite.util.ImageUploadValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * H2 fix: CloudinaryService.uploadImage must (a) delegate real-content validation to
 * ImageUploadValidator before ever calling Cloudinary, and (b) explicitly request
 * resource_type=image on every upload. The validator's own format-by-format behavior is
 * covered exhaustively by ImageUploadValidatorTest; this class only proves the wiring between
 * the two, plus the successful-upload response contract.
 */
class CloudinaryServiceTest {

    private CloudinaryService cloudinaryService;
    private Cloudinary mockCloudinary;
    private Uploader mockUploader;

    @BeforeEach
    void setUp() {
        cloudinaryService = new CloudinaryService("test-cloud", "test-key", "test-secret");
        ReflectionTestUtils.setField(cloudinaryService, "imageUploadValidator", new ImageUploadValidator());
        mockCloudinary = mock(Cloudinary.class);
        mockUploader = mock(Uploader.class);
        when(mockCloudinary.uploader()).thenReturn(mockUploader);
        ReflectionTestUtils.setField(cloudinaryService, "cloudinary", mockCloudinary);
    }

    private static byte[] resource(String name) throws Exception {
        try (InputStream in = CloudinaryServiceTest.class.getClassLoader()
                .getResourceAsStream("images/" + name)) {
            assertNotNull(in, "missing test fixture images/" + name);
            return in.readAllBytes();
        }
    }

    @Test
    void uploadImage_validImage_reachesCloudinary_withExplicitResourceTypeImage() throws Exception {
        when(mockUploader.upload(any(), any())).thenReturn(Map.of("url", "https://cdn.example.com/fake.png"));
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", resource("test.png"));

        String url = cloudinaryService.uploadImage(file);

        assertEquals("https://cdn.example.com/fake.png", url);
        ArgumentCaptor<Map> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockUploader).upload(any(), optionsCaptor.capture());
        assertEquals("image", optionsCaptor.getValue().get("resource_type"));
    }

    @Test
    void uploadImage_invalidContent_isRejected_andCloudinaryIsNeverCalled() {
        MultipartFile file = new MockMultipartFile("file", "page.html", "text/html",
                "<html></html>".getBytes());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cloudinaryService.uploadImage(file));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Only image files are allowed.", ex.getReason());
        verifyNoInteractions(mockUploader);
    }

    @Test
    void uploadImage_mismatchedDeclaredTypeAndContent_isRejected_andCloudinaryIsNeverCalled() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", resource("test.png"));

        assertThrows(ResponseStatusException.class, () -> cloudinaryService.uploadImage(file));

        verifyNoInteractions(mockUploader);
    }

    // ---- Cloudinary orphan-image cleanup: deleteImage ----

    @Test
    void deleteImage_realUploadUrl_extractsPublicId_andCallsDestroy() throws Exception {
        when(mockUploader.destroy(any(), any())).thenReturn(Map.of("result", "ok"));

        cloudinaryService.deleteImage("https://res.cloudinary.com/dfembms4i/image/upload/v1745429024/abcdefghijklmnop.jpg");

        verify(mockUploader).destroy(eq("abcdefghijklmnop"), any());
    }

    @Test
    void deleteImage_defaultMaleProfilePicture_isNeverDeleted() {
        cloudinaryService.deleteImage(Constants.DEFAULT_PROFILE_PICTURE_MALE);

        verifyNoInteractions(mockUploader);
    }

    @Test
    void deleteImage_defaultFemaleProfilePicture_isNeverDeleted() {
        cloudinaryService.deleteImage(Constants.DEFAULT_PROFILE_PICTURE_FEMALE);

        verifyNoInteractions(mockUploader);
    }

    @Test
    void deleteImage_nullOrBlankUrl_isNeverDeleted() {
        cloudinaryService.deleteImage(null);
        cloudinaryService.deleteImage("");
        cloudinaryService.deleteImage("   ");

        verifyNoInteractions(mockUploader);
    }

    @Test
    void deleteImage_nonCloudinaryUrl_isNeverDeleted() {
        cloudinaryService.deleteImage("https://example.com/some/other/cdn/photo.png");

        verifyNoInteractions(mockUploader);
    }

    @Test
    void deleteImage_destroyThrows_isCaughtAndLogged_doesNotPropagate() throws Exception {
        when(mockUploader.destroy(any(), any())).thenThrow(new IOException("cloudinary unavailable"));

        assertDoesNotThrow(() -> cloudinaryService.deleteImage(
                "https://res.cloudinary.com/dfembms4i/image/upload/v1/somepublicid.png"));
    }
}
