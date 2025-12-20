package com.english.education.security.jwt;

import com.english.education.constant.Constants;
import com.english.education.constant.MessageConstant;
import com.english.education.exception.AuthenException;
import com.english.education.model.dto.response.ValidRequestResponse;
import com.english.education.model.service.authstate.AuthStateService;
import com.english.education.model.service.common.CommonServiceImpl;
import com.english.education.security.principle.UserDetailCustomService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;
    private final UserDetailCustomService userDetailCustomService;
    private final AuthStateService authStateService;
    private final CommonServiceImpl commonServiceImpl;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (token != null && jwtProvider.validateToken(token)) {
            Claims claims = jwtProvider.parse(token);

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("userRole", List.class);

            validateTokenAndRedis(claims);

            String username = jwtProvider.getUsernameFromToken(token);
            UserDetails userDetails = User.withUsername(username)
                    .password("")
                    .authorities(roles.toArray(new String[0]))
                    .build();
            Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    public String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void validateTokenAndRedis(Claims claims){
        Integer userId = claims.get("userId", Integer.class);
        String deviceType = claims.get("device", String.class);
        Long tokenVer = claims.get("tokenVer", Long.class);
        if (userId == null || deviceType == null || tokenVer == null) {
            throw new AuthenException(MessageConstant.INVALID_ACCESS_TOKEN, "token");
        }

        String tokenVerKey = commonServiceImpl.getTokenVerDevice(deviceType, userId);
        String lockedKey = Constants.USER_LOCKED_KEY + userId;

        ValidRequestResponse vrr = ValidRequestResponse.builder()
                .deviceType(deviceType)
                .lockedKey(lockedKey)
                .tokenVerKey(tokenVerKey)
                .tokenVer(tokenVer)
                .userId(userId)
                .build();

        authStateService.validateAuthState(vrr);
    }
}
