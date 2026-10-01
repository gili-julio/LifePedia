package com.lifepedia.api.planning;

import com.lifepedia.api.config.StorageProperties;
import com.lifepedia.api.shared.image.ImageStore;
import com.lifepedia.api.shared.image.Images;
import org.springframework.stereotype.Component;

/** Fotos dos itens do planejamento em {@code STORAGE_DIR/plan-items} (JPEG de até 512px). */
@Component
public class PlanImageStore extends ImageStore {

    static final int MAX_SIDE = 512;

    public PlanImageStore(StorageProperties properties) {
        super(properties.dir().resolve("plan-items"));
    }

    /** Imagem vinda do usuário (upload ou prévia do link): valida, re-encoda e grava. */
    public String save(byte[] bytes) {
        return saveJpeg(Images.fromUpload(bytes, MAX_SIDE));
    }
}
