package org.example.apimywebsite.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.example.apimywebsite.util.Constants;
import org.example.apimywebsite.util.ImageUploadValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CloudinaryService {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    // Cloudinary orphan-image cleanup: uploadImage never passes a `folder`, so every asset this
    // app creates lives directly under .../<resource_type>/upload/[transformations/]v<version>/
    // <public_id>.<ext> - no folder prefix to account for. Matches only what this app's own
    // upload path actually produces; anything else (a URL we don't recognize) is left untouched.
    private static final Pattern CLOUDINARY_UPLOAD_PATH = Pattern.compile(
            "res\\.cloudinary\\.com/[^/]+/(?:image|video|raw)/upload/(?:[^/]+/)*?(?:v\\d+/)?([^/.]+)(?:\\.[A-Za-z0-9]+)?$"
    );

    // Never delete these regardless of URL shape - they're shared, not per-user uploads.
    private static final Set<String> NEVER_DELETE = Set.of(
            Constants.DEFAULT_PROFILE_PICTURE_MALE, Constants.DEFAULT_PROFILE_PICTURE_FEMALE
    );

    private final Cloudinary cloudinary;

    @Autowired
    private ImageUploadValidator imageUploadValidator;

    public CloudinaryService(
        @Value("${cloudinary.name}") String cloudName,
        @Value("${cloudinary.key}") String apiKey,
        @Value("${cloudinary.secret}") String apiSecret
) {
    this.cloudinary = new Cloudinary(ObjectUtils.asMap(
            "cloud_name", cloudName,
            "api_key", apiKey,
            "api_secret", apiSecret
    ));
}
    public String uploadImage(MultipartFile file) throws IOException {
        // H2 fix: real-content validation (declared Content-Type allowlist + full decode +
        // detected-format-matches-declared-type) happens here, before any Cloudinary request is
        // made. See ImageUploadValidator for why this is not just a Content-Type/extension/
        // magic-byte check.
        imageUploadValidator.validate(file);

        Map uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap("resource_type", "image"));
        return uploadResult.get("url").toString();
    }

    // Cloudinary orphan-image cleanup: best-effort remote deletion, called AFTER the
    // corresponding DB write has already succeeded (deletePost/uploadProfilePicture/
    // removeProfilePicture). Never throws - a Cloudinary-side failure must not undo or fail an
    // already-committed DB change; it just means the orphan persists a little longer, which is
    // strictly no worse than today's total absence of cleanup. Silently no-ops (no Cloudinary
    // call, no log noise) for null/blank URLs, the two shared default profile pictures, and any
    // URL that isn't a Cloudinary upload URL this app itself could have produced.
    public void deleteImage(String url) {
        if (url == null || url.isBlank() || NEVER_DELETE.contains(url)) {
            return;
        }
        String publicId = extractPublicId(url);
        if (publicId == null) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            log.warn("Failed to delete Cloudinary image (public_id={}); leaving it in place", publicId, e);
        }
    }

    private String extractPublicId(String url) {
        Matcher matcher = CLOUDINARY_UPLOAD_PATH.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }
}
