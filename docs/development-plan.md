# NossaSaúde — Plano de Desenvolvimento

> Plano de execução por etapas para o app Android (Kotlin/Jetpack Compose).
> Cada etapa entrega valor incremental e pode ser validada isoladamente.
> Convenção: código em inglês, strings de UI em pt-BR, docs/commits em pt-BR.

---

## Status das etapas (atualizado 2026-04-15)

| Etapa | Descrição | Status |
|-------|-----------|--------|
| 0 | Setup do projeto | ✅ Completo |
| 1 | Design System | ✅ Completo |
| 2 | Camada de dados local (Room) | ✅ Completo |
| 3 | Camada de rede (Retrofit) | ✅ Completo |
| 4 | Repositórios + Sincronização | ✅ Completo |
| 5 | Tela Home | ✅ Completo |
| 6 | Membro: cadastro, edição e perfil | ✅ Completo |
| 7 | Consultas: cadastro, detalhe, edição | ✅ Completo |
| 8 | Busca (FTS) | ✅ Completo |
| 9 | Visualizador de imagem fullscreen | ✅ Completo |
| 10 | Configurações e polimento | 🔄 Em andamento |

### Observações sobre o estado atual

- Sincronização bidirecional funcionando com serverless-offline + LocalStack (testado em 2026-04-15).
- Sync é **100% manual** — acionada pelo botão na Home ou na tela de Configurações. Não há sync automática ao abrir o app nem periódica em background.
- Upload de imagens para S3 funciona via fila `pending_uploads`, deferred para o momento do sync.
- Em ambiente local com emulador Android: serverless-offline deve rodar com `--host 0.0.0.0`; URLs do LocalStack são remapeadas automaticamente de `localhost` para `10.0.2.2` em builds DEBUG.

---

## Etapa 0 — Setup do Projeto

**Objetivo:** preparar o esqueleto técnico antes de escrever qualquer regra de negócio.

### 0.1 Configuração do Gradle
- Habilitar `buildFeatures { compose = true; buildConfig = true }` no `app/build.gradle.kts`
- Definir `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`
- Configurar Kotlin 2.0+ com plugin `org.jetbrains.kotlin.plugin.compose`
- Habilitar `kotlinOptions { jvmTarget = "17" }`

### 0.2 BuildConfig fields
- `BuildConfig.FAMILY_ID` (String) — UUID v4 do núcleo familiar
- `BuildConfig.API_KEY` (String) — API key do API Gateway
- `BuildConfig.API_BASE_URL` (String) — URL base prod/dev
- Ler valores de `local.properties` (não comitar segredos)
- Variants `debug` (URL dev) e `release` (URL prod)

### 0.3 Dependências
- **Compose:** BOM, material3, compose-ui, compose-foundation, navigation-compose, lifecycle-viewmodel-compose
- **Room:** runtime, ktx, compiler (KSP), `room-fts` para busca
- **Network:** Retrofit, OkHttp logging-interceptor, kotlinx-serialization-converter
- **DI:** Hilt (`hilt-android`, `hilt-compiler` via KSP, `hilt-navigation-compose`)
- **Async:** kotlinx-coroutines-android, kotlinx-coroutines-core
- **Imagens:** Coil 3 (`coil-compose`, `coil-network-okhttp`)
- **Background:** WorkManager + `hilt-work`
- **Persistência leve:** DataStore Preferences
- **Camera:** CameraX (core, camera2, lifecycle, view)
- **Datas:** kotlinx-datetime
- **Testes:** JUnit, MockK, Turbine, Compose UI Test

### 0.4 Estrutura de pacotes
```
br.com.nossasaude/
├── NossaSaudeApp.kt              # @HiltAndroidApp
├── MainActivity.kt
├── di/                           # módulos Hilt
├── data/
│   ├── local/                    # Room: entities, DAOs, database
│   ├── remote/                   # Retrofit: services, DTOs, interceptors
│   ├── repository/               # implementações de repositórios
│   └── mapper/                   # DTO ↔ Entity ↔ Domain
├── domain/
│   ├── model/                    # modelos puros (Member, Consultation, ...)
│   ├── repository/               # interfaces
│   └── usecase/                  # casos de uso
├── sync/                         # SyncManager, UploadWorker
├── ui/
│   ├── theme/                    # Color, Typography, Spacing, Theme
│   ├── components/               # NsAvatar, NsChip, NsFab, ...
│   ├── navigation/               # NavGraph, rotas
│   └── screens/
│       ├── home/
│       ├── member/
│       ├── consultation/
│       ├── search/
│       ├── image/
│       └── settings/
└── util/                         # extensões, formatadores
```

### 0.5 Configuração base do app
- `NossaSaudeApp` com `@HiltAndroidApp` + setup do Coil singleton
- `MainActivity` com `setContent { NossaSaudeTheme { AppNavHost() } }`
- `AppNavHost` com rotas vazias (placeholders das telas)
- ProGuard rules para Retrofit/Room/serialization

### 0.6 Validação
- Build limpo passa
- App abre em tela em branco com tema aplicado
- `BuildConfig.FAMILY_ID` acessível em runtime

---

## Etapa 1 — Design System

**Objetivo:** materializar o STYLE_GUIDE em tokens e componentes reutilizáveis.

### 1.1 Tokens
- `NossaSaudeColors.kt` com toda a paleta da seção 2 do style guide
- `NossaSaudeTypography.kt` com Plus Jakarta Sans (display) + DM Sans (body) via `googlefonts` ou `Font` resources; fallback `FontFamily.Default` se download falhar
- `Spacing.kt`, `Radius.kt`, `Elevation.kt` como `object`s com `dp` constants
- `NossaSaudeTheme(content: @Composable () -> Unit)` aplicando MaterialTheme com cores/typography customizadas
- Suporte a `darkTheme` desabilitado na v1 (force light)

### 1.2 Componentes base
- **`NsAvatar(initials, colorIndex)`** — círculo 48dp, rotação 4 cores (coral/teal/blue/amber) por `index % 4`
- **`NsChip(text, variant: Allergy | Condition | BloodType | Custom, prefix?)`** — pill com cores semânticas
- **`NsIconButton(icon, badgeCount?, onClick)`** — circular 42dp com sombra e badge opcional
- **`NsFab(icon, onClick)`** — coral 58dp com sombra colorida e animação de entrada
- **`NsSyncBar(state: Synced | Pending | Syncing | Error, lastSyncAt?)`** — banner teal com ícone + texto
- **`NsStatCard(number, label, accentColor)`** — número grande + label
- **`NsMemberCard(member, lastConsultationDate, onClick)`** — card completo com avatar, info, chips, footer
- **`NsTopBar(title, navigationIcon?, actions)`** — top bar transparente
- **`NsEmptyState(illustration, title, subtitle, ctaText, onCta)`**
- **`NsContraindicationAlert(medicationName, memberName, reason)`** — banner vermelho com border-left
- **`NsTextField(label, required, ...)`** — outlined com estilo do guia
- **`NsTagInput(tags, onAdd, onRemove)`** — campo + botão "+" gerando chips removíveis
- **`NsSearchableDropdown(items, selected, onSelect)`** — para especialidades CFM

### 1.3 Animações utilitárias
- `Modifier.fadeSlideIn(delay)` — entrada de cards
- `Modifier.pressBounce()` — translateY -1dp em pressed
- Helper `rememberRotatingAngle()` para sync icon

### 1.4 Validação
- Preview Composable com showcase de todos os componentes
- Snapshot test simples (opcional)

---

## Etapa 2 — Camada de Dados Local (Room)

**Objetivo:** banco primário do app, fonte de verdade para a UI.

### 2.1 Entidades
- **`MemberEntity`** — campos do Member + `localId: Long PK autoincrement` + `remoteId: String?` + `updatedAt`, `syncedAt`, `deletedAt`
- **`ConsultationEntity`** — campos da Consultation + sync fields
- **`MedicationEntity`** — embedded em Consultation via JSON ou tabela separada com FK; **decisão:** tabela separada para permitir FTS por nome de medicamento
- **`ExamEntity`** — tabela separada com FK para Consultation, possui `remoteId` (UUID do servidor) para casar imagens
- **`PrescriptionImageEntity`** — `s3Key`, `consultationId`, `localPath?`, `uploadedAt?`, `uploadStatus: Pending | Uploaded`
- **`ResultImageEntity`** — `s3Key`, `examId`, `localPath?`, `uploadedAt?`, `uploadStatus`
- **`PendingUploadEntity`** — fila para WorkManager (id, type, consultationId, examId?, localPath, retryCount)
- **`SyncMetadataEntity`** — chave/valor (`lastPullTimestamp`)

### 2.2 Conversores
- `Converters.kt` com `@TypeConverter` para `List<String>` (allergies, tags), `Instant` ↔ Long, enums (BloodType, MedicationForm, Efficacy, Specialty)

### 2.3 DAOs
- `MemberDao` — CRUD + `observeAll()` Flow filtrando `deletedAt IS NULL` ordenado por `name`
- `ConsultationDao` — CRUD + `observeByMember(memberId)`, `getDirty()` (where `updatedAt > syncedAt`), `getTombstones()`
- `MedicationDao`, `ExamDao`, `ImageDao`
- `PendingUploadDao` — `observePending()`, `markUploaded()`, `incrementRetry()`
- `SyncMetadataDao` — `getLastPullTimestamp()`, `setLastPullTimestamp()`

### 2.4 FTS para busca
- `ConsultationFts` virtual table indexando: `reason`, `notes`, `doctorName`, `clinic`, `tags`, `medicationNames`, `examNames`
- Trigger ou atualização manual ao inserir/atualizar consulta
- DAO `SearchDao.search(query: String)` retornando `List<ConsultationSearchResult>` com snippet do match

### 2.5 Database
- `NossaSaudeDatabase : RoomDatabase` versão 1 com todas as entidades
- Singleton via Hilt (`DatabaseModule`)
- Migrations vazias inicialmente

### 2.6 Validação
- Testes de DAO instrumentados com Room in-memory
- Inserção, leitura, soft-delete, busca FTS funcionando

---

## Etapa 3 — Camada de Rede (Retrofit)

**Objetivo:** clientes HTTP tipados conforme api-contract.md.

### 3.1 Configuração HTTP
- `OkHttpClient` com:
  - `HttpLoggingInterceptor` (BODY em debug, NONE em release)
  - `HeaderInterceptor` adicionando `x-api-key` e `X-Family-Id` em todo request
  - Timeouts: connect 10s, read 30s
- `Retrofit` com `kotlinx-serialization` converter
- Tratamento de erro centralizado: `NetworkResult<T>` (Success / HttpError / NetworkError) com parsing do envelope `{ errorCode, errorMessage }`

### 3.2 DTOs (espelhando o contrato)
- `MemberDto`, `MemberCreateDto`, `MemberPatchDto`
- `ConsultationDto`, `ConsultationCreateDto`, `ConsultationPatchDto`
- `DoctorDto`, `MedicationDto`, `ExamDto`
- `PrescriptionImageDto`, `ResultImageDto`
- `UploadUrlRequestDto`, `UploadUrlResponseDto`
- `ImagesResponseDto` (presigned URLs agrupadas)
- `SyncResponseDto`
- `ApiErrorDto`

### 3.3 Services
- **`MembersApi`** — `POST/GET/GET id/PATCH/DELETE /members`
- **`ConsultationsApi`** — `POST/GET/GET id/PATCH/DELETE /consultations` + query params (`memberId`, `from`, `to`, `doctor`, `tag`)
- **`ImagesApi`** — `POST /consultations/{id}/upload-url`, `GET /consultations/{id}/images`
- **`SyncApi`** — `GET /sync?since=`
- **`S3UploadApi`** — `PUT` direto na presigned URL (cliente Retrofit separado, sem interceptors)

### 3.4 Mappers
- `MemberMapper`: DTO ↔ Entity ↔ Domain
- `ConsultationMapper`: idem (achata medications/exams)
- Helpers para conversão de `Instant` ↔ ISO 8601

### 3.5 Validação
- Testes unitários de mappers
- Mock server (MockWebServer) para validar serialização

---

## Etapa 4 — Repositórios + Sincronização

**Objetivo:** orquestrar Room + API + S3, expor dados via Flow.

### 4.1 Repositórios
- **`MemberRepository`**
  - `observeMembers(): Flow<List<Member>>` (do Room)
  - `getMember(id): Member?`
  - `createMember(input)` — insere Room (`syncedAt = 0`), enfileira push
  - `updateMember(id, patch)` — atualiza Room, bump `updatedAt`, enfileira push
  - `deleteMember(id)` — soft-delete se sincronizado, hard-delete senão
- **`ConsultationRepository`** — espelho do acima + `observeByMember`, `getDirty`
- **`ImageRepository`**
  - `requestUploadUrl(consultId, type)` → `(uploadUrl, s3Key)`
  - `uploadToS3(uploadUrl, file)` com progresso
  - `confirmUpload(consultId, s3Key, examId?)` via PATCH
  - `getImageUrls(consultId)` — busca presigned URLs e retorna mapa `s3Key → url`
- **`SearchRepository`** — wrapper sobre `SearchDao`

### 4.2 SyncManager
- `syncNow(): SyncResult` executa pull + push em sequência
- **Pull:**
  1. `GET /sync?since=lastPullTimestamp`
  2. Para cada Member/Consultation: aplicar regra last-write-wins (seção 3.4 do dreq)
  3. Salvar `syncedAt` da resposta como novo `lastPullTimestamp`
- **Push:**
  1. Para cada `MemberEntity` dirty (`updatedAt > syncedAt`):
     - `remoteId == null` → POST → salva `remoteId`
     - `deletedAt != null` → DELETE → hard-delete local
     - senão → PATCH com diff
  2. Idem para Consultations
  3. Em sucesso: `syncedAt = now`
- Estado exposto via `StateFlow<SyncState>` (Idle / Syncing / Success / Error)
- Trigger automático ao abrir o app (`MainActivity.onCreate` via ViewModel)

### 4.3 UploadWorker (WorkManager)
- `UploadRetryWorker(CoroutineWorker)`:
  1. Lê `pendingUploads` do Room
  2. Para cada um: `requestUploadUrl` → PUT S3 → `confirmUpload` → `markUploaded`
  3. Falha individual → `Result.retry()` com backoff exponencial
- Constraints: `NetworkType.CONNECTED`
- Enfileirado ao salvar consulta com imagem ou ao tocar em "tentar novamente"

### 4.4 Coil ImageLoader
- Singleton via Hilt:
  - `MemoryCache` 15% RAM
  - `DiskCache` 500MB em `cacheDir/rx_images`
- Helper `loadImage(s3Key, presignedUrl)` setando `memoryCacheKey = s3Key`, `diskCacheKey = s3Key`
- `ImageUrlResolver` que consulta `ImageRepository.getImageUrls()` antes de carregar (cache de URL com TTL 10 min para não pedir nova URL toda hora)

### 4.5 Validação
- Testes unitários de SyncManager com fakes de Repository
- Cenários: criar offline → sync → remoteId atualizado; deletar sincronizado → tombstone → DELETE; conflito local-vence

---

## Etapa 5 — Tela Home (Lista de Membros)

**Objetivo:** primeira tela visível, espelha o mockup aprovado.

### 5.1 Estrutura
- `HomeScreen(viewModel: HomeViewModel)`
- `HomeViewModel` expõe `StateFlow<HomeUiState>` com:
  - `members: List<MemberCardState>` (com `lastConsultationDate` calculado)
  - `syncState: SyncState`
  - `lastSyncAt: Instant?`
  - `pendingCount: Int` (membros + consultas dirty)
  - `stats: { consultations, medications, prescriptions }`

### 5.2 Layout (top → bottom)
1. **Top bar:** greeting "Família {nome}", botões Buscar + Sincronizar (badge com `pendingCount`)
2. **Título:** "NossaSaúde" (coral no "Saúde")
3. **SyncBar** com estado atual
4. **Section title:** "Membros" + contagem
5. **Lista de `NsMemberCard`** com animação `fadeSlideIn` em stagger
6. **`quick-stats`** com 3 `NsStatCard`
7. **FAB** coral com "+"

### 5.3 Empty state
- Quando `members.isEmpty()`: `NsEmptyState` "Nenhum membro cadastrado" + CTA "Adicionar membro" (substitui lista)
- FAB continua visível

### 5.4 Interações
- Tap em card → `navigate("member/{id}")`
- Tap em FAB → `navigate("member/new")`
- Tap em sync → `viewModel.syncNow()` (ícone gira durante)
- Tap em buscar → `navigate("search")`

### 5.5 Validação
- Pull-to-refresh opcional dispara `syncNow()`
- Stats refletem dados reais do Room

---

## Etapa 6 — Membro: Cadastro, Edição e Perfil

**Objetivo:** CRUD completo do membro + tela de perfil com timeline.

### 6.1 MemberFormScreen (cadastro/edição)
- Modo: `Create` ou `Edit(id)`
- Campos: nome (obrigatório), data de nascimento (DatePicker), tipo sanguíneo (dropdown), peso, altura
- Tags: alergias e condições crônicas via `NsTagInput`
- Validação: nome não vazio
- Botão "Salvar" no canto direito da TopAppBar
- Ao salvar: `viewModel.save()` → `navigateUp()`

### 6.2 MemberProfileScreen
- TopBar com nome do membro + botões Editar / Menu (overflow com Deletar)
- Header: avatar grande + nome + idade + tipo sanguíneo
- Seções (cards):
  1. **Dados pessoais** — peso, altura
  2. **Alergias** — chips vermelhos (ou empty state "nenhuma alergia registrada")
  3. **Condições crônicas** — chips azuis
  4. **Medicamentos contraindicados** — query: medications where `contraindicated = true` AND `consultation.memberId = this`. Mostra nome, dosagem, motivo. Destaque visual (border vermelha)
  5. **Timeline de consultas** — lista compacta ordenada por data desc, agrupada por mês

### 6.3 Delete flow
- Dialog confirmando: "Isso vai remover o membro e todo o histórico de consultas dele. Confirma?"
- Ao confirmar: `viewModel.delete()` → `navigateUp()` para Home

### 6.4 Validação
- Edição preserva valores originais
- Soft-delete some da Home mas continua no Room até sync

---

## Etapa 7 — Consultas: Cadastro, Detalhe, Edição

**Objetivo:** fluxo principal do app — registrar e atualizar consultas.

### 7.1 ConsultationFormScreen
- Modo `Create(memberId)` ou `Edit(consultationId)`
- Seções colapsáveis ou scroll contínuo:
  1. **Dados** — data (default hoje), médico, especialidade (`NsSearchableDropdown` com 55 CFM + "Outra" → campo texto), clínica, motivo (obrigatório), tags, notas, "consulta de retorno?" (autocomplete de consultas anteriores do mesmo membro)
  2. **Receitas** — grid 2 colunas com thumbs + botão "+ Adicionar foto" (câmera ou galeria)
  3. **Medicamentos** — lista de cards inline editáveis + "+ Adicionar medicamento"; cada item: nome (obrigatório), princípio ativo, dosagem, forma (dropdown), frequência, toggle contraindicado (revela campo motivo obrigatório), eficácia (radio: Eficaz/Parcial/Ineficaz/Não avaliado), efeitos colaterais
  4. **Exames** — lista de cards: nome (obrigatório), notas, anexar resultado (foto/PDF) + "+ Adicionar exame"

### 7.2 Alerta de contraindicação
- Ao digitar nome de medicamento: query nos medicamentos do membro com `contraindicated = true`
- Match parcial (case-insensitive) no nome ou princípio ativo
- Exibe `NsContraindicationAlert` abaixo do campo

### 7.3 Captura de foto
- Botão "Adicionar foto" → bottom sheet "Câmera" / "Galeria"
- CameraX com preview customizado
- Pós-captura: `ImageCompressor` redimensiona para 2000px e JPEG 80% (gera também thumb 300px)
- Salva em `filesDir/pending_uploads/{uuid}.jpg`
- Cria `PendingUploadEntity` e `PrescriptionImageEntity`/`ResultImageEntity` com `uploadStatus = Pending`
- Enfileira `UploadRetryWorker`

### 7.4 ConsultationDetailScreen
- TopBar com "Editar" e overflow (Deletar)
- Header: ícone sync status + data + médico/especialidade
- Seções: motivo, notas, receitas (grid clicável → fullscreen), medicamentos (cards com cor por eficácia), exames (com resultados)
- Botão "Marcar consulta de retorno" → cria nova consulta com `returnOf` preenchido

### 7.5 Validação
- Save offline funciona, dado aparece imediatamente no perfil
- Upload de imagem retoma após reconectar
- Edição de exame para anexar resultado funciona com PATCH

---

## Etapa 8 — Busca

**Objetivo:** busca local instantânea por medicamento, exame, motivo, médico.

### 8.1 SearchScreen
- TopBar com `NsTextField` de busca (autofocus)
- Resultados em tempo real via `debounce(200ms)` + `flatMapLatest`
- Cada resultado é um `ConsultationCard` com:
  - Avatar + nome do membro
  - Data, médico/especialidade
  - Motivo (com termo destacado)
  - Lista compacta de medicamentos/exames que bateram com o termo

### 8.2 Filtros
- Bottom sheet "Filtros":
  - Membro (multi-select)
  - Período (range date picker)
  - Tag (chips selecionáveis com tags existentes)
- Indicador de filtros ativos no topo

### 8.3 Empty states
- Sem busca: "Digite para buscar consultas, medicamentos ou exames"
- Sem resultado: "Nenhum resultado para 'X'"

### 8.4 Validação
- Busca por "amox" retorna consultas com Amoxicilina
- Filtros combinam corretamente
- Performance: <100ms para 1000+ consultas

---

## Etapa 9 — Visualizador de Imagem (Fullscreen)

**Objetivo:** visualizar receitas e resultados em alta resolução.

### 9.1 ImageViewerScreen
- Argumento: lista de `s3Keys` + índice inicial
- HorizontalPager para swipe entre imagens
- Cada página: zoom/pan via `Modifier.pointerInput` (gestures de pinch)
- TopBar minimalista (close + contador "1/3")
- BottomBar com ícone de sync status da imagem

### 9.2 Carregamento
- Usa `loadImage(s3Key, presignedUrl)` do Coil
- Loading: shimmer placeholder
- Erro: ícone + "Não foi possível carregar a imagem"

### 9.3 Validação
- Imagens já em cache abrem instantâneas
- Sem internet, imagens cacheadas funcionam

---

## Etapa 10 — Configurações e Polimento

**Objetivo:** acabamento, indicadores globais e tela de settings.

### 10.1 SettingsScreen
- Mostra `familyId` (BuildConfig) com botão "copiar"
- Mostra versão do app
- Botão "Sincronizar agora"
- Botão "Limpar cache de imagens" (com confirmação)
- Total ocupado por cache

### 10.2 Indicadores globais de sync
- Aplicar `NsSyncBar` na Home
- Ícone de sync status no header de cada `ConsultationDetailScreen`
- Badge no botão de sync na Home (count de pendentes)

### 10.3 Animações finais
- `fadeSlideIn` em stagger para listas (Home, perfil, busca)
- FAB com `scale 0.5→1` na entrada
- Sync icon rotacionando durante `SyncState.Syncing`
- Press feedback `translateY -1dp` em todos os cards
- Shared element transition (avatar + nome) entre Home e Perfil

### 10.4 Tratamento de erros
- Toast/Snackbar para falhas de sync, upload, delete
- Retry inline em telas de detalhe quando aplicável
- Fallback de fonte se Plus Jakarta Sans / DM Sans não baixarem

### 10.5 Acessibilidade
- `contentDescription` em todos os ícones
- Tamanhos de toque mínimos 48dp
- Suporte a dynamic font scaling

### 10.6 Validação final
- Smoke test do fluxo completo: cadastrar família vazia → membro → consulta com receita → busca → editar → sync
- Funciona offline em todos os pontos críticos
- Performance: app abre em <1s, telas trocam em <300ms

---

## Apêndice — Ordem de Implementação Sugerida

1. **Etapas 0 e 1 em paralelo** (setup + design system) — fundação visual e técnica
2. **Etapas 2 e 3 em paralelo** (Room + Retrofit) — camadas de dados independentes
3. **Etapa 4** — costura tudo
4. **Etapa 5** — primeira tela rodando com dados reais
5. **Etapa 6** — primeiro CRUD completo end-to-end (Member é mais simples que Consultation)
6. **Etapa 7** — fluxo principal, mais complexo
7. **Etapa 8** — depende de dados existentes
8. **Etapa 9** — depende de imagens cadastradas
9. **Etapa 10** — só faz sentido com as anteriores prontas

**Estimativa total:** 4–6 semanas de desenvolvimento focado (alinha com o roadmap v1 do dreq.md).
