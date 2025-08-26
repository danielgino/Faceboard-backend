package org.example.apimywebsite.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;
//Localhost
//    public CloudinaryService() {
//        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
//                "cloud_name", System.getProperty("CLOUDINARY_NAME"),
//                "api_key", System.getProperty("CLOUDINARY_API_KEY"),
//                "api_secret", System.getProperty("CLOUDINARY_API_SECRET")
////    check            "secure", true
//        ));
//    }


    //PRODCTION
//public CloudinaryService() {
//    this.cloudinary = new Cloudinary(ObjectUtils.asMap(
//            "cloud_name", System.getenv("CLOUDINARY_NAME"),
//            "api_key", System.getenv("CLOUDINARY_API_KEY"),
//            "api_secret", System.getenv("CLOUDINARY_API_SECRET")
//    ));
//}



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
        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());
        return uploadResult.get("url").toString();
    }
}
