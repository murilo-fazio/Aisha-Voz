# Implementacao da voz de qualidade da Aisha

Este patch adiciona transcricao e sintese de voz pelo OpenRouter, mantendo o chat de texto e a chave que o projeto ja usa.

## Arquitetura

- Navegador grava com `MediaRecorder` e envia o arquivo para `POST /api/voice/transcribe`.
- Spring valida sessao/CSRF e encaminha o multipart ao Flask.
- Flask chama `openai/whisper-1` no OpenRouter e devolve `{ "text": "..." }`.
- O navegador envia a resposta textual da Aisha para `POST /api/voice/speak`.
- Spring encaminha ao Flask; Flask chama `microsoft/mai-voice-2.1` com a voz `pt-BR-Luana:MAI-Voice-2.1` e devolve MP3.

A chave `OPENROUTER_API_KEY` permanece somente no Flask. Nenhuma chave nova e necessaria.

## Aplicar o patch

1. Faca uma copia de seguranca do projeto ou crie um commit antes de substituir arquivos.
2. Extraia este ZIP na raiz do repositorio, mantendo os caminhos e permitindo substituir os arquivos existentes.
3. **Nao substitua nem apague seu `.env`**. O patch nao contem `.env`.
4. Os modelos e limites ja possuem valores padrao no `docker-compose.yml`. Podem ser sobrescritos no `.env`:

```dotenv
STT_MODEL=openai/whisper-1
TTS_MODEL=microsoft/mai-voice-2.1
TTS_VOICE=pt-BR-Luana:MAI-Voice-2.1
AUDIO_TIMEOUT_SECONDS=120
AI_READ_TIMEOUT_SECONDS=150
VOICE_MAX_AUDIO_MB=10
TTS_MAX_CHARS=4000
```

## Rebuild local com a aplicacao dentro do Docker

Na raiz do projeto, execute:

```powershell
docker compose --profile full up -d --build
```

Acompanhe os logs:

```powershell
docker compose --profile full logs -f ai-service app
```

Se o Spring Boot estiver sendo executado pela IDE, reconstrua/suba o servico Flask e reinicie a aplicacao Spring pela IDE. Nesse modo, use a configuracao de Compose que publica o `ai-service` no host (`docker-compose.tools.yml`), conforme o fluxo de desenvolvimento do projeto.

## Teste em camadas

1. Entre na Aisha com uma conta autenticada.
2. Marque **Ler as respostas em voz alta**.
3. Clique no microfone, fale uma frase e clique novamente para encerrar e enviar.
4. A transcricao deve aparecer no campo e ser enviada ao chat.
5. A resposta textual da Aisha deve ser reproduzida em MP3.
6. No DevTools > Network, confira:
   - `POST /api/voice/transcribe`: status 200 e JSON com `text`.
   - `POST /api/voice/speak`: status 200 e resposta `audio/mpeg`.

Se falhar, consulte `docker compose --profile full logs -f ai-service app`. O Flask registra o modelo usado e a categoria da falha, sem registrar a chave de API.

## Seguranca e limites

- `/api/voice/**` passa pelos interceptors de autenticacao e CSRF ja existentes no Spring.
- O Spring encaminha ao Flask usando o `X-Internal-Token` configurado no `RestClient` existente.
- O Flask exige o token interno em `/stt` e `/tts`.
- O audio tem limite de 10 MB; o texto enviado ao TTS tem limite de 4000 caracteres.
- O Compose de producao inclui limite de requisicoes por IP para `/api/voice/` e timeout maior no Nginx.
- STT e TTS consomem credito no OpenRouter. Configure limites de uso na chave do OpenRouter e mantenha a opcao de leitura desmarcada quando nao quiser gerar audio.

## Compatibilidade

A gravacao requer `MediaRecorder`, `getUserMedia` e um contexto seguro (HTTPS ou `localhost`). O Chrome atual oferece esses recursos. O frontend envia o formato gravado pelo navegador (normalmente WebM/Opus); o OpenRouter aceita multipart de audio em formatos comuns, incluindo WebM, MP4, WAV e MP3.
