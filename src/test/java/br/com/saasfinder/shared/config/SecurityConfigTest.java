package br.com.saasfinder.shared.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Teste unitario: a aplicacao nao pode subir sem credencial de curadoria. Nao
 * precisa de banco nem de contexto, entao roda no surefire.
 *
 * <p>Fica no mesmo pacote da classe testada porque os metodos @Bean sao
 * package-private de proposito - nao ha razao para expo-los so para o teste.
 */
class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();
    private final BCryptPasswordEncoder codificador = new BCryptPasswordEncoder();

    @Test
    @DisplayName("sem ADMIN_USER ou ADMIN_PASSWORD, a aplicacao recusa subir")
    void exigeCredencial() {
        assertThatThrownBy(() -> config.curador("", "senha", codificador))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_USER");

        assertThatThrownBy(() -> config.curador("admin", "  ", codificador))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    @Test
    @DisplayName("com as duas variaveis, o usuario e criado")
    void criaUsuario() {
        assertThatCode(() -> config.curador("admin", "senha", codificador)).doesNotThrowAnyException();
    }
}
