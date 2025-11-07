package com.ljh.smarteducation.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path rootLocation = Paths.get("uploads"); // 存储在项目根目录的 'uploads' 文件夹

    public FileStorageService() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage location", e);
        }
    }

    /**
     * 存储文件
     * @param file MultipartFile
     * @return 存储在磁盘上的唯一文件名 (e.g., "uuid-originalName.mp3")
     * @throws IOException
     */
    public String store(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file.");
        }
        
        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
             fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
        
        Path destinationFile = this.rootLocation.resolve(Paths.get(uniqueFilename))
                .normalize().toAbsolutePath();

        if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
            // 这是一个安全检查，防止目录遍历攻击
            throw new IOException("Cannot store file outside current directory.");
        }

        Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
        
        return uniqueFilename; // 返回存储的唯一路径名
    }

    /**
     * 加载文件
     * @param filename 存储在磁盘上的唯一文件名
     * @return Spring Resource
     */
    public Resource loadAsResource(String filename) throws MalformedURLException {
        Path file = rootLocation.resolve(filename);
        Resource resource = new UrlResource(file.toUri());
        
        if (resource.exists() || resource.isReadable()) {
            return resource;
        } else {
            throw new RuntimeException("Could not read file: " + filename);
        }
    }

    /**
     * 删除文件
     * @param filename 存储在磁盘上的唯一文件名
     */
    public void delete(String filename) throws IOException {
         Path file = rootLocation.resolve(filename);
         Files.deleteIfExists(file);
    }
}