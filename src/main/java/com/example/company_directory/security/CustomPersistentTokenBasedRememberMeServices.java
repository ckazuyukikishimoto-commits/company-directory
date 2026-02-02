package com.example.company_directory.security;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomPersistentTokenBasedRememberMeServices extends PersistentTokenBasedRememberMeServices {

    private final PersistentTokenRepository tokenRepository;
    private final UserDetailsService myUserDetailsService;
    private final SecureRandom random;

    public CustomPersistentTokenBasedRememberMeServices(String key, UserDetailsService userDetailsService,
            PersistentTokenRepository tokenRepository) {
        super(key, userDetailsService, tokenRepository);
        this.tokenRepository = tokenRepository;
        this.myUserDetailsService = userDetailsService;
        this.random = new SecureRandom();
    }

    @Override
    protected UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request,
            HttpServletResponse response) {

        if (cookieTokens.length != 2) {
            throw new RememberMeAuthenticationException("Cookie token did not contain 2" +
                    " tokens, but contained '" + Arrays.asList(cookieTokens) + "'");
        }

        final String presentedSeries = cookieTokens[0];
        final String presentedToken = cookieTokens[1];

        PersistentRememberMeToken token = tokenRepository.getTokenForSeries(presentedSeries);

        if (token == null) {
            // No series match, so we can't authenticate using this cookie
            throw new RememberMeAuthenticationException(
                    "No persistent token found for series id: " + presentedSeries);
        }

        // We have a match for this user/series combination
        if (!presentedToken.equals(token.getTokenValue())) {
            // Token mismatch!
            // Check if the stored token was used very recently (e.g. within 10 seconds)
            // If so, it's likely a concurrent request race condition (e.g. browser waking
            // up)
            long lastUsed = token.getDate().getTime();
            long now = System.currentTimeMillis();
            long gracePeriod = 10000; // 10 seconds

            if (now - lastUsed < gracePeriod) {
                log.warn("Concurrent remember-me usage detected for series {}. " +
                        "Stored token was used {} ms ago. " +
                        "Assuming race condition and ignoring this request instead of deleting tokens.",
                        presentedSeries, (now - lastUsed));

                throw new RememberMeAuthenticationException("Concurrent remember-me usage detected");
            }

            // Standard Cookie Theft behavior
            tokenRepository.removeUserTokens(token.getUsername());

            throw new CookieTheftException(
                    "Invalid remember-me token (Series/token) mismatch. Implies previous cookie theft attack.");
        }

        if (token.getDate().getTime() + getTokenValiditySeconds() * 1000L < System.currentTimeMillis()) {
            throw new RememberMeAuthenticationException("Remember-me login has expired");
        }

        // Token also matches, so login is valid. Update the token value, keeping the
        // *same* series number.
        if (log.isDebugEnabled()) {
            log.debug("Refreshing persistent login token for user '"
                    + token.getUsername() + "', series '" + token.getSeries() + "'");
        }

        PersistentRememberMeToken newToken = new PersistentRememberMeToken(
                token.getUsername(), token.getSeries(), generateTokenValue(), new Date());

        try {
            tokenRepository.updateToken(newToken.getSeries(), newToken.getTokenValue(),
                    newToken.getDate());
            setCookie(new String[] { newToken.getSeries(), newToken.getTokenValue() },
                    getTokenValiditySeconds(), request, response);
        } catch (Exception e) {
            log.error("Failed to update token: ", e);
            throw new RememberMeAuthenticationException("Autologin failed due to data access problem");
        }

        return myUserDetailsService.loadUserByUsername(token.getUsername());
    }

    private String generateTokenValue() {
        byte[] newToken = new byte[16];
        random.nextBytes(newToken);
        return new String(Base64.getEncoder().encode(newToken));
    }
}
