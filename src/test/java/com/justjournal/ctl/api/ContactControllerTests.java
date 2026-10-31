package com.justjournal.ctl.api;

import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.Friend;
import com.justjournal.model.Journal;
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
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

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

    @Test
    void testOwnerSeesContactInfoWithOwnerViewOnlyJournal() {
        int userId = 123;
        String username = "laffer1";
        session.setAttribute(LOGIN_ATTRNAME, username);
        session.setAttribute(LOGIN_ATTRID, userId);

        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        user.setFriends(new HashSet<>());
        Journal journal = new Journal();
        journal.setOwnerViewOnly(true);
        user.getJournals().add(journal);

        UserContact contact = new UserContact();
        contact.setUser(user);

        when(userDao.findByUsername(username)).thenReturn(user);
        when(contactDao.findByUser(user)).thenReturn(contact);

        assertEquals(contact, contactController.get(username, session));
    }

    @Test
    void testFriendCannotSeeContactInfoWithOwnerViewOnlyJournal() {
        int myUserId = 456;
        String friendUsername = "laffer1";
        session.setAttribute(LOGIN_ATTRNAME, "myuser");
        session.setAttribute(LOGIN_ATTRID, myUserId);

        User friendUser = new User();
        friendUser.setId(123);
        friendUser.setUsername(friendUsername);
        Journal journal = new Journal();
        journal.setOwnerViewOnly(true);
        friendUser.getJournals().add(journal);

        User myUser = new User();
        myUser.setId(myUserId);

        Friend f = new Friend();
        f.setUser(friendUser);
        f.setFriend(myUser);
        friendUser.setFriends(new HashSet<>(Collections.singletonList(f)));

        when(userDao.findByUsername(friendUsername)).thenReturn(friendUser);

        assertThrows(NotFoundException.class, () -> contactController.get(friendUsername, session));
        verifyNoInteractions(contactDao);
    }

    private User loggedIn(int userId) {
        session.setAttribute(LOGIN_ATTRNAME, "laffer1");
        session.setAttribute(LOGIN_ATTRID, userId);
        User user = new User();
        user.setId(userId);
        user.setUsername("laffer1");
        return user;
    }

    private static UserContact incoming() {
        UserContact contact = new UserContact();
        contact.setEmail("ada@example.com");
        contact.setPhone("555-0100");
        contact.setX("adax");
        contact.setInstagram("adai");
        contact.setFacebook("adaf");
        contact.setTelegram("adat");
        contact.setLinkedin("adal");
        contact.setReddit("adar");
        contact.setHpTitle("Home");
        contact.setHpUri("https://ada.example.com/");
        return contact;
    }

    @Test
    void testUpdateRequiresLogin() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, String> result = contactController.update(incoming(), response, session);

        assertEquals(403, response.getStatus());
        assertEquals("Authentication Error", result.get("error"));
        verify(contactDao, never()).save(any());
    }

    @Test
    void testUpdateCreatesContactWhenNoneExists() {
        User user = loggedIn(123);
        when(userDao.findById(123)).thenReturn(Optional.of(user));
        when(contactDao.findByUser(user)).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, String> result = contactController.update(incoming(), response, session);

        assertEquals(Collections.singletonMap("status", "success"), result);
        ArgumentCaptor<UserContact> captor = ArgumentCaptor.forClass(UserContact.class);
        verify(contactDao).save(captor.capture());
        UserContact saved = captor.getValue();
        assertEquals(user, saved.getUser());
        assertEquals("ada@example.com", saved.getEmail());
        assertEquals("555-0100", saved.getPhone());
        assertEquals("adax", saved.getX());
        assertEquals("adai", saved.getInstagram());
        assertEquals("adaf", saved.getFacebook());
        assertEquals("adat", saved.getTelegram());
        assertEquals("adal", saved.getLinkedin());
        assertEquals("adar", saved.getReddit());
        assertEquals("Home", saved.getHpTitle());
        assertEquals("https://ada.example.com/", saved.getHpUri());
    }

    @Test
    void testUpdateChangesTheExistingContact() {
        User user = loggedIn(123);
        UserContact existing = new UserContact();
        existing.setUser(user);
        existing.setEmail("old@example.com");
        when(userDao.findById(123)).thenReturn(Optional.of(user));
        when(contactDao.findByUser(user)).thenReturn(existing);

        Map<String, String> result = contactController.update(incoming(), new MockHttpServletResponse(), session);

        assertEquals(Collections.singletonMap("status", "success"), result);
        verify(contactDao).save(existing);
        assertEquals("ada@example.com", existing.getEmail());
        assertEquals("Home", existing.getHpTitle());
    }

    @Test
    void testUpdateWithUnknownUserIsABadRequest() {
        loggedIn(999);
        when(userDao.findById(999)).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, String> result = contactController.update(incoming(), response, session);

        assertEquals(400, response.getStatus());
        assertEquals("Unable to save contact information", result.get("error"));
        verify(contactDao, never()).save(any());
    }
}
