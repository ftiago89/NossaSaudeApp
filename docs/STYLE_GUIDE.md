# NossaSaúde — Style Guide & Design Tokens

> Referência de design para o app Android (Kotlin/Jetpack Compose).
> Extraído do mockup aprovado da Home Screen (`home-mockup.html`).
> O Claude Code deve consultar este arquivo ao criar qualquer tela ou componente.

---

## 1. Filosofia de Design

**Tom:** Quente, acolhedor, familiar. Fugir do visual "hospitalar" frio.
**Estilo:** Clean com personalidade. Cards com bordas suaves, cores quentes, tipografia com peso.
**Princípios:**
- Informação médica crítica (alergias, contraindicações) sempre com destaque visual forte
- Hierarquia clara: títulos pesados, meta-info leve, ações primárias em coral
- Offline-first: sempre mostrar status de sincronização de forma sutil mas visível
- Mobile-first: espaçamentos generosos para toque, scroll vertical contínuo

---

## 2. Paleta de Cores

### Cores principais

```kotlin
// Theme.kt — Color definitions
object NossaSaudeColors {
    // Backgrounds
    val Background       = Color(0xFFFAF7F4)  // bege claro, fundo geral
    val Surface          = Color(0xFFFFFFFF)  // branco, cards e superfícies
    val SurfaceWarm      = Color(0xFFFFF8F2)  // bege quente, fundo alternativo

    // Primary (coral)
    val Primary          = Color(0xFFE8634A)  // coral — ações principais, FAB, destaques
    val PrimarySoft      = Color(0xFFFCEAE6)  // coral claro — avatares, badges leves
    val PrimaryDark      = Color(0xFFC94E38)  // coral escuro — pressed states

    // Accent (teal)
    val AccentTeal       = Color(0xFF2BA89C)  // teal — sync, sucesso, informações positivas
    val AccentTealSoft   = Color(0xFFE6F6F4)  // teal claro — banners de status

    // Text
    val TextPrimary      = Color(0xFF2D2A26)  // marrom escuro — títulos, nomes
    val TextSecondary    = Color(0xFF7A756E)  // marrom médio — meta-info, subtítulos
    val TextTertiary     = Color(0xFFADA69E)  // marrom claro — timestamps, placeholders

    // Semantic
    val Border           = Color(0xFFEDE8E3)  // divisores, bordas de cards
    val AllergyBg        = Color(0xFFFDE8E4)  // fundo chip de alergia
    val AllergyText      = Color(0xFFC94E38)  // texto chip de alergia
    val ConditionBg      = Color(0xFFE6F0FF)  // fundo chip de condição crônica
    val ConditionText    = Color(0xFF3B6EB5)  // texto chip de condição crônica
    val BloodTypeBg      = Color(0xFFFFF0F0)  // fundo badge tipo sanguíneo
    val BloodTypeText    = Color(0xFFD64545)  // texto badge tipo sanguíneo
    val SyncPending      = Color(0xFFF5A623)  // amarelo — sync pendente
    val Danger           = Color(0xFFD64545)  // vermelho — delete, contraindicação
    val DangerSoft       = Color(0xFFFDE8E8)  // fundo vermelho claro — alertas

    // Avatar colors (rotação por membro)
    val AvatarCoral      = PrimarySoft to Primary
    val AvatarTeal       = AccentTealSoft to AccentTeal
    val AvatarBlue       = Color(0xFFE6F0FF) to Color(0xFF3B6EB5)
    val AvatarAmber      = Color(0xFFFFF3E0) to Color(0xFFE8964A)
}
```

### Regra de rotação de avatar

Cada membro recebe uma cor baseada no índice: `index % 4` → coral, teal, blue, amber. A cor é o par `(background, foreground)` para o círculo com iniciais.

---

## 3. Tipografia

### Fonte

**Display/Títulos:** Plus Jakarta Sans (weight 700–800)
**Body/Meta:** DM Sans (weight 400–500)

```kotlin
// Se não disponível no sistema, usar:
// Display: FontFamily.Default com weight Bold/ExtraBold
// Body: FontFamily.Default com weight Normal/Medium

// Typography.kt
val NossaSaudeTypography = Typography(
    // App title "NossaSaúde"
    headlineLarge = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        letterSpacing = (-0.8).sp,
        lineHeight = 33.sp,
    ),
    // Section titles ("Membros")
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = (-0.2).sp,
    ),
    // Member name in card
    titleSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = (-0.2).sp,
    ),
    // Greeting, section subtitle
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.3.sp,
    ),
    // Meta info (idade, peso)
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
    ),
    // Chips
    labelSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.1.sp,
    ),
    // Timestamps, footer info
    bodyExtraSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
    ),
)
```

### Hierarquia resumida

| Uso                        | Tamanho | Peso       | Cor            |
|----------------------------|---------|------------|----------------|
| Título do app              | 30sp    | ExtraBold  | TextPrimary + Primary (span) |
| Título de seção            | 16sp    | Bold       | TextPrimary    |
| Nome do membro             | 16sp    | Bold       | TextPrimary    |
| Greeting / subtítulo       | 14sp    | Medium     | TextSecondary  |
| Meta-info (idade, peso)    | 12.5sp  | Normal     | TextSecondary  |
| Chips (alergia, condição)  | 11sp    | SemiBold   | AllergyText / ConditionText |
| Timestamp / footer         | 11.5sp  | Normal     | TextTertiary   |
| Badge (sync count)         | 10sp    | Bold       | White          |
| Stat number                | 22sp    | ExtraBold  | Primary / Teal / Blue |
| Stat label                 | 11sp    | SemiBold   | TextSecondary  |

---

## 4. Espaçamentos & Dimensões

```kotlin
object Spacing {
    val screenPaddingH = 24.dp    // padding horizontal das telas
    val cardPadding    = 18.dp    // padding interno dos cards
    val cardGap        = 12.dp    // gap entre cards na lista
    val chipGap        = 6.dp     // gap entre chips
    val sectionGap     = 16.dp    // espaço entre seções
    val iconBtnSize    = 42.dp    // botões de ícone (busca, sync)
    val avatarSize     = 48.dp    // avatar do membro
    val fabSize        = 58.dp    // FAB
}

object Radius {
    val small  = 10.dp   // chips internos, stat cards, banners
    val medium = 16.dp   // cards de membro, formulários
    val large  = 24.dp   // bottom sheets, modais
    val full   = 999.dp  // chips, avatares, badges, FAB
}
```

---

## 5. Elevações & Sombras

```kotlin
object Elevation {
    val cardDefault  = 1.dp   // cards em repouso
    val cardHover    = 4.dp   // cards em foco/pressed
    val fab          = 8.dp   // FAB
    val modal        = 16.dp  // bottom sheets, dialogs
}
```

No Compose, usar `tonalElevation` + `shadowElevation` nos cards. Evitar sombras pesadas — manter visual leve.

---

## 6. Componentes Padrão

### 6.1 Member Card

```
┌─────────────────────────────────────┐
│ [Avatar 48dp]  Nome Completo        │
│                38 anos · 62kg  [A+] │
│                                     │
│ [⚠ Dipirona] [⚠ Sulfa] [Hipert.]  │
│─────────────────────────────────────│
│ 📅 Última consulta: 28 mar      ›  │
└─────────────────────────────────────┘
```

- Background: Surface (branco)
- Border radius: medium (16dp)
- Barra lateral esquerda (4dp largura): transparente por padrão, Primary (coral) no hover/pressed
- Footer separado por Border (1dp) com timestamp e chevron
- Chips de alergia: fundo AllergyBg, texto AllergyText, prefixo "⚠"
- Chips de condição: fundo ConditionBg, texto ConditionText
- Badge de tipo sanguíneo: fundo BloodTypeBg, texto BloodTypeText, radius full

### 6.2 Avatar

- Circular, 48dp
- Fundo: cor soft do par (índice do membro % 4)
- Texto: iniciais (2 letras), cor forte do par, 20sp ExtraBold
- Cores disponíveis: coral, teal, blue, amber (ver seção 2)

### 6.3 Chip

```kotlin
// Chip genérico
@Composable
fun NsChip(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    prefix: String? = null,  // "⚠" para alergias
)
```

- Padding: 4dp vertical, 10dp horizontal
- Radius: full
- Font: labelSmall (11sp SemiBold)

### 6.4 Icon Button (Top Bar)

- Circular, 42dp
- Background: Surface (branco)
- Shadow: cardDefault
- Ícone: 20dp, stroke TextPrimary
- Badge (opcional): circular 18dp, background Primary, texto branco 10sp Bold, posição top-right com offset (-2dp)

### 6.5 FAB (Floating Action Button)

- Circular, 58dp
- Background: Primary (coral)
- Ícone: 26dp, stroke branco, strokeWidth 2.5
- Shadow: coral com 35% opacity, blur 20dp
- Posição: bottom-right, 24dp da borda direita, 32dp da borda inferior
- Animação de entrada: scale 0.5→1 com ease

### 6.6 Sync Bar (Banner de Status)

- Background: AccentTealSoft
- Radius: small (10dp)
- Margin horizontal: 24dp
- Padding: 10dp vertical, 14dp horizontal
- Ícone checkmark + texto "Sincronizado" em AccentTeal, SemiBold 12.5sp
- Timestamp alinhado à direita em cor mais clara

### 6.7 Stat Card

- Background: Surface
- Radius: small (10dp)
- Padding: 14dp vertical, 12dp horizontal
- Número: 22sp ExtraBold, cor semântica (Primary, Teal, ou Blue)
- Label: 11sp SemiBold TextSecondary
- Layout: flex row com 3 cards de largura igual

### 6.8 Empty State

- Ilustração centralizada (ícone grande ou SVG simples)
- Texto: "Nenhum membro cadastrado" em TextSecondary, 14sp Medium
- Subtexto: "Adicione o primeiro membro da família" em TextTertiary, 12.5sp
- Botão primário: "Adicionar membro" em Primary, radius full, padding 12×24

---

## 7. Ícones

Usar set **Lucide** (ou equivalente outline, strokeWidth 2, strokeLinecap round).

| Ação            | Ícone             |
|-----------------|-------------------|
| Busca           | Search (lupa)     |
| Sync            | RefreshCw         |
| Adicionar       | Plus              |
| Voltar          | ChevronLeft       |
| Avançar/abrir   | ChevronRight      |
| Calendário      | Calendar          |
| Editar          | Pencil            |
| Deletar         | Trash2            |
| Câmera          | Camera            |
| Receita/imagem  | Image             |
| Medicamento     | Pill              |
| Exame           | TestTube / FlaskConical |
| Sync OK         | CheckCircle       |
| Sync pendente   | CloudOff          |
| Upload pendente | CloudUpload (↑)   |
| Configurações   | Settings          |
| Menu overflow   | MoreVertical      |
| Alerta          | AlertTriangle     |

---

## 8. Animações

| Contexto              | Tipo                  | Duração  |
|-----------------------|-----------------------|----------|
| Cards aparecendo      | fadeSlideIn (Y +16dp) | 500ms ease, stagger 100ms |
| FAB aparecendo        | scale 0.5→1           | 500ms ease, delay 600ms   |
| Sync girando          | rotate 360°           | 800ms/volta, linear       |
| Card pressed          | translateY -1dp       | 250ms cubic-bezier        |
| Navegação entre telas | Shared element (nome do membro, avatar) | 300ms |
| Toast de feedback     | slideUp + fade        | 300ms in, 200ms out       |

---

## 9. Padrões de Tela

### Estrutura base de toda tela

```
┌─ Status Bar (sistema) ──────────────┐
│                                     │
│ ← Título da Tela          [ações]  │  ← Top App Bar
│                                     │
│ ┌─ Conteúdo scrollável ──────────┐ │
│ │                                 │ │
│ │  (seções, cards, formulários)   │ │
│ │                                 │ │
│ └─────────────────────────────────┘ │
│                                     │
│                              [FAB] │  ← se aplicável
└─────────────────────────────────────┘
```

### Top App Bar

- Home: sem botão voltar, título "NossaSaúde" grande, ações busca + sync
- Telas internas: botão voltar (ChevronLeft), título menor (titleMedium), ações contextuais (Editar, Menu)
- Background: transparente (mesmo fundo da tela)

### Formulários

- Campos: OutlinedTextField com borda Border, focus borda Primary
- Labels: TextSecondary 12.5sp acima do campo
- Campos obrigatórios: label com "*" em Primary
- Seções: separadas por header (titleMedium) com 24dp de margem top
- Dropdowns: ExposedDropdownMenuBox com mesmo estilo dos TextFields
- Tags (alergias, condições): campo de texto + botão "+" que adiciona chip. Chips aparecem abaixo com "×" para remover
- Botão salvar: na TopAppBar (canto direito), texto "Salvar" em Primary

### Detalhe (visualização)

- Seções com headers e ícones à esquerda
- Cards internos para medicamentos e exames
- Imagens em grid 2 colunas (thumbnails), clique abre fullscreen
- Status de sync no topo (ícone + texto)

---

## 10. Indicadores de Sync (uso em todas as telas)

| Estado                   | Ícone         | Cor          | Texto           |
|--------------------------|---------------|--------------|-----------------|
| Sincronizado             | CheckCircle   | AccentTeal   | —               |
| Pendente de envio        | CloudOff      | SyncPending  | "Não sincronizado" |
| Upload de imagem pendente| CloudUpload ↑ | SyncPending  | "Upload pendente"  |
| Sincronizando            | RefreshCw ↻   | AccentTeal   | "Sincronizando..." |
| Erro de sync             | AlertTriangle | Danger       | "Falha ao sincronizar" |

---

## 11. Alertas de Contraindicação

Quando o usuário digita o nome de um medicamento no formulário de consulta e há match com um medicamento contraindicado do membro:

```
┌─ Banner vermelho ────────────────────┐
│ ⚠ Medicamento contraindicado!        │
│ "Dipirona" marcado como restrito     │
│ para João Silva.                     │
│ Motivo: reação alérgica severa       │
└──────────────────────────────────────┘
```

- Background: DangerSoft
- Border-left: 4dp Danger
- Ícone: AlertTriangle em Danger
- Texto: bodySmall, cor Danger
- Radius: small (10dp)

---

## 12. Referência de Mockups

| Tela                     | Arquivo                         | Status    |
|--------------------------|---------------------------------|-----------|
| Home (lista de membros)  | `app/docs/design/home-mockup.html` | ✅ Aprovado |
| Perfil do membro         | —                               | Pendente  |
| Formulário de membro     | —                               | Pendente  |
| Formulário de consulta   | —                               | Pendente  |
| Detalhe da consulta      | —                               | Pendente  |
| Busca                    | —                               | Pendente  |
| Viewer de imagem         | —                               | Pendente  |
| Configurações            | —                               | Pendente  |
