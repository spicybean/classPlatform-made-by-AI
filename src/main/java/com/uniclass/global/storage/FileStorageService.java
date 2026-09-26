package com.uniclass.global.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path rootLocation;

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB

    private static final Set<String> ALLOWED_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "pdf", "ppt", "pptx", "doc", "docx", "xls", "xlsx",
            "hwp", "hwpx", "zip", "txt", "png", "jpg", "jpeg", "gif", "csv"
    )));

    public FileStorageService(@Value("${uniclass.upload.location:./uploads}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw new IllegalStateException("파일 업로드 저장소 디렉터리를 생성할 수 없습니다: " + this.rootLocation, e);
        }
    }

    /**
     * 파일 업로드 유효성 검증 및 디스크 저장
     */
    public StoredFile store(MultipartFile file, String subDirectory) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어 있습니다.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("파일 용량이 제한(최대 20MB)을 초과하였습니다. (업로드 파일 크기: " +
                    String.format("%.2f MB", file.getSize() / (1024.0 * 1024.0)) + ")");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed");

        // 경로 조작 공격(Directory Traversal) 방어
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("유효하지 않은 파일 이름입니다: " + originalFilename);
        }

        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("지원하지 않는 파일 형식(확장자: ." + extension + ")입니다. " +
                    "허용 형식: PDF, PPT, Word, Excel, HWP, ZIP, TXT, 이미지");
        }

        String storedFilename = UUID.randomUUID().toString() + (extension.isEmpty() ? "" : "." + extension);

        try {
            Path targetDir = this.rootLocation.resolve(subDirectory).normalize();
            Files.createDirectories(targetDir);

            Path targetPath = targetDir.resolve(storedFilename).normalize();

            // 디렉터리 탈출 검증
            if (!targetPath.startsWith(this.rootLocation)) {
                throw new SecurityException("보안 정책상 지정된 업로드 디렉터리 외부에 파일을 저장할 수 없습니다.");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            String relativePath = subDirectory + "/" + storedFilename;
            return new StoredFile(originalFilename, storedFilename, relativePath, file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 중 입출력 오류가 발생했습니다: " + originalFilename, e);
        }
    }

    /**
     * 저장된 파일 리소스 로드 (다운로드용)
     */
    public Resource loadAsResource(String relativePath) {
        try {
            Path filePath = this.rootLocation.resolve(relativePath).normalize();

            if (!filePath.startsWith(this.rootLocation)) {
                throw new SecurityException("허용되지 않은 파일 경로 접근입니다.");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new IllegalArgumentException("요청한 파일을 찾을 수 없거나 읽을 수 없습니다: " + relativePath);
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("잘못된 파일 경로 형식입니다: " + relativePath, e);
        }
    }

    /**
     * 파일 삭제
     */
    public boolean delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        try {
            Path filePath = this.rootLocation.resolve(relativePath).normalize();
            if (filePath.startsWith(this.rootLocation)) {
                return Files.deleteIfExists(filePath);
            }
        } catch (IOException e) {
            // 삭제 실패 로그 기록 후 계속 진행
            return false;
        }
        return false;
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1);
    }

    public record StoredFile(
            String originalFilename,
            String storedFilename,
            String relativePath,
            long fileSize,
            String contentType
    ) {}
}
