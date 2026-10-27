package com.lifepedia.api.achievement;

import com.lifepedia.api.achievement.AchievementDtos.AchievementResponse;
import com.lifepedia.api.shared.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/achievements")
public class AchievementController {

    private final AchievementService service;

    public AchievementController(AchievementService service) {
        this.service = service;
    }

    /** Conquistas de quem está logado (todas, inclusive as que amigos não veem). */
    @GetMapping
    public List<AchievementResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.of(CurrentUser.id(jwt), true);
    }
}
