package com.lifepedia.api.vehicle;

import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Papel de cada pessoa num veículo: dono, compartilhado direto com ela ou com um grupo de que ela é membro (vale o
 * maior). Sem acesso → 404; papel menor que o pedido → 403.
 */
@Component
public class VehicleAccess {

    public record Access(Vehicle vehicle, VehicleRole role) {

        public boolean can(VehicleRole needed) {
            return role.atLeast(needed);
        }
    }

    /** @param groupName nome do grupo quando o acesso vem só por ele */
    public record Grant(VehicleRole role, String groupName) {
    }

    private static final String ROLES = """
            select s.role from vehicle_shares s where s.vehicle_id = :vehicle and s.user_id = :user
            union all
            select s.role from vehicle_shares s join group_members m on m.group_id = s.group_id
             where s.vehicle_id = :vehicle and m.user_id = :user
            """;

    private static final String GRANTS = """
            select s.vehicle_id, s.role, null as group_name from vehicle_shares s where s.user_id = :user
            union all
            select s.vehicle_id, s.role, g.name from vehicle_shares s
              join group_members m on m.group_id = s.group_id
              join user_groups g on g.id = s.group_id
             where m.user_id = :user
            """;

    private static final String PEOPLE = """
            select s.user_id, s.role from vehicle_shares s where s.vehicle_id = :vehicle and s.user_id is not null
            union all
            select m.user_id, s.role from vehicle_shares s join group_members m on m.group_id = s.group_id
             where s.vehicle_id = :vehicle
            """;

    private final VehicleRepository vehicles;
    private final JdbcClient jdbc;

    public VehicleAccess(VehicleRepository vehicles, JdbcClient jdbc) {
        this.vehicles = vehicles;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Access require(UUID vehicleId, UUID userId, VehicleRole needed) {
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow(VehicleAccess::notFound);
        VehicleRole role = roleOf(vehicle, userId);
        if (role == null) throw notFound();
        if (!role.atLeast(needed)) throw forbidden(needed);
        return new Access(vehicle, role);
    }

    @Transactional(readOnly = true)
    public VehicleRole roleOf(Vehicle vehicle, UUID userId) {
        if (vehicle.getUserId().equals(userId)) return VehicleRole.OWNER;
        return jdbc.sql(ROLES).param("vehicle", vehicle.getId()).param("user", userId)
                .query((rs, n) -> VehicleRole.valueOf(rs.getString(1))).list().stream()
                .max(Enum::compareTo).orElse(null);
    }

    /** Veículos de outras pessoas compartilhados com esta (os dela via grupo também vêm; quem lista filtra). */
    @Transactional(readOnly = true)
    public Map<UUID, Grant> grants(UUID userId) {
        Map<UUID, Grant> grants = new HashMap<>();
        jdbc.sql(GRANTS).param("user", userId).query((RowCallbackHandler) rs -> grants.merge(rs.getObject(1, UUID.class),
                new Grant(VehicleRole.valueOf(rs.getString(2)), rs.getString(3)), VehicleAccess::best));
        return grants;
    }

    /** Dono + quem tem pelo menos o papel (direto ou pelo grupo), para avisos. */
    @Transactional(readOnly = true)
    public Set<UUID> people(Vehicle vehicle, VehicleRole atLeast) {
        Set<UUID> ids = new HashSet<>();
        ids.add(vehicle.getUserId());
        jdbc.sql(PEOPLE).param("vehicle", vehicle.getId()).query((RowCallbackHandler) rs -> {
            if (VehicleRole.valueOf(rs.getString(2)).atLeast(atLeast)) ids.add(rs.getObject(1, UUID.class));
        });
        return ids;
    }

    /** Papel de cada veículo da lista para a pessoa (sem acesso não entra). */
    @Transactional(readOnly = true)
    public Map<UUID, VehicleRole> roles(Collection<Vehicle> list, UUID userId) {
        Map<UUID, Grant> grants = grants(userId);
        Map<UUID, VehicleRole> roles = new HashMap<>();
        for (Vehicle v : list) {
            if (v.getUserId().equals(userId)) roles.put(v.getId(), VehicleRole.OWNER);
            else if (grants.containsKey(v.getId())) roles.put(v.getId(), grants.get(v.getId()).role());
        }
        return roles;
    }

    static NotFoundException notFound() {
        return new NotFoundException("Veículo não encontrado.");
    }

    private static Grant best(Grant a, Grant b) {
        VehicleRole role = a.role().atLeast(b.role()) ? a.role() : b.role();
        String group = a.groupName() == null || b.groupName() == null ? null : a.groupName();
        return new Grant(role, group);
    }

    private static BusinessException forbidden(VehicleRole needed) {
        String message = switch (needed) {
            case LOG -> "Você só pode ver este veículo.";
            case EDIT -> "Só quem pode editar o veículo faz isso.";
            default -> "Só o dono do veículo faz isso.";
        };
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }
}
