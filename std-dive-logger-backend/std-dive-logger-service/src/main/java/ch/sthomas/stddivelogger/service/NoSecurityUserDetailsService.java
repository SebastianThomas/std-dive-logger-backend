package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.user.User;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Primary
@Profile("no-security")
public class NoSecurityUserDetailsService implements UserDetailsService {
    private static final String validEmail = "test@test.ch";
    private static final String validPassword = "abc123";

    private final UserRepository userRepository;

    public NoSecurityUserDetailsService(
            final UserRepository userRepository, final PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;

        userRepository.deleteAll();
        if (userRepository.findByEmailIgnoreCase(validEmail).isEmpty()) {
            userRepository.save(
                    new UserEntity(validEmail, passwordEncoder.encode(validPassword), "Test User"));
        }
    }

    @Override
    public User loadUserByUsername(final String email) throws UsernameNotFoundException {
        return userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException(email + " not found."))
                .toRecord();
    }
}
