package com.justjournal.core;

import com.justjournal.Login;
import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserContextService {

    private UserRepository userRepository;

    public UserContextService(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    @Transactional(propagation = Propagation.REQUIRED)
    public UserContext getUserContext(final String username, final HttpSession session) {
        if (!Login.isUserName(username)) {
            return null;
        }

        User authUser = null;
        try {
            authUser = userRepository.findByUsername(Login.currentLoginName(session));
        } catch (final Exception e) {
            log.trace(e.getMessage(), e);
        }

        try {
            final User user = userRepository.findByUsername(username);
            if (user == null || user.getId() == 0) return null;

            return new UserContext(user, authUser);
        } catch (final Exception e) {
            log.error("Unable to get user context", e);
        }
        return null;
    }
}
