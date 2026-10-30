/*
 * Comportamentos de interface presentes em todas as telas internas:
 * menu no celular, tema claro/escuro e tamanho das letras.
 *
 * O tema e o tamanho ficam no localStorage, ou seja, no aparelho — e nao na
 * conta. Quem usa o tablet na sala e o celular no quarto costuma querer
 * ajustes diferentes em cada um.
 */
(function () {

    // ── Tamanho das letras ──────────────────────────────────
    // Toda a interface e dimensionada em rem, entao mexer no font-size do
    // <html> aumenta texto, botoes e espacamentos juntos, sem quebrar layout.
    const FONTE_PADRAO = 18;
    const FONTE_MIN    = 16;
    const FONTE_MAX    = 30;
    const FONTE_PASSO  = 2;

    function fonteAtual() {
        const salva = parseFloat(localStorage.getItem('aisha-fonte'));
        if (salva >= FONTE_MIN && salva <= FONTE_MAX) return salva;
        return FONTE_PADRAO;
    }

    function aplicarFonte(px) {
        const valor = Math.min(FONTE_MAX, Math.max(FONTE_MIN, px));
        document.documentElement.style.fontSize = valor + 'px';
        try { localStorage.setItem('aisha-fonte', String(valor)); } catch (e) { /* sem armazenamento */ }
        return valor;
    }

    /*
     * Unico caminho para mudar o tamanho das letras, e ele passa pela conversa:
     * a pessoa pede a Aisha, ela chama a ferramenta e o chat aplica por aqui.
     * Nao ha botao na tela — o topo ficava com tres controles disputando
     * atencao, e pedir por escrito e o gesto natural de quem esta conversando.
     */
    window.aishaTamanhoDoTexto = {
        aumentar: function (passos) { return aplicarFonte(fonteAtual() + FONTE_PASSO * (passos || 1)); },
        diminuir: function (passos) { return aplicarFonte(fonteAtual() - FONTE_PASSO * (passos || 1)); },
        normal:   function () { return aplicarFonte(FONTE_PADRAO); },
        atual:    fonteAtual,
        maximo:   FONTE_MAX,
        minimo:   FONTE_MIN
    };

    // ── Tema claro / escuro ─────────────────────────────────
    // O tema inicial ja foi aplicado no <head>, antes da primeira pintura.
    // Aqui so ficam o botao e o icone.
    function temaAtual() {
        return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    }

    function pintarIconeDoTema() {
        const botao = document.getElementById('btn-theme');
        if (!botao) return;
        // Qual dos dois SVGs aparece e decisao do CSS, por data-theme. Aqui
        // fica so o rotulo, que o leitor de tela anuncia.
        const escuro = temaAtual() === 'dark';
        botao.title = escuro ? 'Mudar para o tema claro' : 'Mudar para o tema escuro';
        botao.setAttribute('aria-label', botao.title);
    }

    const btnTheme = document.getElementById('btn-theme');
    if (btnTheme) {
        btnTheme.addEventListener('click', function () {
            const novo = temaAtual() === 'dark' ? 'light' : 'dark';
            document.documentElement.setAttribute('data-theme', novo);
            try { localStorage.setItem('aisha-tema', novo); } catch (e) { /* sem armazenamento */ }
            pintarIconeDoTema();
        });
        pintarIconeDoTema();
    }

    // ── Menu no celular ─────────────────────────────────────
    // No computador a barra lateral abre sozinha ao passar o mouse (CSS);
    // no toque nao existe "passar o mouse", entao o botao continua.
    const hamburger = document.getElementById('hamburger');
    const sidebar   = document.getElementById('sidebar');
    const overlay   = document.getElementById('overlay');

    function abrirMenu() {
        sidebar && sidebar.classList.add('open');
        overlay && overlay.classList.add('open');
        document.body.style.overflow = 'hidden';
    }

    function fecharMenu() {
        sidebar && sidebar.classList.remove('open');
        overlay && overlay.classList.remove('open');
        document.body.style.overflow = '';
    }

    hamburger && hamburger.addEventListener('click', abrirMenu);
    overlay   && overlay.addEventListener('click', fecharMenu);

    window.addEventListener('resize', function () {
        if (window.innerWidth > 768) fecharMenu();
    });

    // ── Nova conversa ───────────────────────────────────────
    const btnNewChat = document.getElementById('btn-new-chat');
    if (btnNewChat) {
        btnNewChat.addEventListener('click', async function (e) {
            e.preventDefault();

            // Abre uma conversa nova no servidor. Antes este botao so limpava a
            // tela: o contexto continuava no servidor e a Aisha seguia
            // respondendo com base na conversa que o usuario achava ter
            // encerrado.
            try {
                const meta = document.querySelector('meta[name="csrf-token"]');
                const r = await fetch('/api/chat/nova', {
                    method: 'POST',
                    headers: { 'X-CSRF-Token': meta ? meta.content : '' }
                });
                if (!r.ok) throw new Error('HTTP ' + r.status);
            } catch (err) {
                const statusEl = document.getElementById('chat-status');
                if (statusEl) statusEl.textContent = 'Não consegui iniciar uma conversa nova. Tente de novo.';
                return;
            }

            // Recarrega em vez de limpar o DOM na mao: a pagina inteira —
            // mensagens e lista de conversas da barra lateral — e renderizada
            // pelo servidor a partir do banco. Montar aqui uma segunda versao
            // desse estado so criaria duas verdades para manter em sincronia.
            window.location.assign('/home');
        });
    }
})();
