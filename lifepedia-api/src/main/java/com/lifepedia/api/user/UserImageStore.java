package com.lifepedia.api.user;

import com.lifepedia.api.config.StorageProperties;
import com.lifepedia.api.shared.image.ImageStore;
import com.lifepedia.api.shared.image.Images;
import org.springframework.stereotype.Component;

/** Fotos de perfil em {@code STORAGE_DIR/users}: JPEG/PNG até 1 MB, regravadas como JPEG de até 512px. */
@Component
public class UserImageStore extends ImageStore {

    static final int MAX_SIDE = 512;

    public UserImageStore(StorageProperties properties) {
        super(properties.dir().resolve("users"));
    }

    /** Valida, re-encoda e grava; devolve a chave do arquivo. */
    public String save(byte[] bytes) {
        return saveJpeg(Images.fromUpload(bytes, MAX_SIDE));
    }
}
