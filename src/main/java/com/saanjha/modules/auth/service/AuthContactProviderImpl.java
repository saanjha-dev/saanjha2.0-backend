package com.saanjha.modules.auth.service;

import com.saanjha.modules.auth.entity.AuthUser;
import com.saanjha.modules.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthContactProviderImpl implements AuthContactProvider {

    private final AuthUserRepository authUserRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> getVerifiedEmail(UUID userId) {
        return authUserRepository.findById(userId)
                .filter(AuthUser::isEmailVerified)
                .map(AuthUser::getEmail);
    }
}
