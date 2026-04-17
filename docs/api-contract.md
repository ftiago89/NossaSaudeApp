# NossaSaúde Server — API Contract

> Referência completa para consumo do backend pelo app Android.  
> Base URL prod: `https://{api-id}.execute-api.sa-east-1.amazonaws.com/prod`  
> Base URL dev: `http://localhost:3000/dev`

---

## Autenticação

Todo request exige dois identificadores obrigatórios:

| Header | Descrição |
|--------|-----------|
| `x-api-key` | API Key do API Gateway. Configurada no `BuildConfig.API_KEY` |
| `X-Family-Id` | UUID do núcleo familiar. Configurada no `BuildConfig.FAMILY_ID` |

Requests sem `x-api-key` são rejeitados pelo API Gateway (antes de invocar a Lambda) com `403 Forbidden`.  
Requests sem `X-Family-Id` são rejeitados pela Lambda com `400 Bad Request`.

---

## Formato de resposta

### Sucesso

```json
// 200 OK ou 201 Created
{
  // corpo específico de cada endpoint
}
```

### Erro

```json
{
  "timeStamp": "2024-06-10T10:00:00.000Z",
  "status": 400,
  "statusDescription": "Bad Request",
  "type": "General Error",
  "errorCode": "MISSING_FAMILY_ID",
  "errorMessage": "X-Family-Id header is required",
  "errors": []         // presente apenas em erros de validação AJV (422)
}
```

### Códigos de status

| Código | Significado |
|--------|-------------|
| `200` | OK |
| `201` | Created |
| `400` | Bad Request — header obrigatório ausente ou JSON inválido |
| `404` | Not Found — recurso não encontrado ou não pertence à família |
| `422` | Unprocessable Entity — body falhou na validação de schema |
| `500` | Internal Server Error — erro inesperado no servidor |

### Códigos de erro (`errorCode`)

| Código | Status | Causa |
|--------|--------|-------|
| `MISSING_FAMILY_ID` | 400 | Header `X-Family-Id` ausente ou vazio |
| `INVALID_JSON` | 400 | Body não é JSON válido |
| `VALIDATION_ERROR` | 422 | Body falhou na validação de schema AJV |
| `NOT_FOUND` | 404 | Recurso não existe ou não pertence à família |

---

## Modelos de dados

### Member

```typescript
{
  _id: string              // UUID v4 gerado pelo servidor
  familyId: string
  name: string             // obrigatório
  birthDate: string | null // ISO 8601
  bloodType: string | null // enum: ver Enums
  weight: number | null    // kg, >= 0
  height: number | null    // cm, >= 0
  allergies: string[]
  chronicConditions: string[]
  syncedAt: string | null  // ISO 8601
  deletedAt: string | null // null = ativo; preenchido = soft-deleted
  createdAt: string        // ISO 8601
  updatedAt: string        // ISO 8601
}
```

### Consultation

```typescript
{
  _id: string              // UUID v4 gerado pelo servidor
  familyId: string
  memberId: string         // UUID — ref Member._id
  date: string             // ISO 8601, obrigatório
  reason: string           // obrigatório
  doctor: {
    name: string | null
    specialty: string | null   // ver lista de especialidades
    customSpecialty: string | null  // preenchido quando specialty = "OTHER"
  }
  clinic: string | null
  notes: string | null
  tags: string[]
  returnOf: string | null  // UUID — ref Consultation._id (consulta de retorno)
  prescriptionImages: PrescriptionImage[]
  medications: Medication[]
  exams: Exam[]
  syncedAt: string | null
  deletedAt: string | null
  createdAt: string
  updatedAt: string
}
```

### PrescriptionImage

```typescript
{
  s3Key: string      // chave estável no S3 — usar como cache key
  uploadedAt: string // ISO 8601
  // sem _id
}
```

### Medication

```typescript
{
  // sem _id
  name: string              // obrigatório
  activeIngredient: string | null
  dosage: string | null     // ex: "500mg"
  form: string | null       // enum: ver Enums
  frequency: string | null  // ex: "8 em 8 horas"
  contraindicated: boolean  // default: false
  restrictionReason: string // obrigatório quando contraindicated = true
  efficacy: string | null   // enum: ver Enums — null = não avaliado
  sideEffects: string | null
}
```

### Exam

```typescript
{
  _id: string        // UUID v4 — necessário para associar imagens de resultado
  name: string       // obrigatório
  notes: string | null
  resultImages: ResultImage[]
}
```

### ResultImage

```typescript
{
  s3Key: string      // chave estável no S3 — usar como cache key
  uploadedAt: string // ISO 8601
  // sem _id
}
```

---

## Enums

### bloodType
`"A+"` `"A-"` `"B+"` `"B-"` `"AB+"` `"AB-"` `"O+"` `"O-"`

### medication.form
`"COMPRIMIDO"` `"CAPSULA"` `"LIQUIDO"` `"POMADA"` `"INJETAVEL"` `"GOTAS"` `"SPRAY"` `"ADESIVO"` `"OUTRO"`

### medication.efficacy
`"EFICAZ"` `"PARCIAL"` `"INEFICAZ"` `null` *(null = não avaliado)*

### upload type
`"prescription"` `"exam"`

### doctor.specialty — especialidades CFM
O servidor **não valida** o valor de `specialty` — é uma string livre. A lista abaixo é a referência para o app montar o dropdown. Quando `specialty = "OTHER"`, preencher `customSpecialty` com texto livre.

```
CLINICA_GERAL, PEDIATRIA, ORTOPEDIA, DERMATOLOGIA, GINECOLOGIA,
CARDIOLOGIA, OFTALMOLOGIA, OTORRINOLARINGOLOGIA, NEUROLOGIA, PSIQUIATRIA,
UROLOGIA, ENDOCRINOLOGIA, GASTROENTEROLOGIA, PNEUMOLOGIA, REUMATOLOGIA,
NEFROLOGIA, ONCOLOGIA, HEMATOLOGIA, INFECTOLOGIA, GERIATRIA,
CIRURGIA_GERAL, CIRURGIA_PLASTICA, CIRURGIA_VASCULAR, CIRURGIA_TORACICA,
CIRURGIA_CABECA_PESCOCO, ANESTESIOLOGIA, RADIOLOGIA, PATOLOGIA,
MEDICINA_NUCLEAR, MEDICINA_INTENSIVA, MEDICINA_DO_TRABALHO,
MEDICINA_ESPORTIVA, MEDICINA_DE_FAMILIA, MEDICINA_DE_EMERGENCIA,
ALERGIA_IMUNOLOGIA, NEUROPEDIATRIA, NEONATOLOGIA, MASTOLOGIA,
COLOPROCTOLOGIA, HEPATOLOGIA, FISIATRIA, ACUPUNTURA, HOMEOPATIA,
NUTOLOGIA, GENETICA_MEDICA, MEDICINA_LEGAL, FONIATRIA,
HANSENOLOGIA, HIPERBARISMO, ANGIORRADIOLOGIA, ENDOSCOPIA,
DENSITOMETRIA, MEDICINA_DE_TRAFEGO, SEXOLOGIA, OTHER
```

---

## Endpoints

---

### Members

#### `POST /members`
Cria um novo membro na família.

**Request body**
```json
{
  "name": "João Silva",           // string, obrigatório, minLength: 1
  "birthDate": "1985-03-15T00:00:00.000Z",  // ISO 8601, opcional
  "bloodType": "O+",              // enum bloodType, opcional
  "weight": 80.5,                 // number >= 0, opcional
  "height": 178,                  // number >= 0, opcional
  "allergies": ["dipirona"],      // string[], opcional
  "chronicConditions": ["hipertensão"]  // string[], opcional
}
```

**Resposta de sucesso — `201 Created`**
```json
// Objeto Member completo
```

**Erros**
| Status | errorCode | Causa |
|--------|-----------|-------|
| 400 | `MISSING_FAMILY_ID` | Header ausente |
| 422 | `VALIDATION_ERROR` | `name` ausente ou campo inválido |

---

#### `GET /members`
Lista todos os membros ativos da família.

**Query params:** nenhum

**Resposta de sucesso — `200 OK`**
```json
[ /* array de Member */ ]
```
> Retorna apenas membros com `deletedAt = null`, ordenados por `name`.

**Erros**
| Status | errorCode | Causa |
|--------|-----------|-------|
| 400 | `MISSING_FAMILY_ID` | Header ausente |

---

#### `GET /members/{id}`
Busca um membro pelo ID.

**Resposta de sucesso — `200 OK`**
```json
// Objeto Member completo
```

**Erros**
| Status | errorCode | Causa |
|--------|-----------|-------|
| 400 | `MISSING_FAMILY_ID` | Header ausente |
| 404 | `NOT_FOUND` | Membro não existe ou não pertence à família |

---

#### `PATCH /members/{id}`
Atualiza campos de um membro. Apenas os campos enviados são alterados.

**Request body** — ao menos 1 campo obrigatório
```json
{
  "name": "João Silva Atualizado",
  "weight": 82,
  "allergies": ["dipirona", "ibuprofeno"]
  // qualquer subset dos campos do Member (exceto _id, familyId, deletedAt, syncedAt)
}
```

**Resposta de sucesso — `200 OK`**
```json
// Objeto Member atualizado
```

**Erros**
| Status | errorCode | Causa |
|--------|-----------|-------|
| 400 | `MISSING_FAMILY_ID` | Header ausente |
| 404 | `NOT_FOUND` | Membro não existe |
| 422 | `VALIDATION_ERROR` | Campo inválido (ex: bloodType fora do enum) |

---

#### `DELETE /members/{id}`
Soft-delete de um membro (seta `deletedAt`).

**Request body:** nenhum

**Resposta de sucesso — `200 OK`**
```json
{ "message": "Member deleted" }
```

**Erros**
| Status | errorCode | Causa |
|--------|-----------|-------|
| 400 | `MISSING_FAMILY_ID` | Header ausente |
| 404 | `NOT_FOUND` | Membro não existe |

---

### Consultations

#### `POST /consultations`
Cria uma nova consulta com medicamentos e exames opcionais.

**Request body**
```json
{
  "memberId": "uuid-do-membro",        // string UUID, obrigatório
  "date": "2024-06-10T10:00:00.000Z", // ISO 8601, obrigatório
  "reason": "Dor de cabeça",          // string, obrigatório
  "doctor": {                          // opcional
    "name": "Dr. Carlos Mendes",
    "specialty": "NEUROLOGIA",
    "customSpecialty": null
  },
  "clinic": "Clínica São Lucas",       // opcional
  "notes": "Paciente relata...",       // opcional
  "tags": ["neurologia", "cefaleia"], // string[], opcional
  "returnOf": "uuid-consulta-anterior", // UUID, opcional
  "medications": [                     // opcional
    {
      "name": "Amitriptilina",         // obrigatório
      "activeIngredient": "cloridrato de amitriptilina",
      "dosage": "25mg",
      "form": "COMPRIMIDO",
      "frequency": "1x ao dia à noite",
      "contraindicated": false,
      "efficacy": "EFICAZ",
      "sideEffects": null
    },
    {
      "name": "Dipirona",
      "contraindicated": true,
      "restrictionReason": "alergia confirmada" // obrigatório quando contraindicated = true
    }
  ],
  "exams": [                           // opcional
    {
      "name": "Hemograma Completo",    // obrigatório
      "notes": "Resultado normal"
    }
  ]
}
```

**Resposta de sucesso — `201 Created`**
```json
// Objeto Consultation completo (com _id dos exams preenchido)
```

**Erros**
| Status | errorCode | Causa |
|--------|-----------|-------|
| 400 | `MISSING_FAMILY_ID` | Header ausente |
| 422 | `VALIDATION_ERROR` | `memberId`/`date`/`reason` ausentes, ou `contraindicated: true` sem `restrictionReason` |

---

#### `GET /consultations`
Lista consultas da família com filtros opcionais.

**Query params**
| Param | Tipo | Descrição |
|-------|------|-----------|
| `memberId` | string | Filtra por membro |
| `from` | ISO 8601 | Data mínima da consulta (inclusive) |
| `to` | ISO 8601 | Data máxima da consulta (inclusive) |
| `doctor` | string | Busca parcial case-insensitive no nome do médico |
| `tag` | string | Filtra por tag exata |

**Resposta de sucesso — `200 OK`**
```json
[ /* array de Consultation, ordenado por date desc */ ]
```
> Retorna apenas consultas com `deletedAt = null`.

---

#### `GET /consultations/{id}`
Busca uma consulta pelo ID com todos os dados (medicamentos, exames, imagens).

**Resposta de sucesso — `200 OK`**
```json
// Objeto Consultation completo
```

**Erros**
| Status | errorCode | Causa |
|--------|-----------|-------|
| 400 | `MISSING_FAMILY_ID` | Header ausente |
| 404 | `NOT_FOUND` | Consulta não existe |

---

#### `PATCH /consultations/{id}`
Atualiza campos de uma consulta. Também usado para registrar e remover imagens do S3.

**Request body** — ao menos 1 campo obrigatório

*Atualizar campos gerais:*
```json
{
  "notes": "Paciente melhorou após medicação.",
  "tags": ["neurologia", "retorno-positivo"]
}
```

*Registrar imagem de receita (após upload S3 concluído):*
```json
{
  "addPrescriptionImage": {
    "s3Key": "familyId/memberId/consultation/consultId/prescriptions/uuid.jpg"
  }
}
```

*Registrar imagem de resultado de exame (após upload S3 concluído):*
```json
{
  "addExamImage": {
    "examId": "uuid-do-exame",   // _id do exame dentro da consulta
    "s3Key": "familyId/memberId/consultation/consultId/exams/uuid.jpg"
  }
}
```

*Remover imagem de receita (remove do banco e apaga do S3):*
```json
{
  "removePrescriptionImage": {
    "s3Key": "familyId/memberId/consultation/consultId/prescriptions/uuid.jpg"
  }
}
```

*Remover imagem de resultado de exame (remove do banco e apaga do S3):*
```json
{
  "removeExamImage": {
    "examId": "uuid-do-exame",
    "s3Key": "familyId/memberId/consultation/consultId/exams/uuid.jpg"
  }
}
```

> Os campos gerais e os campos de imagem (`add*` / `remove*`) podem ser enviados juntos no mesmo PATCH.

**Resposta de sucesso — `200 OK`**
```json
// Objeto Consultation atualizado
```

**Erros**
| Status | errorCode | Causa |
|--------|-----------|-------|
| 400 | `MISSING_FAMILY_ID` | Header ausente |
| 404 | `NOT_FOUND` | Consulta não existe, ou `examId` não encontrado no exame |
| 422 | `VALIDATION_ERROR` | Campo inválido |

---

#### `DELETE /consultations/{id}`
Soft-delete de uma consulta.

**Resposta de sucesso — `200 OK`**
```json
{ "message": "Consultation deleted" }
```

**Erros**
| Status | errorCode | Causa |
|--------|-----------|-------|
| 400 | `MISSING_FAMILY_ID` | Header ausente |
| 404 | `NOT_FOUND` | Consulta não existe |

---

### Images (S3 Presigned URLs)

#### `POST /consultations/{id}/upload-url`
Gera uma presigned URL para upload direto ao S3. **Não salva nada no banco** — o cliente deve confirmar o upload via `PATCH /consultations/{id}`.

**Request body**
```json
{
  "type": "prescription",   // enum: "prescription" | "exam", obrigatório
  "contentType": "image/jpeg"  // MIME type, opcional (default: "image/jpeg")
}
```

**Resposta de sucesso — `200 OK`**
```json
{
  "uploadUrl": "https://s3.amazonaws.com/...?X-Amz-Signature=...",
  "s3Key": "familyId/memberId/consultation/consultId/prescriptions/uuid.jpg"
}
```

> `uploadUrl` expira em **5 minutos**.  
> `s3Key` é estável e permanente — usar como cache key no app.

**Fluxo completo de upload:**
```
1. POST /consultations/{id}/upload-url  →  { uploadUrl, s3Key }
2. PUT <uploadUrl>                       →  upload binário direto ao S3
   Header: Content-Type: image/jpeg
   Body: bytes da imagem
3. PATCH /consultations/{id}            →  { addPrescriptionImage: { s3Key } }
                                            ou { addExamImage: { examId, s3Key } }
```

**Erros**
| Status | errorCode | Causa |
|--------|-----------|-------|
| 400 | `MISSING_FAMILY_ID` | Header ausente |
| 404 | `NOT_FOUND` | Consulta não existe |
| 422 | `VALIDATION_ERROR` | `type` ausente ou inválido |

---

#### `GET /consultations/{id}/images`
Retorna presigned URLs de leitura para todas as imagens da consulta, agrupadas por tipo.

**Resposta de sucesso — `200 OK`**
```json
{
  "prescriptions": [
    {
      "s3Key": "familyId/memberId/consultation/consultId/prescriptions/uuid.jpg",
      "url": "https://s3.amazonaws.com/...?X-Amz-Signature=...",
      "uploadedAt": "2024-06-10T10:05:00.000Z"
    }
  ],
  "exams": [
    {
      "examId": "uuid-exam-1",
      "examName": "Hemograma Completo",
      "images": [
        {
          "s3Key": "familyId/memberId/consultation/consultId/exams/uuid.jpg",
          "url": "https://s3.amazonaws.com/...?X-Amz-Signature=...",
          "uploadedAt": "2024-06-13T08:30:00.000Z"
        }
      ]
    }
  ]
}
```

> `url` expira em **15 minutos**.  
> Usar `s3Key` como cache key (estável) e `url` apenas para o download.  
> Após download, armazenar a imagem em cache local indexada pelo `s3Key`.

**Erros**
| Status | errorCode | Causa |
|--------|-----------|-------|
| 400 | `MISSING_FAMILY_ID` | Header ausente |
| 404 | `NOT_FOUND` | Consulta não existe |

---

### Sync

#### `GET /sync`
Retorna todos os membros e consultas modificados após o timestamp informado. Inclui registros deletados (com `deletedAt` preenchido) para propagação de deleções ao banco local.

**Query params**
| Param | Tipo | Descrição |
|-------|------|-----------|
| `since` | ISO 8601 ou Unix ms | Retorna registros com `updatedAt > since`. Omitir ou `since=0` retorna tudo |

**Resposta de sucesso — `200 OK`**
```json
{
  "members": [ /* array de Member (incluindo deletados) */ ],
  "consultations": [ /* array de Consultation (incluindo deletados) */ ],
  "syncedAt": "2024-06-10T10:00:00.000Z"
}
```

> Salvar `syncedAt` como novo `lastPullTimestamp` para o próximo pull.  
> Registros com `deletedAt != null` devem ser removidos do banco local.

**Erros**
| Status | errorCode | Causa |
|--------|-----------|-------|
| 400 | `MISSING_FAMILY_ID` | Header ausente |

---

## Protocolo de sincronização

```
Pull (ao abrir o app ou botão manual):
  GET /sync?since={lastPullTimestamp}
  → Para cada registro recebido:
    - deletedAt != null → remover do Room local
    - não existe localmente (por remoteId) → inserir
    - existe e updatedAt <= syncedAt → sobrescrever com dados do servidor
    - existe e updatedAt > syncedAt  → manter local (edição pendente de push)
  → Salvar syncedAt como novo lastPullTimestamp

Push (após criar/editar/deletar):
  POST   /members              → criar membro
  PATCH  /members/{remoteId}   → atualizar membro
  DELETE /members/{remoteId}   → soft-delete membro

  POST   /consultations              → criar consulta
  PATCH  /consultations/{remoteId}   → atualizar consulta / adicionar imagens
  DELETE /consultations/{remoteId}   → soft-delete consulta
```

---

## Estrutura de s3Key

```
{familyId}/{memberId}/consultation/{consultationId}/prescriptions/{uuid}.jpg
{familyId}/{memberId}/consultation/{consultationId}/exams/{uuid}.jpg
```

O `s3Key` é determinístico e estável — nunca muda após criação. Usar como chave de cache local (disco e memória).
