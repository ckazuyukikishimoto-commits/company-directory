package com.example.company_directory.config;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;

import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.company_directory.entity.RememberMeToken;
import com.example.company_directory.repository.RememberMeTokenRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class JpaPersistentTokenRepository implements PersistentTokenRepository {

    private final RememberMeTokenRepository tokenRepository;

    @Override
    @Transactional
    public void createNewToken(PersistentRememberMeToken token) {
        RememberMeToken entity = new RememberMeToken(
                token.getSeries(),
                token.getUsername(),
                token.getTokenValue(),
                toLocalDateTime(token.getDate()));
        tokenRepository.save(entity);
    }

    @Override
    @Transactional
    public void updateToken(String series, String tokenValue, Date lastUsed) {
        tokenRepository.findById(series).ifPresent(token -> {
            token.setTokenValue(tokenValue);
            token.setLastUsed(toLocalDateTime(lastUsed));
            tokenRepository.save(token);
        });
    }

    @Override
    @Transactional
    public PersistentRememberMeToken getTokenForSeries(String series) {
        log.info("RememberMe DB Lookup: Series={}", series);
        PersistentRememberMeToken token = tokenRepository.findBySeries(series)
                .map(t -> {
                    log.info("RememberMe DB Hit: Token={}", t.getTokenValue());
                    return new PersistentRememberMeToken(
                            t.getUsername(),
                            t.getSeries(),
                            t.getTokenValue(),
                            toDate(t.getLastUsed()));
                })
                .orElse(null);

        if (token == null) {
            log.info("RememberMe DB Miss: Series={}", series);
        }
        return token;
    }

    @Override
    @Transactional
    public void removeUserTokens(String username) {
        tokenRepository.deleteByUsername(username);
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null)
            return LocalDateTime.now();
        return new Timestamp(date.getTime()).toLocalDateTime();
    }

    private Date toDate(LocalDateTime localDateTime) {
        if (localDateTime == null)
            return new Date();
        return Timestamp.valueOf(localDateTime);
    }
}
