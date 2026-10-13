package com.bartolomeo.clinics.sync.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Genera el material criptográfico de los tests.
 *
 * <p>Las claves se generan en memoria, en cada corrida: ningún archivo PEM en el repo y
 * ninguna dependencia de lo que haya en la máquina.
 *
 * <p>Se firman tokens reales con RS256, no se falsea la validación: si el test pasa, es
 * porque la cadena de validación funciona de verdad, no porque se la esquivó.
 */
public final class TestKeys {

    /** Audiencia que el servicio espera; coincide con la de los tests. */
    public static final String AUDIENCE = "clinics-sync";

    /** Emisor esperado en los tests. */
    public static final String ISSUER = "clinics-booking";

    private static final KeyPair TRUSTED = generateKeyPair();
    private static final KeyPair FOREIGN = generateKeyPair();

    private TestKeys() {
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el par de claves del test", ex);
        }
    }

    /** Clave con la que el servicio confía. */
    public static RSAPublicKey trustedPublicKey() {
        return (RSAPublicKey) TRUSTED.getPublic();
    }

    /** Escribe la clave pública confiable a disco y devuelve la ruta, para inyectarla. */
    public static String writePublicKeyToTempFile() {
        try {
            Path file = Files.createTempFile("jwt-public-", ".pem");
            String pem = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, new byte[]{'\n'})
                    .encodeToString(trustedPublicKey().getEncoded())
                    + "\n-----END PUBLIC KEY-----\n";
            Files.writeString(file, pem);
            file.toFile().deleteOnExit();
            return file.toAbsolutePath().toString();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo escribir la clave pública del test", ex);
        }
    }

    /** Token válido: firma de confianza, audiencia correcta y sin expirar. */
    public static String validToken() {
        return token(TRUSTED, AUDIENCE, Instant.now().plusSeconds(600), null);
    }

    /** Token vencido hace un minuto. */
    public static String expiredToken() {
        // OJO: issuedAt tiene que ser ANTERIOR a expiresAt. Un token con las fechas al revés
        // no "está vencido": es estructuralmente inválido, y Nimbus lo rechaza antes de que
        // llegue a mirarse la vigencia. El síntoma es el mismo 401 pero por otro motivo, y el
        // mensaje dice "expiresAt must be after issuedAt".
        return token(TRUSTED, AUDIENCE, Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(3600));
    }

    /** Token bien formado pero firmado con una clave que el servicio no conoce. */
    public static String foreignSignatureToken() {
        return token(FOREIGN, AUDIENCE, Instant.now().plusSeconds(600), null);
    }

    /** Token de confianza, con la audiencia que declara otro servicio. */
    public static String wrongAudienceToken() {
        return token(TRUSTED, "otro-servicio", Instant.now().plusSeconds(600), null);
    }

    private static String token(KeyPair keyPair, String audience, Instant expiresAt,
                                Instant issuedAt) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject("usuario-1")
                    .issuer(ISSUER)
                    .audience(List.of(audience))
                    .issueTime(Date.from(issuedAt == null
                            ? Instant.now().minusSeconds(5) : issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .claim("scope", List.of("catalog:read"));

            JWTClaimsSet claims = builder.build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));

            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("No se pudo firmar el token del test", ex);
        }
    }
}