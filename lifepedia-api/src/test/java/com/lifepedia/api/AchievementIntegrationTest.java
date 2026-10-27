package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.achievement.Achievement;
import com.lifepedia.api.achievement.UserAchievement;
import com.lifepedia.api.achievement.UserAchievementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AchievementIntegrationTest extends ApiTestSupport {

    @Autowired
    UserAchievementRepository unlocked;

    @Test
    void unlockIsRecordedOnActionAndNeverLost() {
        String me = registerAndGetBearer();
        UUID myId = UUID.fromString(userId(me));

        String task = JsonPath.read(post(me, "/api/tasks", """
                {"title":"Ler"}"""), "$.id");
        assertThat(send("PUT", me, "/api/tasks/" + task + "/status", """
                {"date":null,"status":"DONE"}""")).hasStatus(204);
        // Gravado na ação, antes de qualquer leitura.
        assertThat(unlocked.findByUserId(myId)).extracting(UserAchievement::getCode, UserAchievement::getLevel)
                .containsExactly(tuple(Achievement.TASKS_DONE, 1));

        assertThat(send("PUT", me, "/api/tasks/" + task + "/status", """
                {"date":null,"status":"PENDING"}""")).hasStatus(204);
        String mine = body(send("GET", me, "/api/achievements", null));
        assertThat(JsonPath.<List<Integer>>read(mine, "$[?(@.code == 'TASKS_DONE')].level")).containsExactly(1);
        assertThat(JsonPath.<List<Integer>>read(mine, "$[?(@.code == 'TASKS_DONE')].value")).containsExactly(1);
        assertThat(JsonPath.<List<String>>read(mine, "$[?(@.code == 'TASKS_DONE')].unlockedAt")).doesNotContainNull();
        assertThat(JsonPath.<List<Object>>read(mine, "$[?(@.code == 'FRIENDS')].unlockedAt")).containsOnlyNulls();

        // Grupos: 3 (nível 2); sair de 2 não tira o nível.
        List<String> groups = List.of("A", "B", "C").stream()
                .map(name -> JsonPath.<String>read(post(me, "/api/groups", """
                        {"name":"%s"}""".formatted(name)), "$.id"))
                .toList();
        send("POST", me, "/api/groups/" + groups.get(0) + "/leave", null);
        send("POST", me, "/api/groups/" + groups.get(1) + "/leave", null);
        mine = body(send("GET", me, "/api/achievements", null));
        assertThat(JsonPath.<List<Integer>>read(mine, "$[?(@.code == 'GROUPS')].level")).containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(mine, "$[?(@.code == 'GROUPS')].target")).containsExactly(5);
    }
}
