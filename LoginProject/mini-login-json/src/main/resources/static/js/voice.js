/*
 * voice.js — voz de qualidade pelo backend da Aisha.
 * Microfone: MediaRecorder -> Spring -> Flask -> OpenRouter STT.
 * Resposta: texto da Aisha -> Spring -> Flask -> OpenRouter TTS -> MP3.
 */
(function () {
    "use strict";

    const input = document.getElementById("message-input");
    const sendBtn = document.getElementById("send-btn");
    const btnMic = document.getElementById("btn-mic");
    const chkVoz = document.getElementById("chk-voz");
    const opcoes = document.getElementById("voice-options");
    const status = document.getElementById("chat-status");
    const CSRF_TOKEN =
        (document.querySelector('meta[name="csrf-token"]') || {}).content || "";

    if (!input || !sendBtn) return;

    const STORAGE_KEY = "aisha:ler-respostas";
    const MAX_AUDIO_BYTES = 10 * 1024 * 1024;

    let recorder = null;
    let mediaStream = null;
    let audioChunks = [];
    let transcribing = false;
    let currentAudio = null;
    let currentAudioUrl = null;
    let speechAbortController = null;
    let fallbackPlayer = null;
    let lastVoiceStatus = null;

    function setStatus(message) {
        if (!status) return;
        status.textContent = message || "";
        lastVoiceStatus = message || null;
    }

    function clearStatus() {
        if (status && lastVoiceStatus && status.textContent === lastVoiceStatus) {
            status.textContent = "";
        }
        lastVoiceStatus = null;
    }

    function getErrorMessage(data, fallback) {
        if (data && typeof data.error === "string") return data.error;
        if (data && data.error && typeof data.error.message === "string") {
            return data.error.message;
        }
        if (data && typeof data.response === "string") return data.response;
        return fallback;
    }

    function limparParaFala(texto) {
        return String(texto || "")
            .replace(/```[\s\S]*?```/g, " ")
            .replace(/`([^`]+)`/g, "$1")
            .replace(/\[([^\]]+)\]\([^)]+\)/g, "$1")
            .replace(/[*_#>~]/g, "")
            .replace(/\p{Extended_Pictographic}/gu, "")
            .replace(/\s+/g, " ")
            .trim();
    }

    function stopAudio() {
        if (speechAbortController) {
            speechAbortController.abort();
            speechAbortController = null;
        }
        if (currentAudio) {
            currentAudio.pause();
            currentAudio.removeAttribute("src");
            currentAudio.load();
            currentAudio = null;
        }
        if (currentAudioUrl) {
            URL.revokeObjectURL(currentAudioUrl);
            currentAudioUrl = null;
        }
        if (fallbackPlayer) {
            fallbackPlayer.remove();
            fallbackPlayer = null;
        }
    }

    async function falar(texto) {
        if (!chkVoz || !chkVoz.checked) return;

        const limpo = limparParaFala(texto);
        if (!limpo) return;

        stopAudio();
        speechAbortController = new AbortController();
        const controller = speechAbortController;

        try {
            const response = await fetch("/api/voice/speak", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-CSRF-Token": CSRF_TOKEN
                },
                credentials: "same-origin",
                signal: controller.signal,
                body: JSON.stringify({ text: limpo })
            });

            if (!response.ok) {
                const data = await response.json().catch(() => ({}));
                throw new Error(getErrorMessage(data, "Não consegui gerar o áudio da Aisha."));
            }

            const blob = await response.blob();
            if (!blob.size) throw new Error("O serviço devolveu um áudio vazio.");
            if (controller.signal.aborted) return;

            currentAudioUrl = URL.createObjectURL(blob);
            currentAudio = new Audio(currentAudioUrl);
            currentAudio.preload = "auto";

            currentAudio.addEventListener("ended", function () {
                if (currentAudioUrl) URL.revokeObjectURL(currentAudioUrl);
                currentAudioUrl = null;
                currentAudio = null;
            }, { once: true });

            try {
                await currentAudio.play();
                clearStatus();
            } catch (playError) {
                // Alguns celulares exigem um toque diretamente no controle de audio.
                fallbackPlayer = document.createElement("audio");
                fallbackPlayer.controls = true;
                fallbackPlayer.src = currentAudioUrl;
                fallbackPlayer.style.display = "block";
                fallbackPlayer.style.width = "min(100%, 420px)";
                if (opcoes) opcoes.appendChild(fallbackPlayer);
                setStatus("O áudio está pronto. Toque no controle abaixo para ouvir.");
                currentAudio = null;
            }
        } catch (error) {
            if (error.name === "AbortError") return;
            console.error("[AISHA] Erro ao gerar/reproduzir voz:", error);
            setStatus(error.message || "Não consegui reproduzir a voz da Aisha.");
        } finally {
            if (speechAbortController === controller) speechAbortController = null;
        }
    }

    // Mantém o contrato já utilizado pelo chat.js.
    window.aishaFalar = falar;

    if (opcoes) opcoes.hidden = false;

    if (chkVoz) {
        try {
            chkVoz.checked = localStorage.getItem(STORAGE_KEY) === "1";
        } catch (e) {
            // Se o armazenamento estiver bloqueado, usa o estado inicial do HTML.
        }

        chkVoz.addEventListener("change", function () {
            try {
                localStorage.setItem(STORAGE_KEY, chkVoz.checked ? "1" : "0");
            } catch (e) {
                // Preferência fica apenas nesta sessão.
            }
            if (!chkVoz.checked) stopAudio();
        });
    }

    function escolherFormatoGravacao() {
        if (!window.MediaRecorder || typeof MediaRecorder.isTypeSupported !== "function") {
            return "";
        }
        const formatos = [
            "audio/webm;codecs=opus",
            "audio/webm",
            "audio/mp4"
        ];
        return formatos.find(function (tipo) {
            return MediaRecorder.isTypeSupported(tipo);
        }) || "";
    }

    function extensaoDoAudio(mimeType) {
        if (mimeType.includes("mp4")) return "mp4";
        if (mimeType.includes("ogg")) return "ogg";
        return "webm";
    }

    async function enviarAudio(blob, filename) {
        if (!blob || !blob.size) {
            setStatus("Não consegui capturar sua voz. Tente novamente.");
            return;
        }
        if (blob.size > MAX_AUDIO_BYTES) {
            setStatus("A gravação ficou maior que 10 MB. Grave uma mensagem mais curta.");
            return;
        }

        const formData = new FormData();
        formData.append("audio", blob, filename);

        transcribing = true;
        if (btnMic) btnMic.disabled = true;
        setStatus("Transcrevendo sua fala…");

        try {
            const response = await fetch("/api/voice/transcribe", {
                method: "POST",
                headers: { "X-CSRF-Token": CSRF_TOKEN },
                credentials: "same-origin",
                body: formData
            });

            const data = await response.json().catch(function () { return {}; });
            if (!response.ok) {
                throw new Error(getErrorMessage(data, "Não consegui transcrever sua fala."));
            }

            const transcript = (data.text || "").trim();
            if (!transcript) throw new Error("Não consegui identificar fala nesse áudio.");

            input.value = transcript;
            input.dispatchEvent(new Event("input", { bubbles: true }));
            clearStatus();
            sendBtn.click();
        } catch (error) {
            console.error("[AISHA] Erro na transcrição:", error);
            setStatus(error.message || "Falha ao transcrever o áudio.");
        } finally {
            transcribing = false;
            if (btnMic) btnMic.disabled = false;
        }
    }

    async function iniciarGravacao() {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia || !window.MediaRecorder) {
            setStatus("Este navegador não oferece gravação de áudio. Use HTTPS ou um navegador compatível.");
            return;
        }

        try {
            stopAudio();
            mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
            const mimeType = escolherFormatoGravacao();
            recorder = mimeType
                ? new MediaRecorder(mediaStream, { mimeType: mimeType })
                : new MediaRecorder(mediaStream);
            audioChunks = [];

            recorder.addEventListener("dataavailable", function (event) {
                if (event.data && event.data.size > 0) audioChunks.push(event.data);
            });

            recorder.addEventListener("stop", function () {
                const tipo = recorder && recorder.mimeType ? recorder.mimeType : "audio/webm";
                const blob = new Blob(audioChunks, { type: tipo });
                const filename = "gravacao." + extensaoDoAudio(tipo);
                audioChunks = [];
                if (mediaStream) {
                    mediaStream.getTracks().forEach(function (track) { track.stop(); });
                    mediaStream = null;
                }
                btnMic.classList.remove("listening");
                btnMic.setAttribute("aria-pressed", "false");
                btnMic.setAttribute("aria-label", "Falar com a Aisha");
                if (blob.size) enviarAudio(blob, filename);
            }, { once: true });

            recorder.addEventListener("error", function (event) {
                console.error("[AISHA] Erro no MediaRecorder:", event.error || event);
                setStatus("Ocorreu um erro durante a gravação.");
                encerrarGravacao();
            }, { once: true });

            recorder.start();
            btnMic.classList.add("listening");
            btnMic.setAttribute("aria-pressed", "true");
            btnMic.setAttribute("aria-label", "Parar gravação");
            setStatus("Gravando… toque novamente no microfone para enviar.");
        } catch (error) {
            if (mediaStream) {
                mediaStream.getTracks().forEach(function (track) { track.stop(); });
                mediaStream = null;
            }
            console.error("[AISHA] Não foi possível iniciar o microfone:", error);
            if (error.name === "NotAllowedError" || error.name === "SecurityError") {
                setStatus("Permita o acesso ao microfone nas configurações do navegador.");
            } else if (error.name === "NotFoundError") {
                setStatus("Não encontrei um microfone neste dispositivo.");
            } else {
                setStatus("Não consegui iniciar a gravação. Verifique o microfone e tente novamente.");
            }
        }
    }

    function encerrarGravacao() {
        if (recorder && recorder.state === "recording") {
            recorder.stop();
        }
    }

    if (btnMic && navigator.mediaDevices && navigator.mediaDevices.getUserMedia && window.MediaRecorder) {
        btnMic.hidden = false;
        btnMic.addEventListener("click", function () {
            if (recorder && recorder.state === "recording") {
                encerrarGravacao();
                setStatus("Enviando sua gravação…");
            } else if (!transcribing) {
                iniciarGravacao();
            }
        });
    } else if (btnMic) {
        btnMic.hidden = true;
    }

    // Ao iniciar uma nova mensagem, interrompe eventual áudio anterior.
    sendBtn.addEventListener("click", stopAudio);
    input.addEventListener("keydown", function (event) {
        if (event.key === "Enter" && !event.shiftKey) stopAudio();
    });
})();
