# AGENTS.md — lifepedia-api

Leia antes o [`../AGENTS.md`](../AGENTS.md) (regras gerais, **segurança** e a regra de **documentação sempre
atualizada**). Aqui ficam só as regras da API. Mudou endpoint, variável, estrutura ou convenção? Atualize este
arquivo, o `README.md` desta pasta e o `.env.example` da raiz na mesma tarefa.

## Stack

- **Spring Boot 4.1** / Spring Framework 7 / Spring Security 7 / Hibernate 7 / **Jackson 3** (pacote `tools.jackson`,
  não `com.fasterxml` — exceto as anotações, que continuam em `com.fasterxml.jackson.annotation`). Java 21. Maven (`./mvnw`).
- Starters do Boot 4 são modulares: `spring-boot-starter-webmvc`, `...-security-oauth2-resource-server`, etc.
  Testes usam os `*-test` correspondentes (ex.: `AutoConfigureMockMvc` está em
  `org.springframework.boot.webmvc.test.autoconfigure`).
- Extras: Apache HttpClient 5 (versão do Boot; `DnsResolver` próprio no `SafeHttpClient`), jsoup (HTML de lojas) e
  TwelveMonkeys `imageio-webp` (ler fotos WebP; `Images` registra com `ImageIO.scanForPlugins()`).

## Segurança (resumo técnico)

- Resource server validando JWT HS256 emitido pela própria API (`TokenService`), com validação de `iss`.
  O `sub` é o UUID do usuário → `CurrentUser.id(jwt)`. **Não coloque dados pessoais em claims.**
- `SecurityConfig.bearerTokenResolver`: lê `Authorization: Bearer` ou o cookie `lifepedia_session`; ignora o token
  nas rotas públicas de auth (um cookie vencido não pode bloquear o login).
- `SecurityFilters.RequireCustomHeaderFilter`: CSRF — escrita sem `Authorization` exige `X-Requested-With`.
- `SecurityFilters.AuthRateLimitFilter` + `RateLimiter` (memória): limite por IP em login/cadastro.
  `AuthService.login`: bloqueio por e-mail após N falhas, hash "dummy" para tempo constante, upgrade de hash BCrypt.
- `server.forward-headers-strategy: native` — o IP do cliente vem do `X-Forwarded-For` que o nginx define.
- Sem segredo padrão: `JWT_SECRET` e `DB_PASSWORD` são obrigatórios (perfil `dev` lê `../.env`).
- `jwtDecoder` também valida que o usuário do `sub` existe (conta excluída → 401). Cookie de sessão só por
  `SessionCookies` (login, logout, excluir conta).
- Ação sensível de quem já está logado (ex.: excluir conta) confirma a senha com `AuthService.verifyPassword`
  (limite de erros por usuário).
- Nova regra de segurança ⇒ novo caso em `SecurityIntegrationTest`.

## Convenções

- Comentários: regra da raiz (só o essencial, Javadoc de uma linha). Não edite migrations já aplicadas, nem para
  mudar comentário — o Flyway valida o checksum e a API não sobe.
- **Pacote por módulo** (`auth`, `user`, `finance/...`), não por camada. Cada feature tem entity, repository,
  service, controller e `*Dtos` (records aninhados) juntos.
- Entidades estendem `shared.BaseEntity` (UUID + `created_at`/`updated_at`). Sem Lombok; getters/setters explícitos.
  Relações são colunas UUID (`userId`, `categoryId`), não `@ManyToOne`.
- Controllers finos: recebem `@AuthenticationPrincipal Jwt jwt`, validam com `@Valid` e delegam ao service.
- Services com `@Transactional` (`readOnly = true` em leitura). Buscar por id **sempre** com o usuário
  (`findByIdAndUserId`) e lançar `NotFoundException` → 404.
- Id de outra entidade vindo no corpo (ex.: `categoryId`) → validar dono e tipo (`CategoryService.requireUsable`) → 400.
- Regra de negócio violada → `BusinessException(HttpStatus, "mensagem em pt-BR")`.
- Mensagens de validação em pt-BR nas anotações dos DTOs.
- `ddl-auto: validate`: o schema vem **só** do Flyway. Nova mudança = nova migration `V<n+1>__descricao.sql`.
  Nunca altere migration já aplicada em algum ambiente.
- `YearMonth` persiste como DATE (dia 1) via `finance.YearMonthConverter`.
- Dados iniciais de um módulo para usuário novo: `@EventListener` de `UserRegisteredEvent` (síncrono, mesma transação).
- Categorias padrão: `finance/category/DefaultCategories.java`.
- **Listagens**: `GET /recurso` sempre paginado com `Paging.pageable(page, size, sort, CAMPOS, padrão, desempate...)`
  e `PageResponse.of(...)`. `CAMPOS` é um `Map` fechado (nome público → expressão JPQL); nunca repasse `sort` cru.
  A consulta é `@Query` com alias (`t`, `c`...) + `countQuery`; desempate estável (ex.: `t.id`). Lista completa só em
  `GET /recurso/all`. Ordenar/paginar é sempre na consulta, nunca em memória.
- Ocasional conta no `month` (≥ mês da `date`), não na data. Filtrar sempre por `month`.
- **Grupos**: toda rota de grupo passa por `GroupAccess.requireMember` (404) ou `requireAdmin` (403). Quem sai/é
  removido dispara `GroupMemberRemovedEvent` (síncrono) — cada módulo apaga os itens dele no grupo.
- **Itens de grupo sincronizados**: editou fixo/ocasional pessoal? Chame `GroupFinanceSync` (já feito em
  `TransactionService.update`/`RecurringEntryService.update`); categoria reatribuída também vale para os grupos.
  Exclusão cascateia pela FK `source_id`.
- **Arquivos**: imagens só por `shared/image` (`Images` valida bytes/dimensões e regrava JPEG, `Images.uploadBytes`
  lê o multipart; `ImageStore` grava com nome aleatório, apaga o arquivo novo se a transação falhar e o antigo só
  depois do commit; `ImageFile.response()` serve com cache e ETag) — ex.: `UserImageStore`, `GroupImageStore`,
  `PlanImageStore`, cada um numa subpasta de `lifepedia.storage.dir` (`STORAGE_DIR`). Trocou ou removeu a imagem?
  `deleteAfterCommit(anterior)`: nada de arquivo órfão no disco. Nunca sirva arquivo pelo nome vindo do cliente. Grupo
  excluído publica `GroupDeletedEvent`: quem guarda arquivo do grupo apaga os seus (o banco apaga as linhas em
  cascata). Conta excluída apaga a foto de perfil (`UserService.deleteImageAfterCommit`).
- **URL vinda do usuário**: acessar só por `shared/net/SafeHttpClient` (IP público checado na conexão, portas 80/443,
  redirecionamentos revalidados, tempo/tamanho limitados), fora de transação e com `RateLimiter` por usuário. Novo
  caso de rede interna ⇒ novo teste em `SafeHttpClientTest`.
- **Planejamento**: conta em `PlanningSimulator` (puro, em centavos, testado em `PlanningSimulatorTest`); o serviço só
  monta a base (`GroupSummaryService.points`) e os itens. Leitura de página de loja em `LinkMetadataParser` (JSON-LD,
  Open Graph, microdados, Amazon) — loja nova ⇒ caso em `LinkMetadataParserTest` com HTML de exemplo, sem rede.
  Produto parecido: `PriceReferenceService` (lê `__NEXT_DATA__` da busca do Buscapé) + `ProductMatcher` (números e
  modelos precisam bater; acessório/usado descartado; variante não pedida penalizada) — ajuste de regra ⇒ caso em
  `ProductMatcherTest`. Testes de integração usam `@MockitoBean PriceReferenceService` (nada de rede em teste).
- **DTO de entrada com booleano opcional**: use `Boolean` (Jackson 3 recusa campo `boolean` ausente → 400).
- **Notificações**: `NotificationService.notify(...)` na mesma transação da ação; tipo novo = valor em
  `NotificationType` + texto no front.
- Entidade de grupo chama `UserGroup` (tabela `user_groups`): `Group` é palavra reservada no JPQL.
- Valor de fixo num mês: sempre considerar `RecurringAdjustment` (ver `SummaryService.amountIn`).
- **"Hoje"**: use o `Clock` do contexto (`config/TimeProperties`, fuso `TIME_ZONE`), nunca `LocalDate.now()` sem
  fuso — o container roda em UTC. (Financeiro/planejamento ainda usam o fuso da JVM.)
- **Tarefas** (`task/`): repetição em `Recurrence` (pura, testada em `RecurrenceTest`); a agenda gera as ocorrências e
  junta os registros de `task_occurrences` (só grava o que difere da tarefa; registro que voltou ao padrão é apagado).
  Dono por `TaskOwner` (usuário ou grupo; consultas com `userId = :userId or groupId = :groupId`, o outro nulo).
  Tarefa sem repetição e "a partir da conclusão" têm uma ocorrência só, no `dueDate`; `completed_at` na tarefa marca
  concluída/série encerrada (filtra a agenda). Ordenação da agenda é em memória na API (a lista é gerada, não vem de
  consulta), nunca no front. Teste em `TaskIntegrationTest`.
  Grupo: o mesmo `TaskController` atende `/api/groups/{groupId}/tasks` (`TaskScope` = dono + quem age + admin, via
  `GroupAccess.requireMember`). Responsável da ocorrência só por `TaskService.assignee(task, date, row)` (trocado na
  ocorrência → vez do rodízio → fixo). Avisos só por `TaskNotifier` (não avisa quem agiu nem ex-membro). Rodízio é
  `@OrderColumn` sem `UNIQUE (task_id, user_id)` (o Hibernate reescreve posições). Agendamentos: `@Scheduled` com
  `zone = "${lifepedia.time.zone}"` e lógica num método público testável (ex.: `TaskOverdueJob.notifyOverdue`,
  `TaskReminderJob.remind(from, to)`); `SchedulingConfig` só liga com `lifepedia.scheduling.enabled` (desligado em
  `application-test.yml`: o teste chama o método). Teste em `GroupTaskIntegrationTest`.
  Hábito: conta só em `HabitStats` (pura, `HabitStatsTest`); pulado é neutro. Lembrete: destino por
  `TaskNotifier.reminder`. Fixo ligado: validar com `requireRecurring` (pessoal + do usuário). Teste em
  `TaskExtrasIntegrationTest`.
- **Conta** (`account/`): orquestra outros módulos. Exportar/importar usa `AccountDtos.FORMAT`
  (`lifepedia/account@2`; aceita `AccountDtos.IMPORTABLE` = `@1` e `@2`); mudou o arquivo ⇒ `@3` e importação dos
  anteriores mantida. Tarefas no arquivo: `task/TaskTransferService` (campo ausente = não mexe). Importação valida tudo antes de
  gravar e roda numa transação só (`dryRun` percorre o mesmo caminho sem salvar). Exclusão reaproveita
  `GroupService.leave` (eventos de saída limpam itens e fotos) e depende das FKs `ON DELETE CASCADE/SET NULL` para
  `users` — tabela nova com `user_id` precisa de uma delas. Teste em `AccountIntegrationTest`.

## Testes

- Integração com **Testcontainers** (`TestcontainersConfiguration`, Postgres 17) + `MockMvcTester`, perfil `test`
  (`src/test/resources/application-test.yml`: segredo de teste e limite por IP alto).
- Estenda `ApiTestSupport` (cria usuário, pega categoria por nome, envia requisições autenticadas).
- Requisições sem `Authorization` precisam de `X-Requested-With` (constante `CSRF`), como no navegador.
- Cada teste cria seu próprio usuário com e-mail aleatório; não dependa de ordem nem limpe o banco.
- Cubra sempre: fluxo feliz, validação (400), isolamento entre usuários (404/400) e acesso sem token (401).

```bash
./mvnw test
```

## Rodar local

`docker compose up -d db` na raiz e `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. Stack completo: compose da raiz.
