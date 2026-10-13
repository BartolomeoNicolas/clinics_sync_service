package com.bartolomeo.clinics.sync.infrastructure.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Límites HTTP del servicio.
 *
 * <p>El servicio es un <strong>resource server</strong>: valida JWT emitidos por el servicio de
 * turnos con RS256, y no emite ninguno. Carga solo la clave pública, desde una ruta fuera del
 * repo.
 *
 * <p>Únicos recursos públicos: {@code /actuator/health} y la documentación. Todo lo demás
 * exige token, incluso todavía no haya controllers: la regla se declara antes de que exista
 * el endpoint, y no al revés.
 *
 * <p>Los errores de seguridad salen en {@code problem+json} como los demás, con el mismo
 * {@code code} estable. Spring, por defecto, devuelve un cuerpo vacío en el 401 y manda al
 * navegador a un formulario de login: acá se reemplaza.
 */
@Configuration
public class SecurityConfig {

    /** Lo único público. Todo lo demás, autenticado. */
    public static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ProblemAuthenticationEntryPoint authenticationEntryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {

        return http
                // Sin sesión: el estado viaja en el Bearer token, no en cookies.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // No hay cookies ni formularios, así que el CSRF no aplica.
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())

                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtScopeConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                // Sin formulario de login: los clientes son máquinas, no navegadores.
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)

                // Devolvemos problem+json en vez de redirigir al formulario.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .build();
    }

    /**
     * Decodifica y valida el JWT.
     *
     * <p>Valida por tres cosas y las tres hacen falta: la firma (que la haya hecho la clave de
     * confianza), la vigencia, y la audiencia. Con el issuer, si viene configurado.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) throws IOException {

        RSAPublicKey publicKey = loadPublicKey(Path.of(properties.getPublicKeyPath()));

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new AudienceValidator(properties.getAudience()));
        validators.add(new AudienceValidator.IssuerValidator(properties.getIssuer()));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                validators.toArray(new OAuth2TokenValidator[0])));

        return decoder;
    }

    private RSAPublicKey loadPublicKey(Path path) throws IOException {

        if (!Files.isReadable(path)) {
            // Se nombra la ruta: un path mal escrito es el error más probable, y el más difícil
            // de ver si el mensaje es genérico.
            throw new IllegalStateException(
                    "No se pudo leer la clave pública de JWT en: " + path.toAbsolutePath()
                            + ". Definí CLINICS_JWT_PUBLIC_KEY_PATH apuntando al PEM de la clave pública."
                            + " La privada nunca debe estar en esta máquina.");
        }

        String pem = Files.readString(path)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.getDecoder().decode(pem));

        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "La clave pública de JWT no es una clave RSA válida: " + path, ex);
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(JwtProperties properties) {

        CorsConfiguration configuration = new CorsConfiguration();

        // Lista explícita. Nunca "*": sería permitirle a cualquier origen leer el catálogo.
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Location", "X-Total-Count", "Link"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}