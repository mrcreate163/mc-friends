package com.example.mcfriends.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.mcfriends.client.AuthClient;
import com.example.mcfriends.client.dto.UserDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class JwtAuthenticationFilter extends BasicAuthenticationFilter {

    private final AuthClient authClient;

    public JwtAuthenticationFilter(AuthenticationManager authenticationManager,
                                   AuthClient authClient) {
        super(authenticationManager);
        this.authClient = authClient;
    }

    @Override
    public void doFilterInternal(HttpServletRequest req,
                                 HttpServletResponse res,
                                 FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer")) {
            log.warn("Не передан токен в заголовке!");
            chain.doFilter(req, res);
            return;
        }

        String token = header.replace("Bearer ", "");

        boolean isValid = authClient.checkValidateToken(token);

        if (!isValid) {
            log.warn("Токен не валиден!");
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        UserDto user = extractUserFromToken(token);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user,
                null
        );

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(req, res);
    }

    private UserDto extractUserFromToken(String token) {
        DecodedJWT decode = JWT.decode(token);
        UUID userId = decode.getClaim("userId").as(UUID.class);
        String email = decode.getClaim("email").asString();
        return new UserDto(userId, email);
    }
}
