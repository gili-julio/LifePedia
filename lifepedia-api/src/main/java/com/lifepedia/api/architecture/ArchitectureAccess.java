package com.lifepedia.api.architecture;

import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Papel de cada pessoa num projeto: dono, compartilhado direto com ela ou com um grupo de que ela é membro (vale o
 * maior). Sem acesso → 404 (não revela que o projeto existe); papel menor que o pedido → 403.
 */
@Component
public class ArchitectureAccess {

    /** Projeto e o papel de quem pediu. */
    public record Access(ArchProject project, ArchRole role) {

        public boolean can(ArchRole needed) {
            return role.atLeast(needed);
        }
    }

    /** Compartilhamento com a pessoa: maior papel e o nome do grupo quando vem só por grupo. */
    public record Grant(ArchRole role, String groupName) {
    }

    private static final String ROLES = """
            select s.role from arch_shares s where s.project_id = :project and s.user_id = :user
            union all
            select s.role from arch_shares s join group_members m on m.group_id = s.group_id
             where s.project_id = :project and m.user_id = :user
            """;

    private static final String GRANTS = """
            select s.project_id, s.role, null as group_name from arch_shares s where s.user_id = :user
            union all
            select s.project_id, s.role, g.name from arch_shares s
              join group_members m on m.group_id = s.group_id
              join user_groups g on g.id = s.group_id
             where m.user_id = :user
            """;

    private final ArchProjectRepository projects;
    private final JdbcClient jdbc;

    public ArchitectureAccess(ArchProjectRepository projects, JdbcClient jdbc) {
        this.projects = projects;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Access require(UUID projectId, UUID userId, ArchRole needed) {
        ArchProject project = projects.findById(projectId).orElseThrow(ArchitectureAccess::notFound);
        ArchRole role = roleOf(project, userId);
        if (role == null) throw notFound();
        if (!role.atLeast(needed)) throw forbidden(needed);
        return new Access(project, role);
    }

    /** Papel da pessoa no projeto; nulo sem acesso. */
    @Transactional(readOnly = true)
    public ArchRole roleOf(ArchProject project, UUID userId) {
        if (project.getUserId().equals(userId)) return ArchRole.OWNER;
        return jdbc.sql(ROLES).param("project", project.getId()).param("user", userId)
                .query((rs, n) -> ArchRole.valueOf(rs.getString(1))).list().stream()
                .max(Enum::compareTo).orElse(null);
    }

    /** Projetos compartilhados com a pessoa (inclusive os dela via grupo; quem lista filtra). */
    @Transactional(readOnly = true)
    public Map<UUID, Grant> grants(UUID userId) {
        Map<UUID, Grant> grants = new HashMap<>();
        jdbc.sql(GRANTS).param("user", userId).query((RowCallbackHandler) rs -> grants.merge(rs.getObject(1, UUID.class),
                new Grant(ArchRole.valueOf(rs.getString(2)), rs.getString(3)), ArchitectureAccess::best));
        return grants;
    }

    private static Grant best(Grant a, Grant b) {
        ArchRole role = a.role().atLeast(b.role()) ? a.role() : b.role();
        String group = a.groupName() == null || b.groupName() == null ? null : a.groupName();
        return new Grant(role, group);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Projeto não encontrado.");
    }

    private static BusinessException forbidden(ArchRole needed) {
        String message = switch (needed) {
            case COMMENT -> "Você só pode ver este projeto.";
            case EDIT -> "Só quem pode editar o projeto faz isso.";
            default -> "Só o dono do projeto faz isso.";
        };
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }
}
