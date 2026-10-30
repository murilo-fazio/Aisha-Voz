package com.example.minilogin.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** Encaminha audio entre o navegador e o ai-service, sem expor a chave do provedor. */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private static final Logger log = LoggerFactory.getLogger(VoiceController.class);
    private static final long MAX_AUDIO_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_TEXT_CHARS = 4000;

    private final RestClient aiClient;

    public VoiceController(RestClient aiRestClient) {
        this.aiClient = aiRestClient;
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> transcribe(@RequestParam("audio") MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Envie uma gravacao de audio."));
        }
        if (audio.getSize() > MAX_AUDIO_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("error", "O audio deve ter no maximo 10 MB."));
        }

        String filename = audio.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "audio.webm";
        } else {
            filename = filename.replace("\\", "/");
            filename = filename.substring(filename.lastIndexOf('/') + 1);
        }

        final String safeFilename = filename;
        ByteArrayResource resource;
        try {
            resource = new ByteArrayResource(audio.getBytes()) {
                @Override
                public String getFilename() {
                    return safeFilename;
                }
            };
        } catch (IOException e) {
            log.warn("Nao foi possivel ler o audio enviado para transcricao.", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Nao foi possivel ler o arquivo de audio."));
        }

        HttpHeaders fileHeaders = new HttpHeaders();
        MediaType audioType = MediaType.APPLICATION_OCTET_STREAM;
        if (audio.getContentType() != null) {
            try {
                audioType = MediaType.parseMediaType(audio.getContentType());
            } catch (IllegalArgumentException ignored) {
                log.debug("Tipo MIME invalido no upload; usando application/octet-stream.");
            }
        }
        fileHeaders.setContentType(audioType);
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, fileHeaders);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("audio", filePart);

        try {
            String json = aiClient.post()
                    .uri("/stt")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("error", "O servico de transcricao devolveu uma resposta vazia."));
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);

        } catch (RestClientResponseException e) {
            log.error("ai-service respondeu {} em POST /stt. Corpo: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(mapUpstreamStatus(e.getStatusCode().value()))
                    .body(Map.of("error", "Nao foi possivel transcrever o audio. Tente novamente."));
        } catch (ResourceAccessException e) {
            log.error("Falha de rede ao chamar ai-service em POST /stt.", e);
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(Map.of("error", "O servico de voz demorou para responder."));
        }
    }

    @PostMapping(value = "/speak", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> speak(@RequestBody(required = false) Map<String, String> request) {
        String text = request == null ? null : request.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Informe o texto que deve ser falado."));
        }
        text = text.strip();
        if (text.length() > MAX_TEXT_CHARS) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("error", "O texto da resposta ultrapassa o limite de 4000 caracteres."));
        }

        try {
            byte[] mp3 = aiClient.post()
                    .uri("/tts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .body(byte[].class);

            if (mp3 == null || mp3.length == 0) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("error", "O servico de voz devolveu um audio vazio."));
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(mp3);

        } catch (RestClientResponseException e) {
            log.error("ai-service respondeu {} em POST /tts. Corpo: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(mapUpstreamStatus(e.getStatusCode().value()))
                    .body(Map.of("error", "Nao foi possivel gerar a voz da Aisha. Tente novamente."));
        } catch (ResourceAccessException e) {
            log.error("Falha de rede ao chamar ai-service em POST /tts.", e);
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(Map.of("error", "A geracao da voz demorou para responder."));
        }
    }

    private HttpStatus mapUpstreamStatus(int status) {
        if (status == 400 || status == 413 || status == 422) {
            return HttpStatus.valueOf(status);
        }
        if (status == 504) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        return HttpStatus.BAD_GATEWAY;
    }
}
