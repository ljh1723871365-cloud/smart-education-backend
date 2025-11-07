package com.ljh.smarteducation.controller;

import com.ljh.smarteducation.entity.ResourceFile;
import com.ljh.smarteducation.service.FileStorageService;
import com.ljh.smarteducation.service.ResourceFileService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.util.Optional;

@RestController
@RequestMapping("/api/files") // 公共访问路径
@CrossOrigin(origins = "*") // 允许所有来源 (学生端)
public class FileDownloadController {

    private final ResourceFileService resourceFileService;
    private final FileStorageService storageService;

    public FileDownloadController(ResourceFileService resourceFileService, FileStorageService storageService) {
        this.resourceFileService = resourceFileService;
        this.storageService = storageService;
    }

    /**
     * GET /api/files/{id} - 学生端通过此接口获取文件 (如.mp3)
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> serveFile(@PathVariable Long id) {
        Optional<ResourceFile> fileOpt = resourceFileService.getFile(id);

        if (fileOpt.isPresent()) {
            ResourceFile fileDetails = fileOpt.get();
            try {
                Resource resource = storageService.loadAsResource(fileDetails.getStoragePath());
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(fileDetails.getFileType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileDetails.getFileName() + "\"")
                        .body(resource);
            } catch (MalformedURLException e) {
                return ResponseEntity.status(404).build();
            }
        } else {
            return ResponseEntity.status(404).build();
        }
    }
}