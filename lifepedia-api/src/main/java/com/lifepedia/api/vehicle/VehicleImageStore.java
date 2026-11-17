package com.lifepedia.api.vehicle;

import com.lifepedia.api.config.StorageProperties;
import com.lifepedia.api.shared.image.ImageStore;
import com.lifepedia.api.shared.image.Images;
import org.springframework.stereotype.Component;

/** Fotos dos veículos (768 px) e dos comprovantes (1600 px, para dar para ler) em {@code STORAGE_DIR/vehicles}. */
@Component
public class VehicleImageStore extends ImageStore {

    static final int PHOTO_SIDE = 768;
    static final int RECEIPT_SIDE = 1600;

    public VehicleImageStore(StorageProperties properties) {
        super(properties.dir().resolve("vehicles"));
    }

    public String savePhoto(byte[] bytes) {
        return saveJpeg(Images.fromUpload(bytes, PHOTO_SIDE));
    }

    public String saveReceipt(byte[] bytes) {
        return saveJpeg(Images.fromUpload(bytes, RECEIPT_SIDE));
    }
}
