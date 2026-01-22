package com.ljh.smarteducation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.ljh.smarteducation.entity")
@EnableJpaRepositories(basePackages = "com.ljh.smarteducation.repository")
public class SmartEducationBackendApplication {
    public static void main(String[] args) {
        // 检查是否要生成Word文档
        // 如果命令行参数中没有 --generate-word，则自动添加（方便IDE直接运行）
        boolean hasGenerateWord = false;
        for (String arg : args) {
            if (arg.contains("generate-word")) {
                hasGenerateWord = true;
                break;
            }
        }
        
        // 如果没有任何参数，且系统属性中没有设置，则自动生成Word文档
        if (args.length == 0 && System.getProperty("skip.word.generation") == null) {
            String[] newArgs = new String[]{"--generate-word"};
            SpringApplication.run(SmartEducationBackendApplication.class, newArgs);
        } else {
            SpringApplication.run(SmartEducationBackendApplication.class, args);
        }
    }
}