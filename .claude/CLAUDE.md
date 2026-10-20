# ranking-beach-tennis — Instruções para Claude

Backend do aplicativo de ranking individual de beach tennis organizado em **ligas** (cada liga
tem membros, temporadas, partidas e ranking próprios; D-017 a D-023). Spring Boot 4.x, Java 25,
PostgreSQL, Flyway. Mapa de endpoints: [`docs/api.md`](../docs/api.md). O app Flutter vive em repositório separado
(`C:\Users\luche\IdeaProjects\ranking-beach-tennis-app`), mantido por outra sessão:
**não altere o repositório do app**. Pedidos de mudança na API chegam por meio do usuário.

## Regras de trabalho

- **Só implemente o que estiver aprovado em `docs/decisions.md`** (seção "Approved").
  Os itens de "Technical defaults" são escolhas técnicas já aceitas; qualquer decisão
  ausente ou na seção "Proposed" deve ser discutida antes.
- Trabalho incremental: ao fim de cada etapa, apresente o que foi feito, os arquivos
  principais e o resultado real de `./mvnw verify`, e aguarde aprovação antes de avançar.
- Nunca afirme que testes passaram sem executá-los.
- Commits somente quando o usuário pedir.
- **Fluxo de branches obrigatório** (detalhes em [`CONTRIBUTING.md`](../CONTRIBUTING.md)):
  - `main` = produção (tag `vX.Y.Z` a cada release, versão do `pom.xml`); `dev` = integração.
  - **Nunca commitar direto em `dev` ou `main`.** Toda mudança nasce de uma branch
    `feat/`, `fix/`, `refactor/` ou `chore/<nome-em-kebab-case>` criada a partir da `dev`
    atualizada, vai para a `dev` por pull request e é apagada depois do merge.
  - A `main` só recebe pull request vindo da `dev` (check `source-branch-is-dev`).
  - Depois de todo merge na `main`, a `dev` é sincronizada automaticamente pelo workflow
    `sync-dev-with-main` (PR `main` → `dev` com merge commit). Confira que
    `git log origin/dev..origin/main` ficou vazio; se o workflow falhar, faça o PR à mão.
  - Antes de commitar, confira a branch atual (`git branch --show-current`); se estiver em
    `dev` ou `main`, crie a branch de trabalho primeiro.
- Idiomas:
  - Conversa com o usuário: **pt-BR**.
  - Código, nomes, comentários, documentação técnica e **mensagens de commit: inglês**
    (requisito original do projeto e padrão de todo o histórico).
  - Textos exibidos ao usuário final: **pt-BR** — mensagens de erro da API
    (`entrypoint/handler/ErrorMessages`) e textos das notificações
    (`core/usecase/notification/MatchNotificationComposer`).
- Não criar recursos na nuvem, contratar serviços ou gerar custos sem aprovação explícita.

## Arquitetura

Clean Architecture por pacote sob `com.lucchetta`:

| Pacote | Responsabilidade |
|---|---|
| `core` | Domínio, casos de uso, portas. **Zero** dependência de Spring, banco ou infra. |
| `dataprovider` | Implementações das portas: PostgreSQL (JPA + JdbcClient) e FCM push. |
| `entrypoint` | Controllers REST, DTOs, validação de entrada, tratamento de erros. |
| `configuration` | Fiação de use cases, segurança (OAuth2/Firebase), OpenAPI, push. |

Regras:
- `core` nunca importa nada de fora de `core`.
- Dependências fluem para dentro: `entrypoint` → `core`, `dataprovider` → `core`, `configuration` → todos.
- Pontos e estatísticas são **derivados** (D-007): apenas partidas são persistidas; nada de
  totais acumulados. `MatchDetailsAssembler` é o único lugar que aplica elegibilidade e
  pontuação a uma partida; rankings e estatísticas são calculados em tempo de consulta por
  `RankingCalculator` / `StatisticsCalculator` a partir desses detalhes.
- Operações administrativas (correção, anulação, reversão) são auditadas com o estado
  completo antes/depois e geram notificações na mesma transação; o push é enviado depois,
  como cópia sem garantia.
- Toda temporada e partida pertence a uma liga (`league_id`). Casos de uso com escopo de liga
  recebem o `leagueId` e checam acesso via `LeagueAccess` (membro, administrador da liga ou
  administrador geral); conteúdo de uma liga é só para membros (D-020). Entrada de membros passa
  sempre por `LeagueAdmission` (limite de 50, cancela pedidos/convites pendentes); mudanças de
  membros travam a linha da liga (`findByIdForUpdate`).
- Endpoints sem liga (app antigo) respondem nos mesmos controllers com `leagueId` opcional e
  usam a liga "Beach Tennis Arena" (`DefaultLeague`), via `LeagueScope` (D-023).

## Stack

- Java 25, Spring Boot 4.1.x, Spring Security (OAuth2 Resource Server)
- PostgreSQL 17, Flyway (migrações em `src/main/resources/db/migration`)
- JPA/Hibernate (validate — nunca gera DDL); JdbcClient para partidas, auditoria e notificações
- Jackson 3 (`tools.jackson`)
- Firebase Authentication (JWT), FCM opcional para push (desligado por padrão)
- springdoc-openapi 3.x, Swagger UI em `/swagger-ui.html`
- Testes: JUnit 5, Mockito (como agente JVM), Testcontainers (ITs com PostgreSQL real)

## Desenvolvimento local

```bash
docker compose up -d                        # sobe só o PostgreSQL
docker compose --profile local up -d --build  # PostgreSQL + API em container (localhost:8080)
./mvnw verify                               # compila + testes unitários + ITs (requer Docker)
./mvnw spring-boot:run                      # alternativa: API pelo Maven em localhost:8080
```

O profile `local` lê a configuração do `.env` (no `.env` local: `ADMIN_AUTH_SUBJECT` com o UID
do usuário, `API_DOCS_ENABLED=true`, `CORS_ALLOWED_ORIGINS=http://localhost:5000`).

No Windows, use `.\mvnw.cmd` com `JAVA_HOME=C:\Users\luche\.jdks\corretto-25.0.4.1`
(o `java` do PATH é o 21). O `.env` é obrigatório (copie de `.env.example`).
Deploy em servidor: mesmo `docker-compose.yml` com `--profile prod` (API + Caddy/HTTPS + backup
diário) — ver `docs/deployment.md`. Enquanto o backend local roda a partir de `target/`, o
Windows bloqueia o jar e `./mvnw verify` falha no repackage: pare-o ou rode em uma cópia.

## Variáveis de ambiente

| Variável | Descrição | Padrão |
|---|---|---|
| `FIREBASE_PROJECT_ID` | ID do projeto Firebase (texto) | `ranking-beach-tennis` |
| `ADMIN_AUTH_SUBJECT` | UID Firebase do administrador | vazio (sem admin) |
| `DATABASE_URL` | JDBC URL do PostgreSQL | docker-compose local |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Credenciais do banco | `ranking` |
| `API_DOCS_ENABLED` | Expõe Swagger/OpenAPI | `true` (`false` no servidor) |
| `CORS_ALLOWED_ORIGINS` | Origens exatas do app web, separadas por vírgula (CORS só em `/api/**`, sem credenciais) | vazio (sem acesso por navegador) |
| `PUSH_FCM_ENABLED` | Ativa push via FCM | `false` |
| `PUSH_FCM_CREDENTIALS_FILE` | Caminho para o service account JSON do Firebase (fora do repositório) | — |

## Segurança

- Autenticação via Firebase JWT (validado em `FirebaseTokenValidators`).
- Dois papéis (D-018): **administrador da liga** (`league_member.role = ADMIN`) e **administrador
  geral** (`sub` do token igual a `ADMIN_AUTH_SUBJECT`, em `AdminAwareAuthoritiesConverter`),
  que age como administrador em qualquer liga. Roles **nunca** vêm de claims do token.
  `/api/admin/**` exige o administrador geral; as demais permissões são checadas nos casos de
  uso (`LeagueAccess`).
- Verificação de e-mail **não** é obrigatória.

## Convenções de código

- Sem Lombok — use records Java para DTOs e value objects imutáveis.
- `core` usa apenas Java puro; sem anotações Spring dentro de `core`.
- Portas são interfaces em `core/port`; adapters ficam em `dataprovider`.
- Casos de uso recebem dependências pelo construtor (injeção via `UseCaseConfiguration`).
- Exceções de negócio estendem `BusinessException` (em `core/exception`) com um código
  estável; o subtipo define o status HTTP (`RuleViolationException` 400,
  `NotFoundException` 404, `ForbiddenException` 403, `ConflictException` 409). Todo código
  novo precisa de mensagem pt-BR em `ErrorMessages`.
- Migrações Flyway nomeadas `V{n}__{descricao_snake_case}.sql`; nunca altere uma migração
  já aplicada.
- DTOs de entrada: use tipos wrapper (`Boolean`, `Integer`) para campos opcionais — o
  Jackson 3 recusa primitivos ausentes no JSON.

## Testes

- Testes unitários puros (sem Spring): exercem `core` com fakes in-memory em
  `src/test/java/com/lucchetta/core/support/` (ex: `InMemoryMatches`, `InMemoryPlayers`,
  `InMemoryNotifications`).
- ITs (`*IT`): estendem a classe base abstrata `IntegrationTest` (contexto Spring completo,
  um único PostgreSQL via Testcontainers compartilhado, tokens Firebase simulados).
  Ao criar tabelas, inclua-as no `truncate` de `IntegrationTest.cleanDatabase()`.
- Mockito roda como agente JVM (configurado no `pom.xml`); prefira fakes in-memory a mocks.

## Armadilhas do ambiente

- PowerShell 5.1 quebra argumentos com aspas duplas em comandos nativos (ex.: `git commit -m`
  com aspas na mensagem): use `git commit -F <arquivo>`.
- `Get-Content`/`Set-Content` sem encoding explícito corrompem acentos; prefira as
  ferramentas de edição ou bash.

## Referências rápidas

- Regras de negócio aprovadas: [`docs/decisions.md`](../docs/decisions.md)
- Fórmula de pontos: `GamesPlusWinBonusScoringPolicy` — **único lugar** onde a fórmula existe (D-003).
- Política de elegibilidade: `RankingEligibilityPolicy` (convidados → D-004; dias da semana da temporada → D-025).
- Validação de placar: `Score` / `MatchFormat` (D-002).
- Cálculo de ranking: `RankingCalculator`, ordem de desempate em D-013.
- Possível duplicata e idempotência do registro: `RegisterMatch` (D-012).
- Correção/anulação/reversão: `MatchAdministration`.
- Ligas: `core/usecase/league` (`LeagueAccess`, `LeagueAdmission`, `LeagueMembership`, `JoinRequests`,
  `LeagueInvitations`, `InviteCodes`); fiação em `LeagueUseCaseConfiguration`.
- Estatísticas globais do próprio usuário: `GetMyStatistics` (D-022).
