package com.ljh.smarteducation.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "`resource_file`")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ResourceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName; // 原始文件名 (e.g., "cet4_listening.mp3")

    @Column(nullable = false)
    private String storagePath; // 存储在服务器上的唯一路径 (e.g., "168898888.mp3")

    @Column(nullable = false)
    private String fileType; // MIME type (e.g., "audio/mpeg")

    private Long fileSize; // 文件大小 (in bytes)

    private String subject; // 关联学科 (e.g., "English")

    @CreationTimestamp
    private LocalDateTime uploadTime;
}