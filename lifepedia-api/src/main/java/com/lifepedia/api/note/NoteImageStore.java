package com.lifepedia.api.note;

import com.lifepedia.api.config.StorageProperties;
import com.lifepedia.api.shared.image.ImageStore;
import com.lifepedia.api.shared.image.Images;
import org.springframework.stereotype.Component;

/** Fotos das notas em {@code STORAGE_DIR/notes} (JPEG de até 1600px). */
@Component
public class NoteImageStore extends ImageStore {

    static final int MAX_SIDE = 1600;

    public NoteImageStore(StorageProperties properties) {
        super(properties.dir().resolve("notes"));
    }

    public String save(byte[] bytes) {
        return saveJpeg(Images.fromUpload(bytes, MAX_SIDE));
    }
}
