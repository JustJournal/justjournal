package com.justjournal.ctl.api;

import com.justjournal.exception.ForbiddenException;
import com.justjournal.model.Friend;
import com.justjournal.model.User;
import com.justjournal.model.UserContact;
import com.justjournal.repository.UserContactRepository;
import com.justjournal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.util.Collections;
import java.util.HashSet;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContactControllerTests {

    @Mock
    private UserContactRepository contactDao;

    @Mock
    private UserRepository userDao;

    @InjectMocks
    private ContactController contactController;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
    }

    @Test
    void testGetOwnContactInfo() {
        int userId = 123;
        String username = "laffer1";
        session.setAttribute(LOGIN_ATTRNAME, username);
        session.setAttribute(LOGIN_ATTRID, userId);

        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        user.setFriends(new HashSet<>());

        UserContact contact = new UserContact();
        contact.setUser(user);

        when(userDao.findByUsername(username)).thenReturn(user);
        when(contactDao.findByUser(user)).thenReturn(contact);

        UserContact result = contactController.get(username, session);

        assertNotNull(result);
        assertEquals(contact, result);
    }

    @Test
    void testGetFriendContactInfo() {
        int myUserId = 456;
        String friendUsername = "laffer1";
        session.setAttribute(LOGIN_ATTRNAME, "myuser");
        session.setAttribute(LOGIN_ATTRID, myUserId);

        User friendUser = new User();
        friendUser.setId(123);
        friendUser.setUsername(friendUsername);
        
        User myUser = new User();
        myUser.setId(myUserId);
        
        Friend f = new Friend();
        f.setUser(friendUser);
        f.setFriend(myUser);
        friendUser.setFriends(new HashSet<>(Collections.singletonList(f)));

        UserContact contact = new UserContact();
        contact.setUser(friendUser);

        when(userDao.findByUsername(friendUsername)).thenReturn(friendUser);
        when(contactDao.findByUser(friendUser)).thenReturn(contact);

        UserContact result = contactController.get(friendUsername, session);

        assertNotNull(result);
        assertEquals(contact, result);
    }

    @Test
    void testGetNonFriendContactInfoForbidden() {
        int myUserId = 456;
        String otherUsername = "laffer1";
        session.setAttribute(LOGIN_ATTRNAME, "myuser");
        session.setAttribute(LOGIN_ATTRID, myUserId);

        User otherUser = new User();
        otherUser.setId(123);
        otherUser.setUsername(otherUsername);
        otherUser.setFriends(new HashSet<>());

        when(userDao.findByUsername(otherUsername)).thenReturn(otherUser);

        assertThrows(ForbiddenException.class, () -> contactController.get(otherUsername, session));
    }

    @Test
    void testGetContactInfoNotLoggedIn() {
        String username = "laffer1";
        // session is empty

        assertThrows(ForbiddenException.class, () -> contactController.get(username, session));
    }
}
