
/*
 * Conversa com a Aisha: envio da mensagem, exibicao na tela e as acoes que a
 * IA pede na interface (hoje, mudar o tamanho das letras).
 */
(function () {
    const API_URL = "/api/chat";
    const messagesEl = document.getElementById("messages");
    const input = document.getElementById("message-input");
    const sendBtn = document.getElementById("send-btn");
    const statusEl = document.getElementById("chat-status");
    const welcomeEl = document.getElementById("chat-welcome");
    const avatarUsuario =
        document.body.getAttribute("data-user-initials") || "U";

    // Token da sessao, publicado pelo servidor na <meta> do layout.
    const CSRF_TOKEN =
        (document.querySelector('meta[name="csrf-token"]') || {}).content || "";

    if (!messagesEl || !input || !sendBtn || !statusEl) return;

    function scrollToBottom() {
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function setStatus(text, isError) {
        statusEl.textContent = text || "";
        statusEl.classList.toggle("chat-error", Boolean(isError));
    }

    function addMessage(role, text) {
        if (welcomeEl) welcomeEl.style.display = "none";

        const primeira = document.getElementById("first-message");
        if (primeira) primeira.remove();

        const wrapper = document.createElement("div");
        wrapper.className = "message " + role;

        const avatar = document.createElement("div");
        avatar.className = "msg-avatar";
        avatar.textContent = role === "bot" ? "✦" : avatarUsuario;

        const bubble = document.createElement("div");
        bubble.className = "msg-bubble";
        bubble.textContent = text;

        wrapper.appendChild(avatar);
        wrapper.appendChild(bubble);
        messagesEl.appendChild(wrapper);

        scrollToBottom();
    }

    /*
     * Acoes decididas pela IA.
     */
    function aplicarAcao(acao) {
        if (!acao || acao.tipo !== "tamanho_texto") return;

        const controle = window.aishaTamanhoDoTexto;
        if (!controle) return;

        const passos = Math.min(
            3,
            Math.max(1, parseInt(acao.passos, 10) || 1)
        );

        if (acao.direcao === "aumentar") {
            controle.aumentar(passos);
        } else if (acao.direcao === "diminuir") {
            controle.diminuir(passos);
        } else if (acao.direcao === "normal") {
            controle.normal();
        }
    }

    // Ajusta a altura do campo conforme a pessoa escreve.
    function ajustarAltura() {
        input.style.height = "auto";
        input.style.height = Math.min(input.scrollHeight, 8 * 16) + "px";
    }

    async function sendMessage() {
        const content = input.value.trim();

        if (!content) return;

        addMessage("user", content);

        input.value = "";
        ajustarAltura();

        setStatus("Aisha está escrevendo...");
        sendBtn.disabled = true;
        input.disabled = true;

        try {
            const response = await fetch(API_URL, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-CSRF-Token": CSRF_TOKEN
                },
                body: JSON.stringify({ message: content })
            });

            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                throw new Error(
                    data.response ||
                    data.error ||
                    "Erro HTTP " + response.status
                );
            }

            // Recebe e exibe a resposta da IA.
            const resposta = data.response || "Sem resposta da IA.";

            addMessage("bot", resposta);

            if (typeof window.aishaFalar === "function") {
                // A funcao de voz chama o backend em paralelo; o chat nao espera
                // a geracao do MP3 para liberar o campo de mensagem.
                window.aishaFalar(resposta);
            }


            // Aplica as acoes solicitadas pela IA.
            aplicarAcao(data.action);

            setStatus("", false);

        } catch (error) {
            addMessage(
                "bot",
                "Não consegui responder agora. Tente novamente em instantes."
            );

            setStatus(
                "Erro: " +
                (error instanceof Error
                    ? error.message
                    : "Falha ao chamar a IA."),
                true
            );

        } finally {
            sendBtn.disabled = false;
            input.disabled = false;
            input.focus();
        }
    }

    sendBtn.addEventListener("click", sendMessage);

    input.addEventListener("input", ajustarAltura);

    input.addEventListener("keydown", function (e) {
        // Enter envia; Shift+Enter pula linha.
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    scrollToBottom();

})();