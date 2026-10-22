package com.lifepedia.api.shared.image;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

/** JPEG lido do {@link ImageStore}; a chave vira o ETag. */
public record ImageFile(String key, byte[] bytes) {

    /** A URL leva {@code ?v=} (versão), então pode ficar em cache privado. */
    public ResponseEntity<byte[]> response() {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .eTag(key)
                .body(bytes);
    }
}
