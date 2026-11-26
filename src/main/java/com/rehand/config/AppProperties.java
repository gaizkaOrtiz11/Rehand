package com.rehand.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed application settings bound from the {@code app.*} properties.
 *
 * <pre>
 * app.upload-dir=./uploads
 * </pre>
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Folder where uploaded images are stored. Relative paths are resolved against the working directory. */
    private String uploadDir = "./uploads";

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    /** The upload folder as an absolute, normalised path. */
    public Path uploadPath() {
        return Path.of(uploadDir).toAbsolutePath().normalize();
    }
}
