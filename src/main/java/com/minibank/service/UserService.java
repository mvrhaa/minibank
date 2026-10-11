package com.minibank.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.minibank.domain.User;
import com.minibank.exception.UserAlreadyExistsException;
import com.minibank.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String username, String password, String fullName, String email) {
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new UserAlreadyExistsException("Username is already taken");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException("Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(password);
        User user = new User(normalizedUsername, passwordHash, fullName, normalizedEmail);
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new UserAlreadyExistsException("Username or email is already registered");
        }
    }
}
