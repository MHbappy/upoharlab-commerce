package com.upoharlab.config;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class UploadErrorAdvice {
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<String> tooLarge() {
    return ResponseEntity.status(413)
        .contentType(MediaType.TEXT_PLAIN)
        .body("Photo too large. Choose a JPEG or PNG up to 5 MB, then go back and try again.");
  }
}
