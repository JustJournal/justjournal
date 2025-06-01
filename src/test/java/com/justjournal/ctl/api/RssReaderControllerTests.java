package com.justjournal.ctl.api;

import com.justjournal.Login;
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.UnauthorizedException;
import com.justjournal.model.RssSubscription;
import com.justjournal.model.User;
import com.justjournal.repository.RssSubscriptionsRepository;
import com.justjournal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RssReaderControllerTests {

    @Mock
    private RssSubscriptionsRepository rssSubscriptionsRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Login login;

    @InjectMocks
    private RssReaderController rssReaderController;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
    }

    @Test
    void testGetById() {
        RssSubscription subscription = new RssSubscription();
        subscription.setSubscriptionId(1);
        when(rssSubscriptionsRepository.findById(1)).thenReturn(Optional.of(subscription));

        ResponseEntity<?> response = rssReaderController.getById(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(subscription, response.getBody());
    }

    @Test
    void testGetByIdNotFound() {
        when(rssSubscriptionsRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> rssReaderController.getById(999));
    }

    @Test
    void testGetByUser() {
        User user = new User();
        user.setUsername("testuser");
        List<RssSubscription> subscriptions = Arrays.asList(new RssSubscription(), new RssSubscription());
        when(userRepository.findByUsername("testuser")).thenReturn(user);
        when(rssSubscriptionsRepository.findByUser(user)).thenReturn(subscriptions);

        ResponseEntity<?> response = rssReaderController.getByUser("testuser");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(subscriptions, response.getBody());
    }

    @Test
    void testGetByUserNotFound() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(null);

        assertThrows(NotFoundException.class, () -> rssReaderController.getByUser("nonexistent"));
    }

    @Test
    void testCreate() {
        session.setAttribute(LOGIN_ATTRNAME, "testuser");
        session.setAttribute(LOGIN_ATTRID, 1);
        User user = new User();
        user.setUsername("testuser");
        RssSubscription subscription = new RssSubscription();
        subscription.setSubscriptionId(1);
        subscription.setUri("https://example.com/rss");

        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(rssSubscriptionsRepository.save(any(RssSubscription.class))).thenReturn(subscription);

        ResponseEntity<?> response = rssReaderController.create("https://example.com/rss", session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("id", "1"), response.getBody());
    }

    @Test
    void testCreateUnauthorized() {
        MockHttpSession emptySession = new MockHttpSession();
        assertThrows(UnauthorizedException.class, () -> rssReaderController.create("https://example.com/rss", emptySession));
    }

    @Test
    void testDelete() {
        session.setAttribute(LOGIN_ATTRNAME, "testuser");
        session.setAttribute(LOGIN_ATTRID, 1);
        User user = new User();
        user.setId(1);
        user.setUsername("testuser");
        RssSubscription subscription = new RssSubscription();
        subscription.setSubscriptionId(1);
        subscription.setUser(user);

        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(rssSubscriptionsRepository.findById(1)).thenReturn(Optional.of(subscription));

        session.setAttribute("userId", 1);
        ResponseEntity<Map<String, String>> response = rssReaderController.delete(1, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("1", response.getBody().get("id"));
        verify(rssSubscriptionsRepository).delete(subscription);
    }

    @Test
    void testDeleteNotFound() {
        session.setAttribute(LOGIN_ATTRNAME, "testuser");
        session.setAttribute(LOGIN_ATTRID, 1);
        User user = new User();
        user.setId(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(rssSubscriptionsRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> rssReaderController.delete(999, session));
    }
}