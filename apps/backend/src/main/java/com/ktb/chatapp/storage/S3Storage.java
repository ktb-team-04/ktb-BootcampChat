package com.ktb.chatapp.storage;

import java.io.InputStream;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class S3Storage implements StoragePort {

    private final S3Client s3Client;
    private final String bucket;

    public S3Storage(S3Client s3Client, @Value("${app.aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public StoredObject put(InputStream content, String key, String contentType, long size) {
        validateKey(key);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(size)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromInputStream(content, size));
            return new StoredObject(key, size);
        } catch (SdkException ex) {
            throw new RuntimeException("S3 파일 저장에 실패했습니다: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<Resource> open(String key) {
        validateKey(key);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            return Optional.of(new S3ObjectResource(key, response));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (SdkException ex) {
            throw new RuntimeException("S3 파일 읽기에 실패했습니다: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void delete(String key) {
        validateKey(key);
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException ex) {
            throw new RuntimeException("S3 파일 삭제에 실패했습니다: " + ex.getMessage(), ex);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()
                || key.startsWith("/")
                || key.contains("\\")
                || key.contains("..")) {
            throw new RuntimeException("파일 경로가 안전하지 않습니다.");
        }
    }

    private static class S3ObjectResource extends ByteArrayResource {
        private final String key;
        private final GetObjectResponse response;

        S3ObjectResource(String key, ResponseBytes<GetObjectResponse> responseBytes) {
            super(responseBytes.asByteArray());
            this.key = key;
            this.response = responseBytes.response();
        }

        @Override
        public String getFilename() {
            return StorageKey.nameOf(key);
        }

        @Override
        public long contentLength() {
            Long contentLength = response.contentLength();
            return contentLength != null ? contentLength : super.contentLength();
        }
    }
}
