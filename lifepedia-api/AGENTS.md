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
- `server.forward-headers-strategy: native` — o IP do cliente vem do `X-Forwarded-For` que o nginx define (o nginx
  já resolve o IP real atrás do proxy do Coolify com `real_ip`; a API nunca é exposta direto).
- Sem segredo padrão: `JWT_SECRET` e `DB_PASSWORD` são obrigatórios (perfil `dev` lê `../.env`).
- `jwtDecoder` também valida que a sessão do `sid` existe e não venceu (`user_sessions`; logout e conta excluída
  apagam → 401). Sessão e token nascem juntos em `TokenService.issue(user, remember)`; logout chama `revoke`.
  `SessionCleanupJob` apaga as vencidas (3h30).
- Google: `GoogleTokenVerifier` (chaves públicas do Google, `iss`, `aud` = `GOOGLE_CLIENT_ID`, validade) → `GoogleIdentity`.
  Em teste, `@MockitoBean GoogleTokenVerifier` (nada de rede). Conta sem senha (`password_hash` nulo) nunca entra por
  senha (o hash "dummy" só mantém o tempo). Cookie de sessão só por `SessionCookies` (login, logout, excluir conta).
- Ação sensível de quem já está logado (ex.: excluir conta) confirma com `AuthService.confirmIdentity` (senha via
  `verifyPassword`, com limite de erros por usuário; conta sem senha: ID token do Google de até 10 min com o `sub`
  vinculado).
- Nova regra de segurança ⇒ novo caso em `SecurityIntegrationTest`.

## Convenções

- Estilo verificado pelo Checkstyle (ver Testes). Comentários: regra da raiz (só o essencial, Javadoc de uma linha). Não edite migrations já aplicadas, nem para
  mudar comentário — o Flyway valida o checksum e a API não sobe.
- **Importação pelo site** (`finance/importing`): `ImportAssistantService.analyze` valida tudo antes (`ImportDocuments`,
  com PDFBox para páginas e senha; `ImportQuota`, pura, testada em `ImportQuotaTest`), grava a linha e chama o Gemini num
  thread virtual, sem transação; no máximo `MAX_RUNNING` leituras juntas (segura os arquivos na memória). Fim e falha só por
  `ImportDraftService.complete/fail`, que notificam na mesma transação. `ImportDraftJob` encerra leitura perdida (a API
  reiniciou) e apaga rascunho esquecido. Prompt só em `ImportPrompt` (`ImportPromptTest`). HTTP só por `shared/ai/GeminiClient`
  (`java.net.http`; `GeminiClientTest` com servidor local). Testes de integração usam `@MockitoBean GeminiClient`
  (`ImportAssistantIntegrationTest`), sem rede. Limite `multipart` da API é o da importação; imagens têm 1 MB em
  `Images.uploadBytes`. Tudo fica na memória (`file-size-threshold`): documento não passa pelo disco.
- **Admin** (`admin/`): acesso por `AdminAccess` (registrado em `SecurityConfig` para `/api/admin/**`) e de novo no
  `AdminController.admin(jwt)`; nunca use só um dos dois. Números por `AdminStats` (`JdbcClient`, só `count`, nunca
  conteúdo; tabela nova de módulo? Inclua em `MODULES` e `USER_COUNTS` e no `features/admin/labels.ts`). Ação de admin =
  `AuditService.record` na mesma transação. Bloqueio e "encerrar sessões" por `TokenService.revokeAll` (publica
  `SessionsRevokedEvent`). `last_seen_at` só pelo `UserRepository.touchLastSeen` (nativo; a entidade não grava a coluna).
  Uso da IA só por `AiUsageLog.record`. Limites dos serviços (`AdminService.limits`, sem transação: lê a cota do 17TRACK
  pela fachada `delivery/TrackingQuota`); serviço novo com cota = linha em `limits` + chave em `LimitKey`/`LIMIT_LABEL` no
  web. Teste em `AdminIntegrationTest` (o perfil de teste fixa `lifepedia.admin.email`).
- **Arquitetura** (`architecture/`): acesso só por `ArchitectureAccess.require(projeto, usuário, papel mínimo)` (dono,
  compartilhamento direto ou por grupo do qual a pessoa é membro na hora; o maior papel vale). Arquivos só por
  `ArchitectureFiles` (`architecture/` no `STORAGE_DIR`, chave aleatória + extensão do `Type`; `receiveModel` copia o
  corpo cru com teto e confere o GLB; `save` apaga se a transação falhar; `deleteAfterCommit`). Upload da maquete:
  reserva em transação → arquivo fora de transação → fecha a versão numa transação (`TransactionTemplate`), apagando o
  arquivo se ela falhar. Espaço do dono (`ArchProjectRepository.usedBytes`: versões, inclusive reservadas, + anexos) por
  `ArchitectureService.requireRoom`. Conta excluída: `deleteUserFilesAfterCommit` (o banco apaga em cascata). Rota
  pública só em `PublicModelController` (`/api/public/architecture/{token}`, liberada em `SecurityConfig`, sem ler o
  cookie, limite por IP). Cena por versão (`ArchSceneService`: variantes com os ajustes em JSON validado por `MaterialOverride`, cômodos com o
  contorno em JSON, luzes; FK em cascata com a versão) entra no link público (`sceneOf`) e no arquivo da conta (só os
  nomes). Teste em `ArchitectureIntegrationTest` (perfil de teste: espaço de 1 MB e maquete de 600 KB).
- **Integrações** (`integration/`): token só pelo `SecretBox` (`seal`/`open`; sem `INTEGRATIONS_KEY` só o Duolingo
  funciona). Chamada ao serviço só por `ApiCalls` (`SafeHttpClient.exchange`: sem redirect, IP público, 443) e fora de
  transação; o que grava vai num `TransactionTemplate` depois. Serviço novo = cliente + `AccountSyncer` (`supports`,
  `sync(Connection)`, `usage`) + valor em `IntegrationProvider` + textos no front. `IntegrationSyncService.sync` evita duas
  ao mesmo tempo na mesma conta; `AUTH` = conta em `ERROR` + aviso uma vez, o resto só no histórico (20 por conta).
  Outros módulos usam só `IntegrationLinks` (conta da pessoa, `Connection`, `codeHost`, agenda, Duolingo). Testes em
  `IntegrationIntegrationTest` e `DevIntegrationTest` com `@MockitoBean` dos clientes (`provider()` precisa de stub: a
  lista de `CodeHost` filtra por ele), `SecretBoxTest`, `PipelinesTest` e o caso de `exchange` no `SafeHttpClientTest`.
- **Dev** (`dev/`): dono por `findByIdAndUserId`; conta e agenda validadas por `IntegrationLinks`. `DevSyncService` (um
  `AccountSyncer`) busca o retrato, move as raias (`dev_automations`: uma vez por MR e evento, só MR criado/aceito depois
  de `rules_since`) e checa issues sumidas fora de transação; grava tudo numa transação só. Tarefa da issue só por
  `TaskLinks.createPersonal`/`completePersonal`; concluir publica `task/TaskDoneEvent` e `DevTaskListener` fecha a issue
  depois do commit (thread virtual). Aviso só se o repositório já sincronizou antes (`synced_at`). Prompt do daily só em
  `DevDailyPrompt`; uso por `AiUsageLog.DEV_DAILY`.
- **Simulador** (`finance/simulation`): conta só em `FinanceSimulator` (puro, em `double`, saída em `BigDecimal`;
  `FinanceSimulatorTest`): base mês a mês, ganhos e gastos livres, investimento por aporte (cada aporte é um lote com a
  própria idade para o IR regressivo), inflação. `SimulatorService.run`/`compare` ficam **fora de transação** (as taxas
  podem ser buscadas no Banco Central na hora); base pelo `SummaryService.history` (meses futuros trazem os fixos com
  fim e ajustes). Taxas só pela fachada `financing/MarketRates` (CDI e Selic são valores de `CorrectionIndex` com
  `correction = false`: `FinancingService` recusa como correção). Simulação salva guarda só o `Input` em JSON. Teste em
  `SimulatorIntegrationTest` (`@MockitoBean BcbClient`).
- **Entregas** (`delivery/`): ver = dono ou membro do grupo da entrega (`visible`, senão 404); mudar = só o dono (`own`,
  membro → 403). HTTP só por `Track17Client` (17TRACK v2.4, 40 códigos por chamada, 3 por segundo: pausa de 400 ms) e
  `CepClient` (ViaCEP), via `SafeHttpClient`, fora de transação; `DeliverySyncService.sync` evita a mesma entrega em duas
  sincronizações ao mesmo tempo e grava tudo numa transação depois. Leitura do `track_info` só em `TrackSnapshot.parse`
  (puro, `TrackSnapshotTest`: status, eventos, transportadora com link só `http(s)`, previsão, taxa pelo texto). Aviso
  `DELIVERY_UPDATE` só quando muda (`notified_status`), nunca na 1ª leitura; parada uma vez (`stall_warned`, volta a
  valer quando chega evento novo). Fotos por `DeliveryImageStore` (`STORAGE_DIR/deliveries`); conta excluída apaga
  (`deleteUserImagesAfterCommit`). Saiu do grupo → `onMemberRemoved` descompartilha. Cota para o admin pela fachada
  `TrackingQuota`. Teste em `DeliveryIntegrationTest` com `@MockitoBean Track17Client` e `CepClient`.
- **Banco** (`finance/bank`): `BankRef` é `@Embedded` em ocasionais, fixos e itens de grupo; valide com `BankRef.clean`
  antes de gravar e passe nos `sync` do `GroupFinanceSync`. Soma por banco só em `BankReport`, nunca em SQL solto.
  Banco novo no catálogo = valor em `Bank` + entrada e logo no web (`src/lib/banks.ts`).
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
- **Termos (LGPD)**: aceite só por `User.acceptTerms(LegalTerms.VERSION)` (cadastro e `UserService.acceptTerms`);
  `UserResponse.termsAccepted` compara com a versão vigente. Trocar `LegalTerms.VERSION` = todos aceitam de novo (mude
  `LEGAL_VERSION` no web junto). Teste em `UserProfileIntegrationTest`. Cadastro em teste manda `"acceptTerms":true`.
- **DTO de entrada com booleano opcional**: use `Boolean` (Jackson 3 recusa campo `boolean` ausente → 400).
- **Notificações**: `NotificationService.notify(...)` na mesma transação da ação; tipo novo = valor em
  `NotificationType` + texto no front + caso em `PushTexts` (o `switch` não compila sem ele).
- **Push** (`notification/push`): `PushService.send(userIds, PushMessage)` agenda para depois do commit (thread
  virtual; falha só loga). Cifra em `PushCrypto` (RFC 8291/8292, só JDK; testado em `PushCryptoTest` decifrando como o
  navegador). HTTP só pelo `PushGateway` (cliente criado no 1º envio, sem redirect) e só para `allowed-hosts`. Inscrição
  ligada a `user_sessions` (`ON DELETE SET NULL`); envio só com sessão válida (`findActive`). Teste em
  `PushIntegrationTest` com `@MockitoBean PushGateway` e chaves geradas no teste (`@DynamicPropertySource`).
- **Atividade de grupo**: rota nova de escrita sob `/api/groups/{groupId}/...` já conta como atividade
  (`GroupActivityInterceptor`, registrado em `config/WebConfig`); se não for atividade (preferência pessoal, consulta via
  POST), anote o método com `@NoGroupActivity`. Atalhos e fixar: `GroupService.shortcuts`/`pin` (teste em
  `GroupIntegrationTest`).
- **Chat** (`chat/`): `ChatService` (membro por `GroupAccess`, `RateLimiter` 30/min, só o autor edita/apaga, apagar
  limpa o texto); toda escrita publica `ChatEvent` (destinatários = membros no envio) e `ChatStreams` entrega por SSE
  **depois do commit** (`@TransactionalEventListener`), com "ping" a cada 25 s e até 5 conexões por usuário. Em
  memória: uma instância da API só. Histórico por cursor (data + id), não por página. Leitura em `group_chat_reads`
  (upsert nativo, sem entidade). Teste em `ChatIntegrationTest` (`@MockitoSpyBean ChatStreams`; o stream no MockMvc
  com `asyncExchange()`, senão o teste espera o SSE acabar).
- **Investimentos**: categoria por `InvestmentCategories.isInvestment` (só despesa; nome normalizado). Resumos pessoal
  e de grupo separam `invested` em `Totals`/`MonthPoint`; `GroupSummaryService.reserve` soma a reserva do planejamento
  com os investimentos até o mês (fixos mês a mês, com ajustes). Teste em `InvestmentIntegrationTest`.
- **Parcelas**: `finance/Installments` (parse do "(n/total)", `isFollowedBy`, `shift` que barra mês antes da compra;
  puro, `InstallmentsTest`). Mover no pessoal chama `GroupFinanceSync` depois de mudar todos (a sync limpa o contexto
  do JPA).
- **Amigos** (`friend/`): `FriendService` (pedido com `RateLimiter` por usuário, limites `MAX_FRIENDS`/`MAX_PENDING_SENT`);
  amizade sempre buscada por `between(a, b)` (par em qualquer ordem; índice único `LEAST/GREATEST`). Perfil de amigo =
  `AchievementService.of(id, false)`. Teste em `FriendIntegrationTest`.
- **Conquistas** (`achievement/`): nível gravado em `user_achievements` (`UserAchievementRepository.unlock`, insert com
  `on conflict do nothing`) e nunca apagado. Ação que pode subir nível publica `AchievementProgress(userId, códigos)`;
  `AchievementService.onProgress` roda depois do commit em transação própria (falha não desfaz a ação). Ler também
  grava nível novo (por isso `of` e o perfil do amigo não são `readOnly`). Resposta pela função pura
  `AchievementResponse.of` (`AchievementResponseTest`); fluxo em `AchievementIntegrationTest`.
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
  `GroupAccess.requireMember`). Responsáveis da ocorrência só por `TaskService.assignees(task, date, row, everyone)` (trocado na
  ocorrência → vez do rodízio → todos (`members(task)`, só com `allMembers`) → fixos); conclusão sempre por
  `complete` (trata "cada um conclui a sua": `doneBy` na ocorrência até todos). Avisos só por `TaskNotifier` (não
  avisa quem agiu nem ex-membro). Choque de horário em `TaskAgendaService.conflicts` (reaproveita `agenda`; nunca
  mostra tarefa pessoal de outra pessoa). Teste em `TaskAgendaIntegrationTest` (também levar para grupo e
  `agenda-settings`). Rodízio é
  `@OrderColumn` sem `UNIQUE (task_id, user_id)` (o Hibernate reescreve posições). Agendamentos: `@Scheduled` com
  `zone = "${lifepedia.time.zone}"` e lógica num método público testável (ex.: `TaskOverdueJob.notifyOverdue`,
  `TaskReminderJob.remind(from, to)`); `SchedulingConfig` só liga com `lifepedia.scheduling.enabled` (desligado em
  `application-test.yml`: o teste chama o método). Teste em `GroupTaskIntegrationTest`.
  Hábito: conta só em `HabitStats` (pura, `HabitStatsTest`); pulado é neutro. Lembrete: destino por
  `TaskNotifier.reminder`. Fixo ligado: validar com `requireRecurring` (pessoal + do usuário). Teste em
  `TaskExtrasIntegrationTest`.
- **Conta** (`account/`): orquestra outros módulos. Exportar/importar usa `AccountDtos.FORMAT`
  (`lifepedia/account@8`; aceita `AccountDtos.IMPORTABLE` = `@1` a `@8`; frequência, notas, financiamentos, arquitetura, Dev, integrações, simulações e entregas só como registro); mudou o arquivo ⇒ `@9` e importação dos
  anteriores mantida. Tarefas no arquivo: `task/TaskTransferService` (campo ausente = não mexe). Importação valida tudo antes de
  gravar e roda numa transação só (`dryRun` percorre o mesmo caminho sem salvar). Exclusão reaproveita
  `GroupService.leave` (eventos de saída limpam itens e fotos) e depende das FKs `ON DELETE CASCADE/SET NULL` para
  `users` — tabela nova com `user_id` precisa de uma delas. Teste em `AccountIntegrationTest`.
- **Frequência** (`attendance/`): dias gerados pela regra (`Commitment.occursOn`); só a marcação é gravada. Contagem e
  status num `Context` por requisição (marcações + dias concluídos da tarefa ligada). Tarefa só pela fachada
  `task/TaskLinks` (pessoal e do usuário; nunca ler `task_occurrences` direto). "Hoje"/horário pelo `Clock`. Teste em
  `AttendanceIntegrationTest` (período já encerrado para não depender da hora). Importação (`importDays`) valida cada
  dia com a mesma regra da marcação (`checkMark`) numa transação só; a leitura do SIGAA é do front.
  Outros módulos leem compromissos só por `attendance/CommitmentLinks`.
- **Notas** (`note/`): dono por `NoteOwner` (usuário ou grupo, como as tarefas) e quem age por `NoteScope` (admin =
  pessoal ou admin do grupo; lixeira/restaurar/apagar só criador ou admin). Gravação com `version`: `update` trava a
  linha (`NoteRepository.lockById`) e compara; diferente → 409. Tarefa ligada só por `TaskLinks.owned` e compromisso por
  `CommitmentLinks` (nunca as tabelas deles). Busca e `[[título]]` com `like` em minúsculas e escape `!`
  (`escapeLike`). Atividade em `note_activities`: edição da mesma pessoa em 30 min só atualiza a linha
  (`NoteActivityRepository.touch`). Fotos por `NoteImageStore` (1600 px); foto fora do texto há mais de 1 h sai ao salvar;
  apagar de vez, lixeira vencida (`NoteTrashJob` → `purgeTrash`), grupo excluído (`onGroupDeleted`) e conta excluída
  (`deleteUserImagesAfterCommit`) apagam os arquivos. Resumo da lista em `NoteService.excerpt` (`NoteExcerptTest`).
  Teste em `NoteIntegrationTest` (organização, versão, lixeira, fotos, grupo e isolamento).
- **Financiamentos** (`financing/`): dono por `FinancingOwner` e quem age por `FinancingScope` (excluir só quem
  cadastrou ou admin). Cálculo só em `FinancingMath` (puro, em `double`, saída arredondada em `BigDecimal`;
  `FinancingMathTest`): parcelas, amortização extra (`TERM` mantém a parcela, `PAYMENT` o prazo), CET por bisseção e
  `firstPaymentFactor` (parcela é linear no valor financiado). Gastos padrão em `FinancingCosts` (chaves estáveis: o
  front manda o valor editado pela chave). Banco Central só pelo `BcbClient` (via `SafeHttpClient`; falha = vazio, nunca
  exceção) e o resto lê do banco (`FinancingRateService`; busca na hora só se vazio, 1 vez por hora). Nos testes o
  `BcbClient` é `@MockitoBean`. Parcelas sempre calculadas na leitura; nada de parcela gravada. Fixo da parte só pela
  fachada `finance/recurring/ScheduledFixed` (cria/atualiza/apaga com os ajustes mensais) e refeito em `syncFixed` a
  cada mudança (contrato, partes, extras); fixo apagado pela pessoa desliga a ligação. Teste em
  `FinancingIntegrationTest` (simulação, contrato, extras, fixo, gastos, aviso, grupo e isolamento).

## Testes

- Integração com **Testcontainers** (`TestcontainersConfiguration`, Postgres 17) + `MockMvcTester`, perfil `test`
  (`src/test/resources/application-test.yml`: segredo de teste e limite por IP alto).
- Estenda `ApiTestSupport` (cria usuário, pega categoria por nome, envia requisições autenticadas).
- Requisições sem `Authorization` precisam de `X-Requested-With` (constante `CSRF`), como no navegador.
- Cada teste cria seu próprio usuário com e-mail aleatório; não dependa de ordem nem limpe o banco.
- Cubra sempre: fluxo feliz, validação (400), isolamento entre usuários (404/400) e acesso sem token (401).

```bash
./mvnw checkstyle:check   # regras em checkstyle.xml (também no CI)
./mvnw test
```

**Checkstyle** (`checkstyle.xml`, rodado à parte, não no `package`, porque o Dockerfile não copia o arquivo):
linha até 140 (conteúdo de text block liberado), sem tab, sem import coringa/não usado, nomes no padrão Java (logger
estático pode ser `log`), chaves no fim da linha e **nada de nome totalmente qualificado no meio do código** (importe a
classe). O CI (`.github/workflows/api.yml`) roda Checkstyle, testes e build da imagem.

## Rodar local

`docker compose up -d db` na raiz e `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. Stack completo: compose da raiz.
