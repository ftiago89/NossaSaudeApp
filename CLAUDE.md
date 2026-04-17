# NossaSaúde — Guia para Claude Code

App Android familiar (Kotlin/Jetpack Compose) para gerir consultas, exames e medicamentos dos membros de uma família. Projeto pessoal, offline-first, sincronização manual com backend serverless.

---

## Convenções do projeto

- **Código:** inglês (classes, variáveis, comentários).
- **Strings de UI:** pt-BR (labels, mensagens de erro, títulos).
- **Commits e documentação:** pt-BR.
- **Estilo visual:** seguir `docs/STYLE_GUIDE.md`. Nunca introduzir cores, tipografia ou espaçamentos fora dos tokens de `ui/theme/`.
- **Referências fiéis ao mockup:** `docs/nossasaude-home.html` é a verdade de design para a Home. Outras telas devem manter o mesmo tom (coral + teal sobre bege, cards brancos com border radius 16dp).

## Stack técnica

| Camada         | Tecnologia                                               |
|----------------|----------------------------------------------------------|
| UI             | Jetpack Compose + Material3 (BOM 2024.10.01)             |
| DI             | Hilt 2.52 + KSP                                          |
| Navegação      | Navigation Compose 2.8.4                                 |
| Persistência   | Room 2.6.1 com FTS4 (busca unificada)                    |
| Rede           | Retrofit 2.11 + OkHttp 4.12 + kotlinx-serialization      |
| Imagens        | Coil 3.0.4 (memória 15% + disco 500MB)                   |
| Background     | WorkManager 2.9.1 + hilt-work                            |
| Data/Hora      | kotlinx-datetime (`Instant`, `LocalDate`)                |
| Min SDK        | 26                                                       |
| Target/compile | 35                                                       |
| JVM            | 17                                                       |

## Configuração obrigatória (`local.properties`)

```
FAMILY_ID=<uuid da família>
API_KEY=<x-api-key>
API_BASE_URL_DEBUG=http://10.0.2.2:3000/dev/
API_BASE_URL_RELEASE=https://api.nossasaude.example/
```

Injetados via `BuildConfig`. Sem login — autenticação é apenas pelo header `x-api-key` + `X-Family-Id`.

---

## Arquitetura

MVVM com camadas bem separadas. Dependências fluem em uma direção: **UI → Domain ← Data**.

```
ui/                         Composables + ViewModels (Hilt)
  ├─ components/            Biblioteca do Design System (NsXxx)
  ├─ theme/                 Cores, tipografia, dimensões, elevação
  ├─ navigation/            NavRoutes + AppNavHost
  ├─ util/                  Formatação compartilhada (DateFormatUtils)
  ├─ home/                  Home
  ├─ member/                Form + perfil de membro
  ├─ consultation/          Form + detalhe de consulta
  ├─ search/                Busca FTS
  ├─ imageviewer/           Viewer fullscreen (Pager)
  └─ settings/              Configurações

domain/model/               Modelos puros (sem deps de Android/Room/Retrofit)

data/
  ├─ local/                 Room (Database, DAOs, Entities, Converters)
  ├─ remote/                Retrofit (APIs, DTOs, Interceptors, ApiException)
  ├─ mapper/                Conversões entre Entity ↔ Domain ↔ DTO
  ├─ repository/            Member/Consultation/Image/Search repositories
  ├─ sync/                  SyncManager, SyncState, UploadWorker
  └─ image/                 NsImageLoader (Coil), S3ImageLoader (cache presigned)

di/                         Hilt modules (Database, Network, Dispatchers, ImageLoader)
```

---

## Modelo de dados

### Entidades Room

- `members` — PK local (UUID), `remoteId` único, soft-delete por `deletedAt`.
- `consultations` — FK para `memberId` (por ID local), `remoteId` único.
- `medications`, `exams` — filhos de consulta, `orderIndex` para ordenação estável. `exams.remoteId` é único (necessário pois imagens de exames referenciam o `_id` remoto).
- `prescription_images` — FK para consulta, `s3Key` único, `uploadStatus` (`PENDING`/`UPLOADED`).
- `result_images` — FK para exame, mesmas regras.
- `pending_uploads` — fila de uploads pendentes para o `UploadWorker`.
- `sync_metadata` — key/value (usado para guardar `last_sync_iso`).
- `consultation_fts` — tabela FTS4 (colunas: `consultationId`, `memberId`, `reason`, `notes`, `doctorName`, `clinic`, `tags`, `medicationNames`, `examNames`).

**Regras importantes:**
- IDs locais são UUIDs. `remoteId` é o `_id` do MongoDB, populado após primeiro sync.
- Datas são gravadas como `Long` (epoch millis) no Room. `Instant` é usado apenas no `domain/`.
- Deleção é sempre soft (`deletedAt = now`). Hard delete só acontece após confirmação do servidor.
- `syncedAt` + `updatedAt` determinam o "dirty bit": `syncedAt IS NULL OR updatedAt > syncedAt`.

### Mappers

- Todos em `data/mapper/`. Funções de extensão nomeadas `toDomain()`, `toEntity()`, `toDto()`, `toCreateDto()`, `toPatchDto()`.
- `ConsultationMapper.kt` expõe `ConsultationAggregate` (entity + filhos) e `ConsultationDto.toAggregate(localId, existingExamIdByRemote, existingImageIdByS3)` que **preserva PKs locais** quando um pull do servidor atualiza registros existentes.

---

## Sincronização

`SyncManager.syncNow()` (orquestrado por `Mutex`, não re-entra):

1. **Push** — itera `getDirtyIds()` em cada repositório e chama `pushDirty()`:
   - `deletedAt != null && remoteId == null` → hard delete local (nunca chegou ao servidor).
   - `deletedAt != null && remoteId != null` → DELETE remoto; hard delete local se sucesso ou 404.
   - `remoteId == null` → POST; recebe ID e grava via `markSynced`.
   - caso contrário → PATCH (body completo).
   - Após criar/atualizar consulta, `reconcileRemote()` preserva `remoteId` dos exames retornados (necessário para uploads de imagem subsequentes).
2. **Pull** — `GET /sync?since=<last_sync_iso>`; aplica last-write-wins comparando `remoteUpdatedAt` vs `localUpdatedAt`.
3. **Uploads** — drena `pending_uploads` via `ImageRepository.executePendingUpload()`.

`SyncState`: `Idle`/`Running`/`Success(Instant)`/`Failure(message, Instant)` — exposto como `StateFlow`.

### Upload de imagens (fluxo crítico)

1. Câmera/galeria → `ImageRepository.saveLocalCopy(Uri)` copia para `filesDir/images/{uuid}.jpg`.
2. `enqueuePrescription()` / `enqueueExam()` insere linha local com `s3Key = "local://{uuid}"` + `uploadStatus = PENDING` e enfileira `PendingUploadEntity`.
3. `UploadWorker` (ou `SyncManager.uploads()`) chama `executePendingUpload()`:
   - **Exige** que a consulta já tenha `remoteId` (e o exame também, para fotos de exame) — senão retorna `false` e tenta depois.
   - `POST /consultations/{id}/upload-url` → presigned PUT URL.
   - `PUT` direto no S3 via `S3UploadApi` (sem `x-api-key`; client separado `@S3Client`).
   - `PATCH /consultations/{id}` com `addPrescriptionImage` ou `addExamImage` (s3Key real).
   - Swap da linha local: remove placeholder, insere linha real com `uploadStatus = UPLOADED`.
4. `WorkManager` reenfileira com backoff exponencial em caso de falha.

### Formato do s3Key

```
{familyId}/{memberId}/consultation/{consultationId}/prescriptions/{uuid}.jpg
{familyId}/{memberId}/consultation/{consultationId}/exams/{uuid}.jpg
```

O segmento `consultation/` (literal) está sempre presente entre `{memberId}` e `{consultationId}`. O `s3Key` é determinístico, estável e retornado pelo servidor via `POST /upload-url` — nunca construído pelo app.

### Exibição de imagens (3 camadas)

1. `s3Key` é a **chave de cache estável** — não muda nunca.
2. `S3ImageLoader.presignedUrl(remoteConsultationId, s3Key)` resolve o URL presignado atual (TTL 10min em memória).
3. Coil (via `NsImageLoader`) cuida da cache memória/disco. Use `memoryCacheKey = s3Key` e `diskCacheKey = s3Key` em chamadas `ImageRequest.Builder` para garantir que o URL efêmero não invalide o cache.

O `ConsultationDetailViewModel` usa `ImageDisplay(url, cacheKey)` para separar o endereço de download da chave de cache estável. Antes de chamar `GET /images`, checa `imageLoader.diskCache?.openSnapshot(s3Key)` — se todas as imagens já estão em disco, o GET é omitido. Em caso de cache miss, `presignedUrl()` é chamado uma única vez (o endpoint retorna URLs de todas as imagens da consulta de uma vez, populando o cache em memória).

**Importante:** `NossaSaudeApp` implementa `SingletonImageLoader.Factory` retornando o `ImageLoader` provido pelo Hilt. Sem isso, `AsyncImage` usa o singleton padrão do Coil (instância diferente, diretório de cache diferente), e o check de `diskCache.openSnapshot()` no ViewModel nunca encontra as imagens, forçando `GET /images` em toda abertura de tela.

---

## Design System

Todos os componentes base começam com prefixo `Ns` e ficam em `ui/components/`:

| Componente                 | Uso                                                 |
|----------------------------|-----------------------------------------------------|
| `NsAvatar`                 | Avatar circular com iniciais (paleta rotativa 4x)  |
| `NsChip` / `NsChipVariant` | Alergia, condição, tipo sanguíneo, neutro, danger  |
| `NsMemberCard`             | Card de membro com chips, meta, footer de consulta |
| `NsFab`                    | FAB coral                                           |
| `NsIconButton`             | Botão circular branco com badge opcional           |
| `NsTopBar`                 | Top app bar (com/sem botão de voltar)              |
| `NsSyncBar` / `NsSyncState`| Banner de status de sincronização                  |
| `NsStatCard`               | Card de métrica (número + label)                   |
| `NsEmptyState`             | Placeholder com ícone + CTA                        |
| `NsTextField`              | Outlined field com label/asterisco/error/visualTransformation |
| `NsTagInput`               | Input + botão `+` gerando chips removíveis         |
| `NsSearchableDropdown`     | Dropdown com busca in-place                        |
| `NsContraindicationAlert`  | Banner vermelho de alerta médico                   |
| `DateMaskVisualTransformation` | `VisualTransformation` para campos de data (armazena dígitos, exibe `dd/mm/yyyy`) |

**Tokens:** `NsColors`, `NsTypography`, `Spacing`, `Radius`, `Elevation`, `AvatarPalette` + `avatarPaletteFor(index)`. Nunca hardcodar cores/dp.

**`NsSearchableDropdown`** aceita parâmetro `searchable: Boolean` (padrão `true`):
- `searchable = true` — campo editável, filtra opções enquanto digita. Usar em listas longas (ex: especialidade médica).
- `searchable = false` — campo read-only, toque abre a lista completa. Usar em listas curtas e fixas (tipo sanguíneo, forma do medicamento, eficácia).

Todas as listas de opções de dropdown devem ser ordenadas alfabeticamente por label (`.sortedBy { it.label }`).

---

## Navegação

`ui/navigation/NavRoutes.kt` centraliza rotas. Helpers geram URLs com argumentos:

- `memberProfile(id)`, `memberEdit(id)`
- `consultationNew(memberId)`, `consultationEdit(id)`, `consultationDetail(id)`
- `imageViewer(urls, index)` — URLs pipe-encoded, máximo 20 por chamada

`AppNavHost` mapeia cada rota a sua tela. ViewModels recebem argumentos via `SavedStateHandle`.

---

## Padrões de ViewModel

- `@HiltViewModel` + `@Inject constructor(SavedStateHandle, ...)`.
- Estado exposto como `StateFlow<XxxUiState>` via `combine(...).stateIn(SharingStarted.WhileSubscribed(5_000))`.
- Observação reativa preferida sobre polling. Para snapshots one-shot use `.first()`.
- **Não chame `collect { ... return@collect }`** como gambiarra para snapshot — use `first()`.
- Campos de formulário: validação derivada (`val isValid: Boolean get() = ...`) em vez de estado separado.

---

## Testes unitários

Stack: **JUnit 4 + MockK 1.13 + Turbine 1.1 + kotlinx-coroutines-test**.

### Convenções
- Fixtures compartilhadas em `app/src/test/.../util/TestFixtures.kt` — builders com defaults razoáveis e overrides por named param. Constantes de tempo `T0`–`T3` (epoch millis) para timestamps determinísticos.
- `MainDispatcherRule` (`util/MainDispatcherRule.kt`) substitui `Dispatchers.Main` por `UnconfinedTestDispatcher` via `@get:Rule` em testes de ViewModel.
- Repositórios recebem `UnconfinedTestDispatcher()` diretamente no construtor (parâmetro `@IoDispatcher`).
- Mocks: `MockKAnnotations.init(this)` no `@Before`; `coEvery`/`coVerify` para suspending functions.

### O que está coberto
| Camada | Arquivo de teste |
|---|---|
| `MemberRepository.pushDirty()` | `data/repository/MemberRepositoryTest` |
| `ConsultationRepository.pushDirty()` + `savePulled()` | `data/repository/ConsultationRepositoryTest` |
| `ImageRepository` (enqueue, upload, cancelamento, remoção) | `data/repository/ImageRepositoryTest` |
| `SyncManager` (estado + pull) | `data/sync/SyncManagerTest` |
| `ConsultationMapper` + `MemberMapper` | `data/mapper/ConsultationMapperTest` |
| `ConsultationFormViewModel` (validação + deleção de imagens) | `ui/consultation/ConsultationFormViewModelTest` |
| `HomeViewModel` (stats, cards, syncState) | `ui/home/HomeViewModelTest` |
| `MemberProfileViewModel` (dados, deleted, contraindicados) | `ui/member/MemberProfileViewModelTest` |
| `parseBirthDate` + `MemberFormState` | `ui/member/ParseBirthDateTest` |
| `ageLabel`, `toShortDate`, `toLongDate` | `ui/util/DateFormatUtilsTest` |

### Convenções adicionais de mock
- `SyncManager.syncNow()` retorna `Result<Unit>` — stub com `coEvery { syncManager.syncNow() } returns Result.success(Unit)`, não `just Runs`.
- Para `StateFlow` exposto por `stateIn(WhileSubscribed)`, forçar emissão em testes via `flow.first { !it.isLoading }` (requer subscriber ativo).
- `SyncManager` e repositórios são classes concretas (não interfaces) — MockK os mocka sem configuração extra graças ao plugin kotlin-allopen do Hilt.

---

## Testes instrumentados (androidTest)

Testes end-to-end que exercitam Room real + Retrofit real contra o LocalStack local. **Requerem o backend rodando.** São pulados automaticamente via `Assume` se o servidor não estiver acessível — não quebram o build CI.

```bash
./gradlew connectedDebugAndroidTest --tests "*.SyncRoundTripTest"
```

| Teste | Bug coberto |
|---|---|
| `memberPush_assignsRemoteId` | smoke test do push básico |
| `consultationPush_sendsMemberRemoteIdToServer` | **Bug #13** — UUID local vs `remoteId` do membro |
| `consultationPush_reconcileExamRemoteIds` | **Bug #25** — `reconcileRemote()` preserva exames |
| `pull_resolvesRemoteMemberIdToLocalUUID` | **Bug #14** — `savePulled()` resolve memberId remoto |

**Setup:** Room in-memory + OkHttpClient com `HeadersInterceptor`/`ErrorHandlingInterceptor` + Retrofit apontando para `BuildConfig.API_BASE_URL`.

**Limpeza automática:** cada teste registra os IDs locais criados em `createdConsultationIds` / `createdMemberIds`. O `@After` faz soft-delete + push de todos eles (consultas antes de membros), removendo os dados do servidor sem deixar lixo para os testes manuais. O `runCatching` no `@After` garante que uma falha de limpeza não mascare a falha real do teste.

---

## Erros comuns a evitar

1. **Esquecer `memberId` no `ConsultationFtsEntity`** — a tabela FTS inclui esse campo; todos os `searchDao.upsert()` devem preenchê-lo.
2. **Usar `UploadType.EXAM_RESULT`** — o enum é `UploadType.EXAM` (valor de API: `"exam"`).
3. **Chamar `api.update()` com body parcial esperando merge** — `ConsultationPatchDto` com campos `null` omite-os, mas o servidor faz replace total de `medications`/`exams` quando enviados. Para adicionar imagem, use `addPrescriptionImage`/`addExamImage`.
4. **Enviar `x-api-key` para S3** — quebra o presigned URL. Use o qualifier `@S3Client` no `OkHttpClient`.
5. **Criar `ageLabel`/formatadores duplicados** — use `ui/util/DateFormatUtils.kt`.
6. **Hard delete antes de confirmar push** — sempre marque `deletedAt` e deixe o `SyncManager` fazer o hard delete após DELETE remoto (ou 404).
7. **Aplicar um `PATCH` com `remoteId` ainda nulo** — por isso `pushDirty()` decide POST vs PATCH pelo `remoteId`.
8. **Lançar exceção não-`IOException` dentro de interceptor OkHttp** — o OkHttp re-lança qualquer exceção que não seja `IOException` no seu thread pool após chamar `onFailure`, causando `FATAL EXCEPTION` mesmo com `runCatching` no coroutine. `ApiException` estende `java.io.IOException`.
9. **Usar `ExamDto` em payloads de escrita** — `ExamDto` contém `resultImages` que o servidor rejeita com 422 (`additionalProperties`). Use sempre `ExamWriteDto` (`_id` opcional + `name` + `notes`) em `ConsultationCreateDto` e `ConsultationPatchDto`. O campo `_id` deve ser enviado quando disponível (ver item 20).
10. **Salvar `syncedAt` bruto do servidor após pull** — o servidor grava `updatedAt` alguns ms depois de `syncedAt`, fazendo `updatedAt > syncedAt` ser verdadeiro e o registro aparecer como dirty. Sempre salvar `syncedAt = max(syncedAt, updatedAt)` ao mapear DTOs vindos do servidor (ver `MemberMapper` e `ConsultationMapper`).
11. **PUT S3 retornando `InvalidRequest: Value for x-amz-checksum-crc32 header is invalid`** — o backend está gerando a presigned URL com `ChecksumAlgorithm: 'CRC32'` no `PutObjectCommand`. Remover essa opção na geração da URL presignada resolve. Problema no backend, não no app.
12. **Buscar `remaining` após `deletePrescriptionImages`** — a query retorna vazio pois o delete já executou. Sempre capturar a lista antes de qualquer delete em cascata (ver `ImageRepository.executePendingUpload`).
13. **`ConsultationCreateDto` enviando UUID local do membro como `memberId`** — o campo `memberId` deve ser o `remoteId` do membro (ID do MongoDB), não o UUID local. Em `pushDirty()`, buscar `memberDao.getById(entity.memberId)?.remoteId` antes do POST. Enviar o UUID local faz a consulta ficar órfã no servidor após limpar o banco local.
14. **Pull de consulta não encontrar o membro local** — `ConsultationDto.memberId` é o `remoteId` do membro no servidor. Em `savePulled()`, resolver via `memberDao.getByRemoteId(dto.memberId)?.id` para obter o UUID local antes de montar a entidade. Passar como `localMemberId` para `toAggregate()`.
15. **Presigned URLs do LocalStack inacessíveis no emulador ou dispositivo físico** — LocalStack gera URLs com `localhost:4566`. `S3ImageLoader` (`toLocalUrl()`) e `ImageRepository` (`remapLocalhostForDebug()`) reescrevem `://localhost` → `://<host de API_BASE_URL>` em `BuildConfig.DEBUG`, usando `java.net.URL(BuildConfig.API_BASE_URL).host`. Assim funciona tanto no emulador (`10.0.2.2`) quanto num dispositivo físico na mesma rede (`192.168.x.x`). Em produção com AWS real as URLs nunca contêm `localhost`, então é no-op.
16. **`String.format("%.1f", value)` em locale pt-BR produz vírgula** — `"155,0".toDoubleOrNull()` retorna `null`, deixando campos de peso/altura vermelhos em dispositivos pt-BR. Sempre usar `String.format(Locale.US, ...)` ou `value.toInt().toString()` para inteiros (ver `formatDecimal()` em `MemberFormViewModel`).
17. **`adjustResize` + `imePadding()` causam animação lenta do teclado** — com `enableEdgeToEdge()`, os dois sistemas animam o layout simultaneamente causando jank. Usar apenas `imePadding()` no `Column` com `verticalScroll`; não definir `windowSoftInputMode` no manifest.
18. **`pushDirty()` fazendo POST de registro já soft-deletado localmente (`deletedAt != null && remoteId == null`)** — se o registro foi criado e deletado sem nunca chegar ao servidor, não há nada para sincronizar. Fazer hard delete local diretamente, sem chamar a API. A ordem de checagem em `pushDirty()` deve ser: `(deletedAt != null && remoteId == null)` → hard delete local; `(deletedAt != null && remoteId != null)` → DELETE remoto + hard delete local; `(remoteId == null)` → POST; caso contrário → PATCH.
19. **Android Auto Backup restaurando o banco Room após reinstalação** — `android:allowBackup="true"` com regras de backup vazias faz o sistema restaurar `nossasaude.db` automaticamente. Os arquivos `res/xml/backup_rules.xml` (API ≤ 30) e `res/xml/data_extraction_rules.xml` (API ≥ 31) devem excluir explicitamente o banco e a pasta de imagens.
20. **`ExamWriteDto` sem `_id` apaga `resultImages` no PATCH** — quando o servidor recebe o array `exams` no PATCH sem `_id`, ele cria novos documentos de exame e perde os `resultImages` existentes. `ExamWriteDto` tem `@SerialName("_id") val id: String? = null` e `Exam.toDto()` popula `id = remoteId`. Exames novos (sem `remoteId`) enviam `null`, que o servidor ignora.
21. **`ConsultationFormViewModel.save()` em modo edição zerando `remoteId` e `resultImages` dos exames** — ao montar a lista de `Exam` para salvar, usar `existing.exams.associateBy { it.id }` para recuperar `remoteId` e `resultImages` do exame existente pelo `ExamDraft.id` (que é preservado de `fromConsultation()`). Sem isso, `remoteId = null` impede o PATCH correto e `resultImages = emptyList()` apaga as fotos localmente.
22. **Navegação de edição de consulta empilhando duas `ConsultationDetail`** — ao salvar uma edição (`CONSULTATION_EDIT`), usar `popUpTo(NavRoutes.CONSULTATION_DETAIL) { inclusive = true }` para remover também a detail antiga antes de navegar para a nova. Usar `popUpTo(CONSULTATION_EDIT)` remove só o form e deixa a detail antiga na pilha, fazendo o botão voltar mostrar dados desatualizados.
23. **Construir `s3Key` manualmente no app sem o segmento `consultation/`** — o formato correto é `{familyId}/{memberId}/consultation/{consultationId}/prescriptions/{uuid}.jpg` (receitas) e `.../exams/{uuid}.jpg` (exames). O segmento literal `consultation/` entre `{memberId}` e `{consultationId}` é obrigatório. O app nunca deve montar o `s3Key` — ele sempre vem do servidor via `POST /upload-url`; qualquer comparação ou parsing local de `s3Key` deve levar esse segmento em conta.
24. **`NossaSaudeApp` não implementar `SingletonImageLoader.Factory`** — `AsyncImage` usa o singleton do Coil; se ele não for o mesmo `ImageLoader` provido pelo Hilt, `diskCache.openSnapshot()` no ViewModel checa um cache diferente do que o Coil escreve, e o resultado é sempre "cache miss" → `GET /images` em toda abertura da tela de detalhe. `NossaSaudeApp` deve implementar `SingletonImageLoader.Factory` e retornar `imageLoader` (o `@Inject`ed do Hilt).
25. **`reconcileRemote()` usando delete+reinsert de exames apaga `result_images` em cascata** — `ResultImageEntity` tem `onDelete = ForeignKey.CASCADE` em relação a `ExamEntity`. Chamar `dao.deleteExamsFor()` dentro de `reconcileRemote()` deleta todas as `result_images` por cascade, inclusive placeholders PENDING ainda não enviados ao S3. Quando `savePulled()` roda logo depois, `pendingResultImages` já está vazio — o placeholder é perdido e `executePendingUpload` não encontra a linha local para fazer o swap após o upload, fazendo a imagem desaparecer localmente. **Solução:** `reconcileRemote()` deve atualizar `remoteId` dos exames via `UPDATE` direto (`dao.updateExamRemoteId(id, remoteId)`) sem deletar e reinserir os registros.
26. **Deletar imagem na edição sem filtrar do `updated` antes de `consultationRepository.update()`** — `replaceFullConsultation` reconstrói todas as linhas de `prescription_images` e `result_images` a partir do objeto `Consultation` passado. Se o s3Key deletado ainda estiver em `updated.prescriptionImages` (ou `updated.exams[*].resultImages`), ele é reinserido localmente mesmo após a chamada `removePrescriptionImage`/`removeExamImage` ao servidor. Em `save()` modo edição, filtrar `deletedPrescriptionS3Keys` de `existing.prescriptionImages` e `draft.deletedResultS3Keys` de cada `existingExam.resultImages` antes de montar o objeto `updated`.
27. **Deletar imagem PENDING sem cancelar o `PendingUploadEntity`** — imagens com `uploadStatus = PENDING` (s3Key começa com `local://`) ainda não chegaram ao S3, portanto não precisam de chamada à API. Basta chamar `imageRepository.cancelPendingUpload(localPath)` para remover da fila `pending_uploads`. Chamar `removePrescriptionImage`/`removeExamImage` no servidor para uma imagem PENDING resultará em erro 404.

---

## Testes manuais recomendados (sem backend)

1. Criar membro → aparece na Home com avatar colorido, ageLabel, chips.
2. Criar consulta com medicamento contraindicado → abrir perfil do membro → banner vermelho aparece.
3. Em nova consulta para o mesmo membro, digitar nome do medicamento contraindicado → alerta em tempo real no card do medicamento.
4. Buscar por nome do médico / medicamento / tag → FTS retorna a consulta.
5. Fechar e reabrir app → dados persistem (Room).
6. Tocar em "Sincronizar" sem rede → `SyncState.Failure` exibido no banner da Home.

---

## Documentos de referência

- `docs/dreq.md` — requisitos funcionais/não-funcionais.
- `docs/api-contract.md` — endpoints, schemas, regras de erro.
- `docs/STYLE_GUIDE.md` — design tokens e componentes (**autoridade visual**).
- `docs/nossasaude-home.html` — mockup aprovado da Home.
- `docs/development-plan.md` — plano de desenvolvimento por etapas (status).
