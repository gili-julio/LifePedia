package com.lifepedia.api.architecture;

import com.lifepedia.api.architecture.ArchitectureAccess.Access;
import com.lifepedia.api.architecture.ArchitectureDtos.LightRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.LightResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.MaterialOverride;
import com.lifepedia.api.architecture.ArchitectureDtos.RoomRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.RoomResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.RoomUpdate;
import com.lifepedia.api.architecture.ArchitectureDtos.Scene;
import com.lifepedia.api.architecture.ArchitectureDtos.VariantRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.VariantResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.VariantUpdate;
import com.lifepedia.api.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Cena de cada versão: variantes de acabamento, cômodos e luzes da noite (quem edita cria; quem vê só lê). */
@Service
public class ArchSceneService {

    static final int MAX_VARIANTS = 20;
    static final int MAX_ROOMS = 60;
    static final int MAX_LIGHTS = 40;
    private static final double MAX_COORDINATE = 100_000;
    private static final Pattern MATERIAL_KEY = Pattern.compile("\\d{1,4}");
    private static final TypeReference<Map<String, MaterialOverride>> MATERIALS = new TypeReference<>() {
    };
    private static final TypeReference<List<double[]>> OUTLINE = new TypeReference<>() {
    };

    private final ArchVariantRepository variants;
    private final ArchRoomRepository rooms;
    private final ArchLightRepository lights;
    private final ArchVersionRepository versions;
    private final ArchitectureAccess access;
    private final JsonMapper json;

    public ArchSceneService(ArchVariantRepository variants, ArchRoomRepository rooms, ArchLightRepository lights,
                            ArchVersionRepository versions, ArchitectureAccess access, JsonMapper json) {
        this.variants = variants;
        this.rooms = rooms;
        this.lights = lights;
        this.versions = versions;
        this.access = access;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Scene scene(UUID projectId, UUID userId, UUID versionId) {
        access.require(projectId, userId, ArchRole.VIEW);
        requireVersion(projectId, versionId);
        return sceneOf(versionId);
    }

    /** Sem checar acesso: para o link público (que já validou o token). */
    @Transactional(readOnly = true)
    public Scene sceneOf(UUID versionId) {
        return new Scene(variants.findByVersionIdOrderByCreatedAtAsc(versionId).stream().map(this::response).toList(),
                rooms.findByVersionIdOrderByCreatedAtAsc(versionId).stream().map(this::response).toList(),
                lights.findByVersionIdOrderByCreatedAtAsc(versionId).stream().map(ArchSceneService::response).toList());
    }

    @Transactional
    public VariantResponse addVariant(UUID projectId, UUID userId, VariantRequest request) {
        Access found = access.require(projectId, userId, ArchRole.EDIT);
        requireVersion(projectId, request.versionId());
        if (variants.countByVersionId(request.versionId()) >= MAX_VARIANTS) {
            throw ArchitectureService.bad("Limite de " + MAX_VARIANTS + " variantes por versão.");
        }
        ArchVariant variant = variants.save(new ArchVariant(projectId, request.versionId(), userId, request.name().trim(),
                materials(request.materials())));
        found.project().touch();
        return response(variant);
    }

    @Transactional
    public VariantResponse updateVariant(UUID projectId, UUID userId, UUID variantId, VariantUpdate request) {
        access.require(projectId, userId, ArchRole.EDIT);
        ArchVariant variant = variants.findByIdAndProjectId(variantId, projectId)
                .orElseThrow(() -> new NotFoundException("Variante não encontrada."));
        variant.setName(request.name().trim());
        variant.setMaterials(materials(request.materials()));
        return response(variant);
    }

    @Transactional
    public void deleteVariant(UUID projectId, UUID userId, UUID variantId) {
        access.require(projectId, userId, ArchRole.EDIT);
        variants.delete(variants.findByIdAndProjectId(variantId, projectId)
                .orElseThrow(() -> new NotFoundException("Variante não encontrada.")));
    }

    @Transactional
    public RoomResponse addRoom(UUID projectId, UUID userId, RoomRequest request) {
        access.require(projectId, userId, ArchRole.EDIT);
        requireVersion(projectId, request.versionId());
        if (rooms.countByVersionId(request.versionId()) >= MAX_ROOMS) {
            throw ArchitectureService.bad("Limite de " + MAX_ROOMS + " cômodos por versão.");
        }
        double[] point = point(request.point());
        Double ceiling = request.ceiling() == null ? null : coordinate(request.ceiling());
        BigDecimal area = request.area() == null ? null : request.area().setScale(2, RoundingMode.HALF_UP);
        List<double[]> outline = request.outline().stream().map(p -> new double[] {coordinate(p.get(0)), coordinate(p.get(1))})
                .toList();
        return response(rooms.save(new ArchRoom(projectId, request.versionId(), userId, request.name().trim(), point, ceiling, area,
                json.writeValueAsString(outline))));
    }

    @Transactional
    public RoomResponse renameRoom(UUID projectId, UUID userId, UUID roomId, RoomUpdate request) {
        access.require(projectId, userId, ArchRole.EDIT);
        ArchRoom room = room(projectId, roomId);
        room.setName(request.name().trim());
        return response(room);
    }

    @Transactional
    public void deleteRoom(UUID projectId, UUID userId, UUID roomId) {
        access.require(projectId, userId, ArchRole.EDIT);
        rooms.delete(room(projectId, roomId));
    }

    @Transactional
    public LightResponse addLight(UUID projectId, UUID userId, LightRequest request) {
        access.require(projectId, userId, ArchRole.EDIT);
        requireVersion(projectId, request.versionId());
        if (lights.countByVersionId(request.versionId()) >= MAX_LIGHTS) {
            throw ArchitectureService.bad("Limite de " + MAX_LIGHTS + " luzes por versão.");
        }
        return response(lights.save(new ArchLight(projectId, request.versionId(), userId, point(request.point()),
                request.color().toLowerCase(), request.intensity())));
    }

    @Transactional
    public void deleteLight(UUID projectId, UUID userId, UUID lightId) {
        access.require(projectId, userId, ArchRole.EDIT);
        lights.delete(lights.findByIdAndProjectId(lightId, projectId)
                .orElseThrow(() -> new NotFoundException("Luz não encontrada.")));
    }

    /** Nomes das variantes e dos cômodos do projeto, para o arquivo da conta. */
    @Transactional(readOnly = true)
    public List<String> variantNames(UUID projectId) {
        return variants.findByProjectIdOrderByCreatedAtAsc(projectId).stream().map(ArchVariant::getName).toList();
    }

    @Transactional(readOnly = true)
    public List<String> roomNames(UUID projectId) {
        return rooms.findByProjectIdOrderByCreatedAtAsc(projectId).stream().map(ArchRoom::getName).toList();
    }

    private String materials(Map<String, MaterialOverride> materials) {
        Map<String, MaterialOverride> clean = new LinkedHashMap<>();
        materials.forEach((key, value) -> {
            if (!MATERIAL_KEY.matcher(key).matches()) throw ArchitectureService.bad("Material inválido.");
            if (value.color() != null || value.texture() != null) {
                clean.put(key, new MaterialOverride(value.color() == null ? null : value.color().toLowerCase(), value.texture(),
                        value.scale()));
            }
        });
        return json.writeValueAsString(clean);
    }

    private void requireVersion(UUID projectId, UUID versionId) {
        if (versionId == null || versions.findById(versionId).filter(v -> v.isReady() && v.getProjectId().equals(projectId))
                .isEmpty()) {
            throw ArchitectureService.bad("Versão inválida.");
        }
    }

    private ArchRoom room(UUID projectId, UUID roomId) {
        return rooms.findByIdAndProjectId(roomId, projectId).orElseThrow(() -> new NotFoundException("Cômodo não encontrado."));
    }

    private VariantResponse response(ArchVariant v) {
        return new VariantResponse(v.getId(), v.getVersionId(), v.getName(), json.readValue(v.getMaterials(), MATERIALS),
                v.getUpdatedAt());
    }

    private RoomResponse response(ArchRoom r) {
        return new RoomResponse(r.getId(), r.getVersionId(), r.getName(), r.point(), r.getCeiling(), r.getArea(),
                json.readValue(r.getOutline(), OUTLINE));
    }

    private static LightResponse response(ArchLight l) {
        return new LightResponse(l.getId(), l.getVersionId(), l.point(), l.getColor(), l.getIntensity());
    }

    private static double[] point(List<Double> values) {
        return new double[] {coordinate(values.get(0)), coordinate(values.get(1)), coordinate(values.get(2))};
    }

    private static double coordinate(Double value) {
        if (value == null || !Double.isFinite(value) || Math.abs(value) > MAX_COORDINATE) {
            throw ArchitectureService.bad("Ponto fora da maquete.");
        }
        return value;
    }
}
