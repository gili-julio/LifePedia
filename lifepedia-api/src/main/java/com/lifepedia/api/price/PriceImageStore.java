package com.lifepedia.api.price;

import com.lifepedia.api.config.StorageProperties;
import com.lifepedia.api.shared.image.ImageStore;
import com.lifepedia.api.shared.image.Images;
import org.springframework.stereotype.Component;

/** Fotos dos itens acompanhados em {@code STORAGE_DIR/prices} (JPEG de até 512px, baixadas do Buscapé). */
@Component
public class PriceImageStore extends ImageStore {

    static final int MAX_SIDE = 512;

    public PriceImageStore(StorageProperties properties) {
        super(properties.dir().resolve("prices"));
    }

    public String saveDownload(byte[] bytes) {
        return saveJpeg(Images.fromDownload(bytes, MAX_SIDE));
    }
}
