package com.lifepedia.api.chat;

import com.lifepedia.api.chat.ChatDtos.EditRequest;
import com.lifepedia.api.chat.ChatDtos.MessagePage;
import com.lifepedia.api.chat.ChatDtos.MessageRequest;
import com.lifepedia.api.chat.ChatDtos.MessageResponse;
import com.lifepedia.api.group.NoGroupActivity;
import com.lifepedia.api.shared.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class ChatController {

    private final ChatService service;
    private final ChatStreams streams;

    public ChatController(ChatService service, ChatStreams streams) {
        this.service = service;
        this.streams = streams;
    }

    /** SSE com as mensagens de todos os grupos de quem está logado (evento {@code message}). */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        // nginx não segura os eventos no buffer.
        response.setHeader("X-Accel-Buffering", "no");
        return streams.open(CurrentUser.id(jwt));
    }

    /** Não lidas por grupo ({@code {groupId: total}}; grupo sem não lidas fica de fora). */
    @GetMapping("/chat/unread")
    public Map<UUID, Long> unread(@AuthenticationPrincipal Jwt jwt) {
        return service.unread(CurrentUser.id(jwt));
    }

    /** Histórico por cursor: {@code before} = id da mais antiga já carregada. */
    @GetMapping("/{groupId}/chat/messages")
    public MessagePage messages(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                @RequestParam(required = false) UUID before, @RequestParam(required = false) Integer size) {
        return service.page(CurrentUser.id(jwt), groupId, before, size);
    }

    @PostMapping("/{groupId}/chat/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                @Valid @RequestBody MessageRequest request) {
        return service.send(CurrentUser.id(jwt), groupId, request);
    }

    @PutMapping("/{groupId}/chat/messages/{id}")
    public MessageResponse edit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id,
                                @Valid @RequestBody EditRequest request) {
        return service.edit(CurrentUser.id(jwt), groupId, id, request);
    }

    @DeleteMapping("/{groupId}/chat/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id) {
        service.delete(CurrentUser.id(jwt), groupId, id);
    }

    /** Marca o chat como lido até agora. */
    @PostMapping("/{groupId}/chat/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @NoGroupActivity
    public void read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        service.markRead(CurrentUser.id(jwt), groupId);
    }
}
