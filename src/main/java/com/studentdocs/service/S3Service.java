package com.studentdocs.service;

import com.studentdocs.dto.DocumentView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class S3Service {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "txt", "png", "jpg", "jpeg", "ppt", "pptx", "xls", "xlsx"
    );
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${studentdocs.bucket}")
    private String bucketName;

    public S3Service(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /** Every student's files live under students/{username}/ so one query never sees another's files. */
    public String userPrefix(String username) {
        return "students/" + username + "/";
    }

    public List<DocumentView> listDocuments(String username) {
        List<DocumentView> documents = new ArrayList<>();
        if (bucketName == null || bucketName.isBlank()) {
            return documents;
        }

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(userPrefix(username))
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);

        for (S3Object obj : response.contents()) {
            String key = obj.key();
            if (key.endsWith("/")) continue;

            String rawName = key.substring(key.lastIndexOf('/') + 1);
            String displayName = rawName.contains("__") ? rawName.substring(rawName.indexOf("__") + 2) : rawName;

            documents.add(new DocumentView(
                    key,
                    displayName,
                    Math.round((obj.size() / 1024.0) * 10) / 10.0,
                    DISPLAY_FORMAT.format(obj.lastModified())
            ));
        }

        documents.sort(Comparator.comparing(DocumentView::modified).reversed());
        return documents;
    }

    public void uploadDocument(String username, MultipartFile file) throws IOException {
        String originalName = sanitizeFilename(file.getOriginalFilename());
        String extension = extensionOf(originalName);

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("That file type isn't allowed.");
        }

        String key = userPrefix(username) + UUID.randomUUID().toString().substring(0, 8) + "__" + originalName;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    }

    /** Throws if the key doesn't belong to this student — never lets one student reach another's files. */
    private void assertOwnership(String username, String key) {
        if (!key.startsWith(userPrefix(username))) {
            throw new SecurityException("Not your document.");
        }
    }

    public String presignedDownloadUrl(String username, String key) {
        assertOwnership(username, key);

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public void deleteDocument(String username, String key) {
        assertOwnership(username, key);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "file";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? "" : filename.substring(dot + 1);
    }
}
