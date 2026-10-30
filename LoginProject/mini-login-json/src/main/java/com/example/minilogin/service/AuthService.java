package com.example.minilogin.service;

import com.example.minilogin.dto.LoginRequest;
import com.example.minilogin.dto.RegisterRequest;
import com.example.minilogin.model.User;
import com.example.minilogin.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       LoginAttemptService loginAttempts) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
    }

    /**
     * Cria a conta.
     *
     * Nao ha @Transactional aqui de proposito: o save() do Spring Data ja abre
     * a sua propria transacao, e assim a violacao de unicidade chega ate este
     * metodo com a transacao ja desfeita, podendo ser tratada. Se o metodo
     * inteiro fosse transacional, capturar a excecao aqui apenas marcaria a
     * transacao como rollback-only e o erro estouraria de novo na saida.
     */
    public User register(RegisterRequest request) {
        String email = request.getEmail().trim();

        // Checagem antecipada: existe para dar uma mensagem clara no caso comum.
        // Ela sozinha nao basta — entre este SELECT e o INSERT abaixo cabe outro
        // cadastro com o mesmo e-mail. Quem realmente garante e o indice unico.
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyUsedException("Ja existe um usuario com esse email");
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        try {
            User saved = userRepository.save(user);
            log.info("Usuario cadastrado: id={}", saved.getId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Perdeu a corrida contra outro cadastro simultaneo do mesmo e-mail.
            log.warn("Cadastro rejeitado pelo indice unico de email.");
            throw new EmailAlreadyUsedException("Ja existe um usuario com esse email");
        }
    }

    /**
     * Autentica.
     *
     * Sem @Transactional: o freio de tentativas grava, e uma transacao apenas
     * de leitura aqui impediria essa escrita. Cada passo abre a sua.
     */
    public User login(LoginRequest request) {
        String email = request.getEmail().trim();

        // Antes de qualquer coisa: dentro da janela de bloqueio nem se confere
        // a senha. Assim o BCrypt nao e gasto e nenhum sinal sobre a conta vaza.
        loginAttempts.ensureNotLocked(email);

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttempts.registerFailure(email);
            // Mesma mensagem para e-mail inexistente e senha errada.
            throw new InvalidCredentialsException("Email ou senha invalidos");
        }

        loginAttempts.registerSuccess(email);
        return user;
    }

    /**
     * Troca o nome exibido.
     *
     * O e-mail nao muda por aqui de proposito: ele e a identidade de login e a
     * chave do indice unico, entao trocar exige um fluxo proprio (conferir
     * senha, garantir que o novo endereco esta livre) que ainda nao existe.
     */
    @Transactional
    public User updateName(Long userId, String novoNome) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Usuario da sessao nao existe mais: " + userId));
        user.setName(novoNome.strip());
        return userRepository.save(user);
    }

    /**
     * Troca a senha.
     *
     * Exige a senha atual mesmo com a pessoa ja logada: sem isso, quem
     * sentasse por um minuto no computador de alguem com a sessao aberta
     * poderia mudar a senha e tomar a conta.
     */
    @Transactional
    public void changePassword(Long userId, String senhaAtual, String novaSenha) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Usuario da sessao nao existe mais: " + userId));

        if (!passwordEncoder.matches(senhaAtual, user.getPasswordHash())) {
            throw new InvalidCredentialsException("A senha atual esta incorreta");
        }

        user.setPasswordHash(passwordEncoder.encode(novaSenha));
        userRepository.save(user);
        log.info("Senha alterada para o usuario id={}", userId);
    }
}
