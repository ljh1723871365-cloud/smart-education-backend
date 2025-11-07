package com.ljh.smarteducation.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface WordParserService {
    String parseWord(MultipartFile file) throws IOException;
}