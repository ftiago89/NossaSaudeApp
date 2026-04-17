# Release Notes — NossaSaúde

---

## v0.0.1 (2026-04-17)

Primeira versão estável do app.

### Membros da família
- Cadastro, edição e exclusão de membros
- Dados pessoais: nome, data de nascimento, tipo sanguíneo, peso e altura
- Informações de saúde: alergias e condições crônicas
- Avatar colorido com iniciais gerado automaticamente

### Consultas médicas
- Cadastro, edição e exclusão de consultas por membro
- Campos: data, motivo, médico, especialidade, clínica, observações, tags e retorno de consulta anterior
- Listagem de consultas no perfil do membro

### Medicamentos
- Registro de medicamentos por consulta
- Campos: nome, princípio ativo, dosagem, forma, frequência, eficácia e efeitos colaterais
- Marcação de medicamentos contraindicados com motivo da restrição
- Alerta de contraindicação exibido no perfil do membro
- Alerta em tempo real ao adicionar medicamento já contraindicado em nova consulta

### Exames
- Registro de exames por consulta com nome e observações
- Upload e visualização de imagens de resultado por exame

### Imagens
- Upload de fotos de receitas médicas por consulta
- Upload de fotos de resultados de exame
- Visualizador de imagens em tela cheia com suporte a múltiplas fotos

### Busca
- Busca unificada por médico, medicamento, motivo, clínica e tag

### Sincronização
- Funcionamento offline — dados salvos localmente no dispositivo
- Sincronização manual com backend: envio de criações, edições e exclusões
- Recebimento de atualizações do servidor com resolução de conflitos (last-write-wins)
- Upload de imagens em background com reenvio automático em caso de falha

### Geral
- Suporte a Android 8.0 ou superior (API 26+)
