package org.interview.config;

import org.apache.tika.Tika;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

    private final Tika tika = new Tika();

    public String parseContent(MultipartFile file) {
        String filename = file.getOriginalFilename();
        try (InputStream is = file.getInputStream()) {
            String text = tika.parseToString(is);
            log.info("Parsed resume '{}': {} chars", filename, text.length());
            return text;
        } catch (Exception e) {
            log.error("Failed to parse resume '{}': {}", filename, e.getMessage());
            throw new RuntimeException("Failed to parse file: " + filename, e);
        }
    }

    public String detectContentType(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            String mimeType = tika.detect(is, file.getOriginalFilename());
            log.debug("Detected content type for '{}': {}", file.getOriginalFilename(), mimeType);
            return mimeType;
        } catch (Exception e) {
            log.warn("Failed to detect content type for '{}', using header: {}",
                    file.getOriginalFilename(), file.getContentType());
            return file.getContentType();
        }
    }

    public String calculateHash(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate file hash", e);
        }
    }
}
