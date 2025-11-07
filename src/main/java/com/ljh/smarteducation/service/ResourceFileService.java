package com.ljh.smarteducation.service;

import com.ljh.smarteducation.entity.ResourceFile;
import com.ljh.smarteducation.repository.ResourceFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class ResourceFileService {

    private final ResourceFileRepository repository;
    private final FileStorageService storageService;

    public ResourceFileService(ResourceFileRepository repository, FileStorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }

    public ResourceFile saveFile(MultipartFile file, String subject) throws IOException {
        String storedPath = storageService.store(file); // 1. 存到磁盘

        // 2. 将元数据存到数据库
        ResourceFile resourceFile = new ResourceFile();
        resourceFile.setFileName(file.getOriginalFilename());
        resourceFile.setStoragePath(storedPath);
        resourceFile.setFileType(file.getContentType());
        resourceFile.setFileSize(file.getSize());
        resourceFile.setSubject(subject);
        
        return repository.save(resourceFile);
    }
    
    public Optional<ResourceFile> getFile(Long id) {
        return repository.findById(id);
    }

    public List<ResourceFile> getAllFiles() {
        return repository.findAll();
    }
    
    public void deleteFile(Long id) throws IOException {
        Optional<ResourceFile> fileOpt = repository.findById(id);
        if (fileOpt.isPresent()) {
            ResourceFile file = fileOpt.get();
            storageService.delete(file.getStoragePath()); // 1. 从磁盘删除
            repository.delete(file); // 2. 从数据库删除
        } else {
            throw new IOException("File not found with id: " + id);
        }
    }
}