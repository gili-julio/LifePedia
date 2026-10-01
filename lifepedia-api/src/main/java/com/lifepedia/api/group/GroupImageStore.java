package com.lifepedia.api.group;

import com.lifepedia.api.config.StorageProperties;
import com.lifepedia.api.shared.image.ImageStore;
import com.lifepedia.api.shared.image.Images;
import org.springframework.stereotype.Component;

/** Imagens de grupo em {@code STORAGE_DIR/groups}: JPEG/PNG até 1 MB, regravadas como JPEG de até 512px. */
@Component
public class GroupImageStore extends ImageStore {

    public static final int MAX_BYTES = Images.MAX_UPLOAD_BYTES;
    static final int MAX_SIDE = 512;

    public GroupImageStore(StorageProperties properties) {
        super(properties.dir().resolve("groups"));
    }

    /** Valida, re-encoda e grava; devolve a chave do arquivo. */
    public String save(byte[] bytes) {
        return saveJpeg(Images.fromUpload(bytes, MAX_SIDE));
    }
}
