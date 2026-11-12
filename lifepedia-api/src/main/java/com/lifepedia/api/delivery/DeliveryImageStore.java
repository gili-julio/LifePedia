package com.lifepedia.api.delivery;

import com.lifepedia.api.config.StorageProperties;
import com.lifepedia.api.shared.image.ImageStore;
import com.lifepedia.api.shared.image.Images;
import org.springframework.stereotype.Component;

/** Fotos dos itens das entregas em {@code STORAGE_DIR/deliveries} (JPEG de até 512px). */
@Component
public class DeliveryImageStore extends ImageStore {

    static final int MAX_SIDE = 512;

    public DeliveryImageStore(StorageProperties properties) {
        super(properties.dir().resolve("deliveries"));
    }

    public String save(byte[] bytes) {
        return saveJpeg(Images.fromUpload(bytes, MAX_SIDE));
    }
}
