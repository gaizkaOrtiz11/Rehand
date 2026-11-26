package com.rehand.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.rehand.config.AppProperties;
import com.rehand.exception.BusinessRuleException;

/**
 * Stores uploaded images on disk under the configured upload folder and returns
 * the path relative to that folder (always with forward slashes), for example
 * {@code listings/3f2a...c1.jpg}. The relative path is what the database keeps;
 * pages show the image at {@code /uploads/<relative path>}.
 *
 * Only JPEG, PNG and WebP files are accepted. The file content (magic bytes) is
 * checked, not only the name or the content type sent by the browser, and the
 * stored file name is always a random UUID so user input never reaches the disk path.
 */
@Service
public class FileStorageService {

    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private static final Set<String> SUB_DIRECTORIES = Set.of("listings", "avatars");

    private final AppProperties appProperties;

    public FileStorageService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Saves one image.
     *
     * @param file   the uploaded file, must not be empty
     * @param subDir either "listings" or "avatars"
     * @return the relative path to store in the database
     * @throws BusinessRuleException when the file is empty, too large or not a JPEG/PNG/WebP image
     */
    public String saveImage(MultipartFile file, String subDir) {
        if (!SUB_DIRECTORIES.contains(subDir)) {
            throw new IllegalArgumentException("Unknown upload folder: " + subDir);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Please choose an image file.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessRuleException("The image is larger than 5 MB.");
        }
        String extension = detectExtension(file);
        if (extension == null) {
            throw new BusinessRuleException("Only JPEG, PNG and WebP images are allowed.");
        }
        String relativePath = subDir + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the uploaded image", e);
        }
        return relativePath;
    }

    /** Deletes a stored image. A missing file or a null/blank path is ignored. */
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + relativePath, e);
        }
    }

    /** Resolves a relative path inside the upload folder and refuses anything that escapes it. */
    private Path resolve(String relativePath) {
        Path root = appProperties.uploadPath();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes the upload folder");
        }
        return target;
    }

    /** Returns jpg, png or webp when the first bytes match, otherwise null. */
    private String detectExtension(MultipartFile file) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded image", e);
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && head[4] == 0x0D && head[5] == 0x0A && head[6] == 0x1A && head[7] == 0x0A) {
            return "png";
        }
        if (read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
