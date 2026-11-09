package com.upoharlab.product;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;

/** Saves details, photos and visibility together; failed submissions leave no partial product. */
@Service
public class ProductEditor {
  private final ProductStore products;
  private final ImageStorage images;

  public ProductEditor(ProductStore products, ImageStorage images) {
    this.products = products;
    this.images = images;
  }

  @Transactional(rollbackFor = IOException.class)
  public Long save(Long id, ProductForm form, Errors errors, List<MultipartFile> uploads)
      throws IOException {
    var files = uploads == null ? List.<MultipartFile>of()
        : uploads.stream().filter(file -> !file.isEmpty()).toList();
    if (files.size() > 4) {
      errors.reject("photos", "Choose up to 4 photos at a time.");
      return id;
    }
    if (form.getSlug() == null || form.getSlug().isBlank()) {
      if (id == null) form.setSlug("gift-" + UUID.randomUUID().toString().substring(0, 12));
      else form.setSlug(products.get(id).details().getSlug());
    }
    String status = form.getStatus();
    Long saved;
    try {
      // Keep the row private within this transaction until photos and all details are valid.
      form.setStatus("DRAFT");
      saved = products.save(id, form, errors);
      if (errors.hasErrors()) return id;
      for (MultipartFile file : files) images.upload(saved, file, form.getTitle());
    } finally {
      form.setStatus(status);
    }
    products.save(saved, form, errors);
    if (errors.hasErrors()) {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      return id;
    }
    return saved;
  }
}
