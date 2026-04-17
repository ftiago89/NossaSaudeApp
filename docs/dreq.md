# NossaSaúde — Planejamento de Requisitos

> App de histórico médico familiar: consultas, receitas, medicamentos e alertas.
> Projetos: **NossaSaudeApp** (Android/Kotlin) e **NossaSaudeServer** (Node.js/Lambda).

---

## 1. Visão Geral

**Problema:** Receitas médicas se perdem, membros da família esquecem quais medicamentos já tomaram, quais funcionaram, quais causaram reações adversas e quais não podem tomar juntos.

**Solução:** App nativo Android (Kotlin) com backend serverless (Node.js + Lambda) que centraliza o histórico médico da família — consultas, receitas (com foto), medicamentos e alertas — organizado por núcleo familiar (`familyId`).

**Projetos:**
- **NossaSaudeApp** — Frontend Android (Kotlin nativo, Jetpack Compose)
- **NossaSaudeServer** — Backend serverless (Node.js + AWS Lambda + API Gateway)

**Stack definida:**
- **Frontend:** Kotlin nativo (Android)
- **Backend:** Node.js + AWS Lambda (serverless)
- **Storage:** S3 (imagens de receitas)
- **Banco remoto:** MongoDB (Atlas)
- **Banco local:** Room (SQLite) — banco primário para leitura e busca
- **Auth:** API Key via `x-api-key` (API Gateway private endpoint) — sem login de usuário

---

## 2. Requisitos Funcionais

### 2.1 Gestão de Família e Membros

- `familyId` é pré-configurado no app via `BuildConfig` (injetado no build de debug/install)
- Não há login, cadastro ou convite — qualquer dispositivo com o `familyId` correto acessa os dados daquela família
- Adicionar/remover membros ao núcleo (nome, data de nascimento)
- Cada membro tem seu perfil médico independente
- Perfil do membro: nome, idade, tipo sanguíneo, peso, altura, alergias conhecidas, condições crônicas (diabetes, hipertensão etc.)
- **Tela de perfil do membro organizada em seções visíveis:**
  - Dados pessoais (nome, idade, tipo sanguíneo, peso, altura)
  - Alergias (destaque visual)
  - Condições crônicas
  - Medicamentos contraindicados — query automática: medicamentos daquele membro com `contraindicado = true`. Mostra nome, dosagem e motivo da restrição. Serve como ficha rápida em situações de emergência (PS, farmácia)
  - Timeline de consultas (abaixo das seções de perfil)

> **Como distribuir para outros núcleos familiares:** gerar um novo `familyId` (UUID), compilar o APK com esse ID no `BuildConfig.FAMILY_ID` e instalar via ADB ou compartilhar o APK. Cada núcleo tem seu ID isolado.

```kotlin
// build.gradle.kts (app)
android {
    defaultConfig {
        buildConfigField("String", "FAMILY_ID", "\"fam_a1b2c3d4-your-uuid\"")
    }
}

// No código Kotlin — acesso ao familyId
val familyId = BuildConfig.FAMILY_ID
```

### 2.2 Registro de Consultas

- Registrar consulta com: data, médico (nome e especialidade), local/clínica, motivo da consulta
- **Especialidade:** dropdown searchable com as 55 especialidades reconhecidas pelo CFM + opção "Outra" com campo de texto livre. Lista ordenada com as mais comuns no topo (clínica geral, pediatria, ortopedia, dermatologia, ginecologia, cardiologia, oftalmologia, otorrino etc.). Valor salvo: `{ specialty: "Cardiologia", customSpecialty: null }` ou `{ specialty: "OTHER", customSpecialty: "Medicina hiperbárica" }`. Lista mantida como constante no código (não precisa de collection — muda a cada década)
- Campo de observações livres (o que o médico disse, diagnóstico informal)
- Vincular consulta a um membro específico
- Possibilidade de registrar "consulta de retorno" vinculada a uma anterior

### 2.3 Registro de Medicamentos

- Cadastrar medicamento: nome comercial, princípio ativo, dosagem, forma, frequência
- Vincular medicamento a uma consulta (qual médico receitou e por quê)
- Marcar como contraindicado (com motivo obrigatório) — ex: "reação alérgica severa"
- Campo para reações/efeitos colaterais observados
- Campo "funcionou?" (eficaz / parcial / ineficaz) — para lembrar no futuro
- Medicamento é sempre um registro pontual vinculado a uma consulta. Medicamentos de uso contínuo (losartana, insulina etc.) serão tratados no perfil do membro em versão futura

**Enums do medicamento:**

```kotlin
enum class FormaMedicamento {
    COMPRIMIDO, CAPSULA, LIQUIDO, POMADA,
    INJETAVEL, GOTAS, SPRAY, ADESIVO, OUTRO
}

enum class Eficacia {
    EFICAZ, PARCIAL, INEFICAZ
}

// Campos de avaliação no medicamento
val contraindicado: Boolean = false       // flag de restrição
val motivoRestricao: String? = null       // obrigatório quando contraindicado = true
val eficacia: Eficacia? = null            // null = ainda não avaliou
val efeitosColaterais: String? = null     // texto livre
```

### 2.4 Receitas (Imagens)

- Fotografar ou fazer upload da receita médica
- Vincular imagem da receita à consulta correspondente
- Visualizar receita em tela cheia com zoom
- Suporte a múltiplas imagens por receita (frente e verso, receitas longas)

### 2.5 Exames e Resultados

- Registrar pedido de exame vinculado a uma consulta: nome do exame (ex: "hemograma completo", "raio-x tórax")
- Exames são embedded na consulta (mesmo padrão dos medicamentos)
- Resultado do exame pode ser anexado depois (dias/semanas após a consulta) via edição da consulta (fluxo 3.5)
- Anexar resultado: foto ou PDF do resultado, seguindo o mesmo fluxo de upload S3 e cache das receitas
- Campo de notas por exame (ex: "leucócitos levemente elevados")
- Busca encontra exames — "hemograma" retorna a consulta onde foi pedido

### 2.6 Busca e Histórico

- Busca unificada 100% local (Room FTS): retorna sempre **consultas** com medicamentos e exames vinculados destacados
- Buscar por medicamento: "quando tomei Amoxicilina?" → retorna consultas onde esse medicamento foi receitado
- Buscar por exame: "hemograma" → retorna consultas onde esse exame foi pedido
- Buscar por sintoma/motivo: "dor de garganta" → retorna consultas com esse motivo
- Buscar por médico: "o que o Dr. Silva já receitou?" → retorna consultas desse médico
- Filtrar por membro, por período, por tag
- Timeline visual do histórico médico de cada membro no perfil

### 2.7 Alertas e Restrições

- Marcar medicamento como contraindicado (`contraindicado = true` + `motivoRestricao` obrigatório) via edição da consulta (fluxo 3.5)
- Alerta visual ao registrar novo medicamento cujo nome ou princípio ativo bata com um medicamento contraindicado do mesmo membro
- Alerta de validade de receita (receitas controladas vencem em 30 dias) — *v2*

---

## 3. Fluxos de Uso

### 3.1 Cadastrar Membro

**Ator:** Qualquer pessoa da família com o app instalado.

**Pré-condição:** App instalado com `familyId` configurado.

**Fluxo principal:**

1. **Home → Lista de membros** — Se é o primeiro uso, lista está vazia com mensagem "Nenhum membro cadastrado" e botão "+ Adicionar membro" em destaque.
2. **Toca em "+ Adicionar membro"** — Abre formulário de cadastro. Campos:
   - Nome — obrigatório
   - Data de nascimento — opcional
   - Tipo sanguíneo — opcional
   - Peso — opcional
   - Altura — opcional
   - Alergias (campo de tags, adiciona uma por vez) — opcional
   - Condições crônicas (campo de tags) — opcional
3. **Salva** — Room local + push em background. Volta à home com o novo membro na lista.

**Fluxo de edição:** toca no membro → perfil → botão "Editar perfil" → mesmo formulário com campos preenchidos. Permite atualizar peso, adicionar/remover alergias, novas condições crônicas etc.

**Primeiro uso do app:** a home vazia é o ponto de partida. Não existe onboarding elaborado — o usuário cadastra o primeiro membro e começa a usar. O `familyId` já está no BuildConfig, não precisa configurar nada.

**Deletar membro:** botão no perfil do membro, com confirmação ("Isso vai remover o membro e todo o histórico de consultas dele. Confirma?"). Soft-delete se já sincronizado (`remoteId != null`), hard-delete se nunca sincronizou.

### 3.2 Registrar Consulta

**Ator:** Qualquer membro da família, logo após uma consulta médica.

**Pré-condição:** App instalado com `familyId` configurado, pelo menos 1 membro cadastrado.

**Fluxo principal:**

1. **Home → Lista de membros** — App abre direto na home mostrando os membros da família. Sem login.
2. **Seleciona membro** — Toca no membro que foi à consulta. Abre o perfil com timeline resumida.
3. **Toca em "+ Nova consulta"** — Abre formulário de consulta.
4. **Preenche dados da consulta** — Campos:
   - Data (default: hoje) — obrigatório
   - Médico (nome) — opcional
   - Especialidade (dropdown searchable com 55 especialidades CFM + "Outra") — opcional
   - Clínica/local — opcional
   - Motivo — obrigatório
   - Tags/categorias (respiratório, dermatológico, digestivo etc.) — opcional
   - Notas livres (o que o médico disse, diagnóstico) — opcional
   - Consulta de retorno? (vincula a uma consulta anterior) — opcional
5. **Fotografa a receita (opcional)** — Botão "Adicionar receita" abre câmera. Permite múltiplas fotos (frente, verso). Preview antes de confirmar. Comprime para max 2000px/JPEG 80%. Salva no cache local imediatamente, upload ao S3 em background via presigned URL.
6. **Adiciona medicamentos (opcional)** — Para cada medicamento:
   - Nome comercial — obrigatório
   - Princípio ativo — opcional
   - Dosagem (ex: "500mg") — opcional
   - Forma (enum `FormaMedicamento`: comprimido, cápsula, líquido, pomada, injetável, gotas, spray, adesivo, outro) — opcional
   - Frequência (ex: "8 em 8 horas") — opcional
   - Botão "+ Outro medicamento" para adicionar vários
   - Vincula automaticamente à consulta
   - Campos de avaliação (eficácia, efeitos colaterais, contraindicado) são preenchidos depois via edição da consulta
7. **Adiciona exames pedidos (opcional)** — Para cada exame:
   - Nome do exame (ex: "hemograma completo", "raio-x tórax") — obrigatório
   - Notas — opcional
   - Resultado (foto/PDF) — pode ser anexado depois via edição, quando o resultado chegar
   - Botão "+ Outro exame" para adicionar vários
8. **Salva** — Consulta salva no Room local (instantâneo) com medicamentos, exames e referências às imagens de receita. `updatedAt` é atualizado, `syncedAt` permanece 0. App tenta push ao backend em background. Volta ao perfil do membro com o novo registro no topo da timeline.

**Atalho rápido:** o fluxo mínimo é selecionar membro → foto da receita → salvar com data e motivo. Medicamentos e demais detalhes podem ser preenchidos depois editando a consulta. O objetivo é capturar a receita na hora e não perder o documento.

**Fluxo alternativo — sem receita:** se a consulta não gerou receita (retorno, consulta de rotina), o passo 5 é pulado. A consulta é salva só com dados e notas.

**Fluxo alternativo — offline/falha no push:** consulta é salva no Room local normalmente — o usuário já vê o dado. Registro fica com `updatedAt > syncedAt`. Na tela de detalhe, ícone "nuvem ✗" indica pendência. Upload de fotos entra na fila do WorkManager. Sem retry automático — usuário sincroniza manualmente via botão na home quando quiser.

### 3.3 Buscar Medicamento / Consulta

**Ator:** Membro da família — em nova consulta médica, na farmácia, ou em casa.

**Pré-condição:** Pelo menos 1 consulta cadastrada no Room local.

**Fluxo principal:**

1. **Home → Ícone de busca** — Lupa no topo da home ou barra de busca persistente.
2. **Digita termo de busca** — Ex: "amoxicilina", "dor de garganta", "Dr. Silva". Busca roda 100% contra o Room local (FTS do SQLite). Resultados em tempo real, sem chamada de rede.
3. **Resultados são consultas** — Cada item mostra: membro, data, médico/especialidade, motivo. Abaixo, os medicamentos daquela consulta (com destaque no termo que bateu com a busca). O match pode ter vindo do nome do medicamento, princípio ativo, motivo, notas ou tags.
4. **Toca em um resultado** — Abre detalhe da consulta: dados completos, foto da receita (cache local), lista de medicamentos com status e eficácia.
5. **Filtros opcionais** — Por membro, por período, por tag.

**Busca sem resultado:** exibe "Nenhum resultado para 'X'".

**Cenário real:** "Doutor, da última vez tomei algo que funcionou." → Busca "infecção garganta" → Resultado: consulta de 15/03/2025, Dr. Silva, Amoxicilina 500mg — funcionou.

### 3.4 Sincronização

**Ator:** Sistema (ao abrir o app) + usuário (botão manual na home).

**Contexto:** Room local é o banco primário para toda leitura e busca. MongoDB (via Lambda) é backup remoto e ponto de sincronização entre dispositivos da mesma família. Last-write-wins é suficiente — não há edição concorrente real no uso familiar.

#### Campos de controle por registro (Room)

Toda entidade sincronizável (membros, consultas, medicamentos) carrega:

```kotlin
val updatedAt: Long = System.currentTimeMillis()  // atualizado em toda modificação local
val syncedAt: Long = 0L                            // timestamp do último sync bem-sucedido
val remoteId: String? = null                       // ID no MongoDB; null = nunca sincronizado
val deletedAt: Long? = null                        // soft-delete (tombstone)
```

**Regra de "sujo":** um registro precisa de push quando `updatedAt > syncedAt`. Isso cobre criações novas (`syncedAt = 0`) e edições locais.

#### Push (escrita → backend)

1. Usuário cria/edita consulta, medicamento ou membro → salva no Room local (instantâneo, dado já disponível na UI). `updatedAt` é atualizado, `syncedAt` não.
2. App tenta POST/PATCH no backend em background (fire-and-forget para a UI).
3. **Sucesso:** atualiza `syncedAt = System.currentTimeMillis()` e `remoteId` (se era criação). Sem indicação visual extra.
4. **Falha (offline, timeout, cold start):** nada muda no Room — registro continua com `updatedAt > syncedAt`. Na tela de detalhe da consulta, ícone "nuvem ✗" indica pendência. Sem retry automático.

#### Pull (leitura ← backend)

1. **Trigger:** ao abrir o app OU quando usuário toca no botão "Sincronizar" na home.
2. App faz `GET /sync?familyId={id}&since={lastPullTimestamp}`.
3. Backend retorna registros modificados desde o último pull (incluindo registros criados por outros dispositivos da família).
4. Para cada registro recebido:
   - Se não existe no Room (`remoteId` não encontrado) → insere como novo.
   - Se existe e `updatedAt <= syncedAt` (sem edição local pendente) → sobrescreve com dados do backend.
   - Se existe e `updatedAt > syncedAt` (edição local pendente) → **local vence** (last-write-wins). Não sobrescreve — a edição local será enviada no próximo push.
5. Atualiza `lastPullTimestamp` no SharedPreferences/DataStore.

#### Soft-delete (tombstones)

Ao deletar um registro que já foi sincronizado (`remoteId != null`):

1. Não remove do Room — marca `deletedAt = System.currentTimeMillis()` e atualiza `updatedAt`.
2. Queries de UI filtram `WHERE deletedAt IS NULL` — tombstones ficam invisíveis.
3. No próximo push, backend recebe a deleção e remove o registro do MongoDB.
4. Após push bem-sucedido, tombstone é removido do Room (hard-delete).
5. Pull ignora registros cujo `remoteId` está nos tombstones locais — evita que o backend re-insira um registro deletado antes do push acontecer.

Para registros nunca sincronizados (`remoteId == null`), deleção é hard-delete direto.

#### Botão "Sincronizar" na home

- Botão visível na tela principal (ícone de sync).
- Ao tocar, executa **pull + push** em sequência:
  1. Pull: busca dados novos do backend.
  2. Push: envia todos os registros com `updatedAt > syncedAt` + tombstones pendentes.
- Badge opcional com contagem de registros pendentes (`updatedAt > syncedAt` ou `deletedAt != null` com `remoteId != null`).
- Feedback visual: ícone gira durante sync (animação 800ms/volta), toast de sucesso/falha ao concluir.
- Se falhar (Lambda cold start, sem internet): toast de erro, registros permanecem pendentes.

#### Cenários

| Cenário | Comportamento |
|---------|--------------|
| Maria cadastra consulta, tem internet | Room + push backend. `syncedAt` atualizado. |
| Maria cadastra consulta, sem internet | Room local. `syncedAt = 0`. Ícone "nuvem ✗" no detalhe. |
| Maria toca "Sincronizar" depois | Push envia pendentes + pull traz novidades. |
| João abre o app | Pull automático traz consulta da Maria. |
| Maria deleta consulta já sincronizada | Tombstone no Room. Próximo push remove do MongoDB. |
| João abre app antes do push da Maria | Pull não traz a consulta deletada de volta (tombstone protege). |
| Dois celulares editam o mesmo registro | Improvável no uso familiar, mas se ocorrer: local vence, próximo push sobrescreve backend. |

#### Dados envolvidos no sync

Entidades sincronizadas: membros, consultas (com medicamentos embedded), vacinas (v2). Imagens de receita seguem fluxo separado de cache S3 (seção 6.6/6.7).

### 3.5 Editar Consulta

**Ator:** Membro da família — a qualquer momento após o registro.

**Pré-condição:** Consulta já cadastrada.

**Fluxo principal:**

1. **Home → Seleciona membro** — Abre o perfil com timeline de consultas.
2. **Toca na consulta** — Abre tela de detalhe (visualização): dados da consulta, fotos de receita, lista de medicamentos, exames.
3. **Toca em "Editar"** — Abre o formulário de consulta em modo edição (mesmo formulário do fluxo 3.2, campos preenchidos). Organizado em seções:
   - **Dados da consulta:** data, médico, especialidade, clínica, motivo, tags, notas
   - **Receitas:** fotos já anexadas com opção de adicionar novas ou remover existentes
   - **Medicamentos:** lista dos medicamentos vinculados, cada um editável inline:
     - Nome, princípio ativo, dosagem, forma (`FormaMedicamento`), frequência
     - Contraindicado? (toggle) — ao ativar, exige preenchimento do motivo da restrição
     - Eficácia: Funcionou? → Eficaz / Parcial / Ineficaz / Não avaliado (`Eficacia?`)
     - Efeitos colaterais (texto livre)
     - Botão de remover medicamento
   - Botão "+ Adicionar medicamento" no final da seção
   - **Exames:** lista dos exames vinculados, cada um editável inline:
     - Nome do exame
     - Notas (observações sobre o resultado)
     - Resultado: anexar foto/PDF (mesmo fluxo de upload S3 das receitas). Este é o principal caso de edição — o resultado chega dias depois da consulta
     - Botão de remover exame
   - Botão "+ Adicionar exame" no final da seção
4. **Salva** — Room local atualizado (`updatedAt` bumped), push em background. Volta à tela de detalhe da consulta com dados atualizados.

**Fluxo alternativo — marcar medicamento como contraindicado:** ao ativar o toggle de contraindicado, o app exige preenchimento do campo "motivo da restrição" (ex: "reação alérgica severa", "vômitos intensos"). Esse motivo será exibido na seção de contraindicados no perfil do membro (seção 2.1).

**Fluxo alternativo — anexar resultado de exame:** o caso mais comum de edição de consulta. Usuário fez exame de sangue, resultado chegou 3 dias depois. Abre a consulta → Editar → seção Exames → anexa foto do resultado → Salva.

**Acesso via busca:** o fluxo 3.3 (busca) leva ao detalhe da consulta, de onde o usuário pode tocar em "Editar" para atualizar qualquer informação incluindo medicamentos e exames.

---

## 4. Requisitos Não-Funcionais

### 4.1 Segurança e Privacidade

A proteção se apoia em API Key + endpoint privado no API Gateway, sem gestão de usuários:

- **API Key (`x-api-key`):** configurada no API Gateway com Usage Plan. O endpoint rejeita qualquer request sem a key válida antes mesmo de invocar a Lambda
- **Endpoint privado no Serverless Framework:** `private: true` no evento HTTP — o API Gateway exige `x-api-key` automaticamente
- **`familyId` no header (`X-Family-Id`):** identifica o núcleo familiar. A Lambda valida presença e formato antes de qualquer operação no banco
- **Dupla camada:** API Key autentica o "app autorizado", `familyId` autoriza "qual família" — mesmo que a key vaze, sem o familyId correto não acessa dados de outra família
- Criptografia em trânsito (HTTPS/TLS) e em repouso (S3 SSE, MongoDB encryption at rest)
- Imagens de receitas no S3 com acesso via presigned URLs (expiração curta, ~15 min)
- PIN ou biometria local no app é recomendável (dado que o celular pode estar desbloqueado e os dados são sensíveis)

**Configuração no serverless.yml:**

```yaml
provider:
  name: aws
  runtime: nodejs20.x
  region: sa-east-1
  apiGateway:
    apiKeys:
      - nossasaude-app-key
    usagePlan:
      quota:
        limit: 10000        # requests por mês
        period: MONTH
      throttle:
        burstLimit: 20
        rateLimit: 10        # requests por segundo

functions:
  listConsultations:
    handler: src/handlers/consultations.list
    events:
      - http:
          path: /consultations
          method: get
          private: true      # ← exige x-api-key
```

**No app Kotlin (BuildConfig):**

```kotlin
// build.gradle.kts
android {
    defaultConfig {
        buildConfigField("String", "API_KEY", "\"sua-api-key-aqui\"")
        buildConfigField("String", "FAMILY_ID", "\"fam_a1b2c3d4-uuid\"")
        buildConfigField("String", "API_BASE_URL", "\"https://xxx.execute-api.sa-east-1.amazonaws.com/prod\"")
    }
}

// Retrofit / OkHttp interceptor
val client = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("x-api-key", BuildConfig.API_KEY)
            .addHeader("X-Family-Id", BuildConfig.FAMILY_ID)
            .build()
        chain.proceed(request)
    }
    .build()
```

> **Distribuição para outros núcleos familiares:** gerar novo `familyId` (UUID v4), a mesma API key pode ser reutilizada ou criar uma key separada por família no Usage Plan para controle de quota individual.

### 4.2 Performance

- Leituras e buscas instantâneas (Room local, sem chamada de rede)
- Upload de imagem com feedback visual de progresso
- Cache de imagens em 3 camadas (memória, disco, S3) — ver seção 6.6

### 4.3 Disponibilidade Offline

- Room local é o banco primário — toda leitura e busca funciona sem internet
- Registrar nova consulta/medicamento offline → dado disponível imediatamente no app
- Sincronização manual via botão na home (sem retry automático)
- Upload de imagens pendentes via WorkManager (retry automático apenas para imagens)

### 4.4 Escalabilidade

- Modelo multi-tenant por `familyId` desde o dia 1
- Estrutura de dados que suporte N famílias sem refatoração

### 4.5 Idioma do Código

- Todo código em inglês: nomes de arquivos, funções, variáveis, classes, interfaces, comentários
- Valores de enums podem ficar em português quando representam dados de domínio exibidos na UI (ex: especialidades médicas, categorias)
- Strings de UI em português (pt-BR) via `strings.xml` no Android
- Commits, documentação técnica (README, CLAUDE.md) e release notes em português

---

## 5. Possíveis Features Futuras

### 5.1 Interação Medicamentosa

- Alertar quando medicamentos receitados em consultas recentes são conhecidamente incompatíveis
- Abordagem simples: tabela local de interações mais comuns (AINEs + anticoagulantes, por exemplo) ou consultar API pública como OpenFDA
- Disclaimer claro: "isto não substitui orientação médica"

### 5.2 Medicamentos de Uso Contínuo

- Campo `medicamentosContinuos` no perfil do membro (nome + dosagem), independente de consultas
- Losartana, insulina etc. aparecem como informação fixa do membro, não vinculada a uma consulta específica

### 5.3 Vacinas

- Carteira de vacinação digital por membro
- Registrar vacinas com data, dose (1ª, 2ª, reforço) e próxima dose prevista
- Separar de medicamentos — vacina tem ciclo próprio
- Collection `vaccines` no MongoDB (já modelada no documento)

### 5.4 Crianças e Idosos

- Histórico de peso/altura por membro (crianças mudam rápido e isso afeta dosagem)
- Destaque visual quando membro acumula muitos medicamentos contraindicados
- Campo de responsável por administrar medicamento (dependentes)

### 5.5 Compartilhar Histórico

- Gerar PDF ou link temporário com histórico médico filtrado de um membro
- Caso de uso: na consulta com novo médico, compartilhar tudo que já tomou e o que funcionou
- Link temporário via Lambda (expira em X horas)

### 5.6 Sugestão de Histórico

- Ao registrar nova consulta com tag já usada (ex: "infecção de ouvido"), o app sugere: "nas últimas 3 vezes, foi tratado com Amoxicilina 500mg"
- Tags/categorias por problema (respiratório, dermatológico, digestivo etc.)

### 5.7 QR Code de Emergência

- QR code no perfil do membro que leva a uma página web com ficha resumida (alergias, contraindicados, condições crônicas)
- Pode ser impresso e colado na carteira
- URL com token difícil de adivinhar, gerada via Lambda

### 5.8 OCR de Receitas

- Amazon Textract para extrair texto da foto da receita
- Sugerir preenchimento automático de medicamentos (nome, dosagem) a partir do texto extraído
- Reduz atrito no cadastro

---

## 6. Análise: Imagens de Receitas no S3

### 6.1 Recomendação: Sim, S3 é a escolha certa

Guardar as imagens das receitas é essencial — muitas vezes a receita tem informações que você não digitou (dosagem exata, orientações escritas à mão, CRM do médico, validade). O S3 é ideal por custo, durabilidade e integração nativa com Lambda.

### 6.2 Arquitetura de Upload Recomendada

```
[App Kotlin] → Lambda (gera presigned URL) → [Upload direto ao S3]
```

**Fluxo:**
1. App chama endpoint `POST /consultations/{id}/upload-url`
2. Lambda gera presigned URL de upload (PUT) com expiração de 5 min
3. App faz upload direto para o S3 (sem passar pela Lambda — economiza custo e tempo)
4. S3 Event Notification → Lambda de processamento (thumbnail, OCR opcional)
5. Para visualizar: app chama `GET /consultations/{id}/images` → Lambda retorna presigned URLs de leitura (GET, expira em 15 min)

### 6.3 Estrutura de Pastas no S3

```
s3://nossasaude-prescriptions/
  └── {familyId}/
      └── {memberId}/
          └── consultation/
              └── {consultationId}/
                  ├── prescriptions/
                  │   ├── receita-1.jpg
                  │   └── receita-2.jpg
                  ├── exams/
                  │   ├── hemograma-1.jpg
                  │   └── raio-x-1.jpg
                  └── thumbnails/
                      ├── receita-1-thumb.jpg
                      ├── receita-2-thumb.jpg
                      ├── hemograma-1-thumb.jpg
                      └── raio-x-1-thumb.jpg
```

> O nível `consultation/` foi adicionado para reservar espaço a outros tipos de registro clínico no futuro (ex: `surgery/`), sem quebrar a hierarquia `{familyId}/{memberId}/`.

### 6.4 Otimizações

- **Thumbnails automáticos:** Lambda trigger no S3 que gera thumbnail (300px) para listagens rápidas
- **Compressão no app:** redimensionar para max 2000px e comprimir JPEG 80% antes do upload (receita legível, ~200-400KB ao invés de 3-5MB)
- **OCR opcional (v2):** Amazon Textract na receita para extrair texto automaticamente → preencher campos de medicamento, dosagem etc. com sugestões
- **Lifecycle Policy:** mover imagens com mais de 2 anos para S3 Infrequent Access (custo menor)
- **Versionamento:** habilitar versionamento no bucket para evitar perda acidental

### 6.5 Custos Estimados (uso doméstico)

Para uma família com ~50 consultas/ano e 2 fotos por receita:
- Storage: ~100 fotos × 400KB = ~40MB/ano → praticamente grátis no S3
- Requests: ~200 PUTs + ~1000 GETs/ano → centavos
- Lambda: dentro do free tier para esse volume
- **Custo total estimado: < US$1/mês** para uso familiar

### 6.6 Estratégia de Cache no App (3 Camadas)

O app **não baixa do S3 toda vez** que o usuário quer ver uma receita. O download do S3 acontece uma única vez por imagem. Depois disso, ela fica no armazenamento local do app em um sistema de cache de 3 níveis:

**Camada 1 — Cache em memória (LRU):**
- Bitmap já decodificado, pronto para exibir
- Capacidade: ~50MB (~20-30 imagens)
- Latência: 0ms (instantâneo)
- Não persiste entre sessões do app
- Usado para receitas que o usuário está vendo agora ou viu nos últimos minutos

**Camada 2 — Cache em disco (app storage):**
- Arquivo JPEG salvo no `cacheDir` ou `filesDir` do app
- Capacidade: ~500MB configurável
- Latência: 5-50ms
- Persiste entre sessões (Android pode limpar `cacheDir` se espaço ficar baixo)
- Organizado por `familyId/memberId/consultId/`
- Com ~40MB/ano de imagens, o cache de 500MB cobre ~12 anos sem precisar re-baixar

**Camada 3 — S3 (origem):**
- Fonte da verdade, acessado apenas quando camadas 1 e 2 não têm a imagem
- Requer presigned URL gerada pela Lambda (expira em 15 min)
- Latência: 200-800ms
- Após download, imagem é salva automaticamente nas camadas 2 e 1

**Fluxo de leitura:**
```
App pede imagem → Memória? (hit → exibe)
                → miss → Disco? (hit → carrega na memória → exibe)
                → miss → Lambda (presigned URL) → S3 download → salva disco → carrega memória → exibe
```

**Cache key estável (problema da presigned URL):**

Presigned URLs mudam a cada request (contêm token, expiração etc.), então não servem como chave de cache. A solução é usar o `s3Key` fixo da imagem como cache key:

```kotlin
// Coil — ImageLoader com cache configurado
val imageLoader = ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder(context)
            .maxSizePercent(0.15) // 15% da RAM do app
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(context.cacheDir.resolve("rx_images"))
            .maxSizeBytes(500L * 1024 * 1024) // 500MB
            .build()
    }
    .build()

// Cache key estável usando s3Key (nunca muda)
// Ex: "fam_abc/member_123/consult_456/receita-1.jpg"
fun loadPrescriptionImage(
    imageView: ImageView,
    s3Key: String,
    presignedUrl: String
) {
    val request = ImageRequest.Builder(context)
        .data(presignedUrl)
        .memoryCacheKey(s3Key)   // chave estável!
        .diskCacheKey(s3Key)     // chave estável!
        .target(imageView)
        .placeholder(R.drawable.rx_placeholder)
        .error(R.drawable.rx_error)
        .crossfade(true)
        .build()
    imageLoader.enqueue(request)
}
```

**Thumbnail vs. full resolution:** nas listagens, carrega thumbnail (~300px, ~30KB). No detalhe com zoom, carrega full (~2000px, ~400KB). São dois `s3Key` diferentes com caches separados.

### 6.7 Fluxo de Upload de Receita

O upload é assíncrono e não bloqueia o usuário. A consulta pode ser salva mesmo antes do upload completar.

**Passo a passo:**

1. **Usuário tira foto** — câmera captura em resolução original, app exibe preview imediato
2. **Compressão local** — app redimensiona para max 2000px e comprime JPEG 80%. Gera thumbnail 300px. Resultado: ~400KB (full) + ~30KB (thumb)
3. **Salva no cache local imediatamente** — imagem fica no disco do app antes do upload, garantindo disponibilidade offline
4. **Pede presigned URL** — `POST /consultations/{id}/upload-url` com `{ type: "prescription" | "exam" }` → Lambda retorna `{ uploadUrl, s3Key }` (expira em 5 min). Nada é salvo no banco ainda
5. **Upload direto ao S3** — app faz PUT na presigned URL (sem passar pela Lambda). Progress bar mostra andamento. ~400KB leva 1-3s em 4G
6. **Confirmação** — app envia `PATCH /consultations/{id}` com `{ addPrescriptionImage: { s3Key } }` ou `{ addExamImage: { examId, s3Key } }`. Lambda salva o `s3Key` na consulta. Ícone "nuvem ✓" aparece na receita

**Upload offline (sem internet):**

Imagem fica no cache local com status "pendente de upload". Um `WorkManager` job tenta reenviar quando a conexão voltar. O usuário vê ícone de "nuvem ↑" indicando upload pendente. A receita continua visível normalmente — está no disco local.

```kotlin
// WorkManager para retry de uploads pendentes
class UploadRetryWorker(ctx: Context, params: WorkerParameters)
    : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val pending = db.getPendingUploads()
        for (upload in pending) {
            try {
                val url = api.getUploadUrl(upload.consultId)
                s3Client.upload(url, upload.localPath)
                db.markUploaded(upload.id)
            } catch (e: Exception) {
                return Result.retry()
            }
        }
        return Result.success()
    }
}
```

**Cenários de uso do cache:**

| Cenário | Camada usada | Latência | Chama API? |
|---------|-------------|----------|------------|
| Primeira vez abrindo receita | S3 (download) | ~500ms | Sim |
| Reabrir mesma receita (mesma sessão) | Memória | 0ms | Não |
| Abrir receita dias depois | Disco | ~30ms | Não |
| Instalar em celular novo | S3 (download) | ~500ms/imagem | Sim |
| Sem internet (receita já vista) | Disco | ~30ms | Não |
| Sem internet (receita nunca vista) | — | — | Placeholder "sem conexão" |
| Cache disco cheio (500MB) | S3 (re-download) | ~500ms | Sim (LRU remove as mais antigas) |

---

## 7. Arquitetura Proposta

### 7.1 Visão Geral

```
┌─────────────────┐     ┌──────────────────────────┐     ┌─────────────┐
│   Kotlin App    │────▶│   API Gateway + Lambda    │────▶│  MongoDB    │
│   (Android)     │     │   (Node.js serverless)    │     │  (Atlas)    │
│                 │     │                            │     │             │
│ BuildConfig:    │     │  Header: X-Family-Id      │     └─────────────┘
│  FAMILY_ID      │     └────────────┬─────────────┘
└────────┬────────┘                  │
         │                           │  trigger
         │  upload direto             ▼
         ▼                    ┌──────────────┐
    ┌─────────┐              │ Lambda (OCR, │
    │   S3    │─────────────▶│  thumbnails) │
    │ (fotos) │   event      └──────────────┘
    └─────────┘
```

### 7.2 Por que MongoDB

MongoDB é uma boa escolha para este projeto por vários motivos:

- **Modelo de documento flexível:** perfil médico, consultas e medicamentos têm campos variáveis — documentos JSON se adaptam naturalmente sem migrations
- **Queries ricas:** busca por texto ($text index), filtros compostos, aggregation pipeline — tudo que DynamoDB exigiria GSIs complexos
- **Atlas Free Tier:** cluster M0 gratuito com 512MB — mais que suficiente para uso familiar (estimativa: ~5MB/ano de dados textuais)
- **Mongoose no Node.js:** ORM maduro com validação de schema, facilitando o desenvolvimento no Lambda
- **Atlas Search (v2):** se precisar de busca full-text mais sofisticada no futuro, já está integrado

**Conexão do Lambda com MongoDB Atlas:**
- Usar connection pooling com `mongoose` + cache de conexão entre invocações (reuso de warm Lambda)
- Configurar o Atlas para aceitar conexões dos IPs do API Gateway (ou usar VPC Peering se necessário)
- Timeout de conexão curto (5s) para não travar invocações Lambda

### 7.3 Modelo de Dados (MongoDB)

Duas collections na v1: `members` e `consultations`. Medicamentos são embedded dentro de `consultations` — sempre pertencem a uma consulta, nunca existem sozinhos. `familyId` presente em todo registro identifica o núcleo familiar (não há collection `families` — o ID vem do BuildConfig).

```javascript
// Collection: members
{
  _id: "uuid-v4-string",             // UUID gerado pelo servidor (crypto.randomUUID)
  familyId: "fam_a1b2c3d4-uuid",    // índice
  name: "João Silva",
  birthDate: ISODate,
  bloodType: "O+",
  weight: 78.5,
  height: 175,
  allergies: ["dipirona", "sulfa"],
  chronicConditions: ["hipertensão"],
  updatedAt: ISODate,
  syncedAt: ISODate,
  deletedAt: ISODate | null           // tombstone para soft-delete
}

// Collection: consultations (com medicamentos e exames embedded)
{
  _id: "uuid-v4-string",             // UUID gerado pelo servidor (crypto.randomUUID)
  familyId: "fam_a1b2c3d4-uuid",
  memberId: "uuid-v4-string",        // ref → members._id (UUID string)
  date: ISODate,
  doctor: { name: "Dra. Ana Costa", specialty: "Cardiologia", customSpecialty: null },
  clinic: "UBS Centro",
  reason: "dor de garganta",
  notes: "Diagnóstico: amigdalite bacteriana",
  tags: ["respiratório", "infecção"],
  returnOf: "uuid-v4-string" | null, // ref → consulta anterior (UUID string)
  prescriptionImages: [               // embedded — imagens da receita (sem _id)
    { s3Key: "fam_.../consult_.../img1.jpg", uploadedAt: ISODate }
  ],
  medications: [                      // embedded — sem _id individual
    {
      name: "Amoxicilina",
      activeIngredient: "amoxicilina tri-hidratada",
      dosage: "500mg",
      form: "COMPRIMIDO",             // FormaMedicamento enum
      frequency: "8 em 8 horas",
      contraindicated: false,         // flag de restrição
      restrictionReason: null,        // obrigatório quando contraindicated = true
      efficacy: "EFICAZ",             // Eficacia enum | null (não avaliou)
      sideEffects: "dor de estômago leve"
    }
  ],
  exams: [                            // embedded — tem _id UUID (necessário para referenciar ao anexar imagem)
    {
      _id: "uuid-v4-string",
      name: "Hemograma completo",
      notes: "Leucócitos levemente elevados",
      resultImages: [                 // resultado anexado depois via PATCH /consultations/{id}
        { s3Key: "fam_.../consult_.../exam-hemograma-1.jpg", uploadedAt: ISODate }
      ]
    }
  ],
  updatedAt: ISODate,
  syncedAt: ISODate,
  deletedAt: ISODate | null           // tombstone para soft-delete
}

// Collection: vaccines (v2)
{
  _id: "uuid-v4-string",
  familyId: "fam_a1b2c3d4-uuid",
  memberId: "uuid-v4-string",
  name: "COVID-19 Pfizer",
  dose: "2ª dose",
  date: ISODate,
  nextDoseDate: ISODate | null,
  location: "UBS Centro",
  notes: null
}

// Índices recomendados:
// members:        { familyId: 1 }, { familyId: 1, updatedAt: -1 }
// consultations:  { familyId: 1, memberId: 1, date: -1 }
// consultations:  { familyId: 1, tags: 1 }
// consultations:  { familyId: 1, updatedAt: -1 }
// consultations:  { familyId: 1, "medications.name": "text", "medications.activeIngredient": "text", "exams.name": "text", reason: "text" }
```

### 7.4 Endpoints da API

Todos os endpoints exigem `x-api-key` (validado pelo API Gateway) e `X-Family-Id` no header (validado pela Lambda).

```
# Membros
POST   /members                            → adicionar membro
GET    /members                            → listar membros da família
GET    /members/{id}                       → detalhe do membro
PATCH  /members/{id}                       → atualizar perfil (peso, alergias etc.)
DELETE /members/{id}                       → remover membro

# Consultas (com medicamentos embedded)
POST   /consultations                      → registrar consulta (com medicamentos no body)
GET    /consultations                      → listar (filtros: ?memberId, ?from, ?to, ?doctor, ?tag)
GET    /consultations/{id}                 → detalhe completo (medicamentos, exames e receitas inclusos)
PATCH  /consultations/{id}                 → editar (dados, medicamentos, exames, receitas)
DELETE /consultations/{id}                 → remover

# Imagens (receitas e resultados de exames)
POST   /consultations/{id}/upload-url      → gerar presigned URL para upload ao S3. Body indica tipo: "prescription" ou "exam". Backend gera s3Key no diretório correto (prescriptions/ ou exams/)
GET    /consultations/{id}/images          → listar presigned URLs de leitura de todas as imagens da consulta, agrupadas por tipo (prescriptions + exams)

# Sincronização
GET    /sync?since={timestamp}             → retorna registros modificados desde o timestamp (pull)
```

---

## 8. Roadmap Sugerido

### v1 — MVP (4-6 semanas)
- Setup: Lambda + API Gateway + MongoDB Atlas (2 collections: members, consultations) + S3 + API Key
- `familyId` via BuildConfig
- Room local como banco primário + sync manual com backend
- CRUD membros (com perfil médico: alergias, condições crônicas, contraindicados)
- CRUD consultas (com medicamentos, exames e receitas embedded)
- Upload e visualização de receitas e resultados de exames (S3 + presigned URLs + cache 3 camadas)
- Busca local (Room FTS) por medicamento, exame, motivo e médico

### v2 — Experiência Completa (4-6 semanas)
- Sugestão de histórico por tags (seção 5.6)
- Medicamentos de uso contínuo no perfil do membro (seção 5.2)
- Vacinas (seção 5.3)
- Compartilhamento de histórico médico via PDF/link (seção 5.5)
- Crianças e idosos: histórico de peso/altura, responsável (seção 5.4)

### v3 — Inteligência (4-6 semanas)
- OCR de receitas com preenchimento automático (seção 5.8)
- Alerta de interação medicamentosa (seção 5.1)
- QR code de ficha de emergência (seção 5.7)
- Dashboard com visão geral da saúde da família
- Auth opcional (Cognito) se decidir abrir para público

---

## 9. Riscos e Mitigações

| Risco | Impacto | Mitigação |
|-------|---------|-----------|
| API key vazada (APK decompilado) | Alto | API key no BuildConfig é extraível por reverse engineering. Mitigar com: Usage Plan com quota baixa, rotação periódica da key, monitoramento de uso anômalo no CloudWatch |
| Dados médicos vazados | Alto | Dupla camada (API key + familyId), HTTPS, presigned URLs curtas, S3 SSE |
| Usuário não adota (muito trabalho registrar) | Alto | UX mínima: foto da receita + 3 campos. OCR no v3 reduz ainda mais |
| Receita ilegível na foto | Médio | Guia de como fotografar, preview antes de salvar, permitir retake |
| Custo escala inesperadamente | Baixo | S3 lifecycle, MongoDB Atlas free tier, Lambda free tier, Usage Plan com quota |
| Perda do familyId ou API key | Médio | Documentar em local seguro; app mostra o familyId nas configurações; API key pode ser recriada no console AWS |
| Cold start do Lambda + MongoDB | Baixo | Connection caching, provisioned concurrency se necessário |
