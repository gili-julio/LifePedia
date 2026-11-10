package com.lifepedia.api.admin;

import com.lifepedia.api.admin.AdminDtos.ActiveAnnouncement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Avisos ativos, para qualquer pessoa logada (faixa no topo do app). */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    public List<ActiveAnnouncement> active() {
        return service.active();
    }
}
