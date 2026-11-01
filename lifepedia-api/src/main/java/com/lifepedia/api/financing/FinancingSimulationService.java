package com.lifepedia.api.financing;

import com.lifepedia.api.financing.FinancingDtos.SavedSimulation;
import com.lifepedia.api.financing.FinancingDtos.SimulationData;
import com.lifepedia.api.financing.FinancingDtos.SimulationRequest;
import com.lifepedia.api.financing.FinancingDtos.SimulationSaveRequest;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Simulações salvas (pessoais ou do grupo); guardam só o pedido, refeito com as taxas do dia ao abrir. */
@Service
public class FinancingSimulationService {

    static final int MAX_SIMULATIONS = 100;

    private final FinancingSimulationRepository simulations;
    private final UserRepository users;
    private final JsonMapper json;

    public FinancingSimulationService(FinancingSimulationRepository simulations, UserRepository users, JsonMapper json) {
        this.simulations = simulations;
        this.users = users;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<SavedSimulation> list(FinancingScope scope) {
        FinancingOwner owner = scope.owner();
        List<FinancingSimulation> items = owner.isGroup() ? simulations.findByGroupIdOrderByCreatedAtDesc(owner.groupId())
                : simulations.findByUserIdOrderByCreatedAtDesc(owner.userId());
        Map<UUID, User> people = users.findAllById(items.stream().map(FinancingSimulation::getCreatedBy)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return items.stream().map(s -> saved(scope, s, people.get(s.getCreatedBy()))).toList();
    }

    @Transactional
    public SavedSimulation save(FinancingScope scope, SimulationSaveRequest request) {
        FinancingOwner owner = scope.owner();
        long count = owner.isGroup() ? simulations.countByGroupId(owner.groupId()) : simulations.countByUserId(owner.userId());
        if (count >= MAX_SIMULATIONS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_SIMULATIONS + " simulações salvas.");
        }
        FinancingSimulation simulation = simulations.save(new FinancingSimulation(owner, scope.userId(),
                request.name().trim(), json.writeValueAsString(request.input())));
        return saved(scope, simulation, users.findById(scope.userId()).orElse(null));
    }

    /** Só quem salvou ou admin. */
    @Transactional
    public void delete(FinancingScope scope, UUID id) {
        FinancingOwner owner = scope.owner();
        FinancingSimulation simulation = simulations.findById(id)
                .filter(s -> owner.isGroup() ? owner.groupId().equals(s.getGroupId())
                        : s.getGroupId() == null && owner.userId().equals(s.getUserId()))
                .orElseThrow(() -> new NotFoundException("Simulação não encontrada."));
        if (!scope.canDelete(simulation.getCreatedBy())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Só quem salvou ou um administrador pode excluir.");
        }
        simulations.delete(simulation);
    }

    @Transactional(readOnly = true)
    public List<SimulationData> export(UUID userId) {
        return simulations.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(s -> new SimulationData(s.getName(), input(s), s.getCreatedAt())).toList();
    }

    private SavedSimulation saved(FinancingScope scope, FinancingSimulation s, User creator) {
        return new SavedSimulation(s.getId(), s.getName(), input(s), creator == null ? null : UserSummary.from(creator),
                s.getCreatedAt(), scope.canDelete(s.getCreatedBy()));
    }

    private SimulationRequest input(FinancingSimulation s) {
        return json.readValue(s.getInput(), SimulationRequest.class);
    }
}
