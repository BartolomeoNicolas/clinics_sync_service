package com.bartolomeo.clinics.sync.infrastructure.security;

import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Convierte los permisos del JWT en autoridades de Spring.
 *
 * <p>El token trae los permisos en el claim {@code scope}, como lista de cadenas. Spring los
 * prefija con {@code SCOPE_} y a partir de ahí se matchean contra
 * {@code hasAuthority("SCOPE_...")} en las reglas de acceso.
 */
public class JwtScopeConverter implements Converter<Jwt, JwtAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter delegate =
            new JwtGrantedAuthoritiesConverter();

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {

        Collection<GrantedAuthority> authorities = delegate.convert(jwt);

        return new JwtAuthenticationToken(jwt, authorities == null ? List.of() : authorities,
                jwt.getSubject());
    }
}