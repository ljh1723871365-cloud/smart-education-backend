package com.ljh.smarteducation.controller;

import com.ljh.smarteducation.entity.ResourceFile;
import com.ljh.smarteducation.service.ResourceFileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/resources")
@CrossOrigin(origins = "http://localhost:5173") // 仅允许后台管理端
@PreAuthorize("hasRole('ADMIN')") // 确保只有管理员能访问
public class ResourceController {

    private final ResourceFileService resourceFileService;

    public ResourceController(ResourceFileService resourceFileService) {
        this.resourceFileService = resourceFileService;
    }

    /**
     * POST /api/admin/resources/upload - 上传文件 (如.mp3)
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam("subject") String subject) {
        try {
            ResourceFile savedFile = resourceFileService.saveFile(file, subject);
            return ResponseEntity.ok(savedFile);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Failed to upload file: " + e.getMessage());
        }
    }

    /**
     * GET /api/admin/resources - 获取所有资源文件列表
     */
    @GetMapping
    public ResponseEntity<List<ResourceFile>> listAllFiles() {
        return ResponseEntity.ok(resourceFileService.getAllFiles());
    }

    /**
     * DELETE /api/admin/resources/{id} - 删除一个资源文件
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteFile(@PathVariable Long id) {
        try {
            resourceFileService.deleteFile(id);
            return ResponseEntity.ok("File deleted successfully");
        } catch (IOException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }
}