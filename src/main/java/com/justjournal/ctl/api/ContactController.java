package com.justjournal.ctl.api;

import static com.justjournal.core.Constants.PARAM_USERNAME;

import com.justjournal.Login;
import com.justjournal.core.Constants;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.User;
import com.justjournal.model.UserContact;
import com.justjournal.repository.UserContactRepository;
import com.justjournal.repository.UserRepository;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final UserContactRepository contactDao;
    private final UserRepository userDao;

    public ContactController(UserContactRepository contactDao, UserRepository userDao) {
        this.contactDao = contactDao;
        this.userDao = userDao;
    }

    @Cacheable(value = "contact", key = "#username")
    @GetMapping(
            value = "{username}",
            headers = Constants.HEADER_ACCEPT_ALL,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public UserContact get(@PathVariable(PARAM_USERNAME) String username, final HttpSession session) {

        if (!Login.isUserName(username)) {
            throw new NotFoundException();
        }

        // make contact information only for authenticated users
        final int userId = Login.currentLoginId(session);
        if (userId <= 0) {
            throw new ForbiddenException();
        }

        final User user = userDao.findByUsername(username);
        if (user == null || user.getFriends().stream().noneMatch(f -> f.getFriend().getId() == userId) || user.getId() != userId) {
            throw new ForbiddenException();
        }

        if (user.getJournals().stream().anyMatch(j -> j.isOwnerViewOnly() || !j.isAllowSpider())) {
            throw new NotFoundException();
        }

        return contactDao.findByUser(user);
    }

    @CacheEvict(value = "contact", allEntries = true)
    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> update(
            @RequestBody final UserContact contact,
            final HttpServletResponse response,
            final HttpSession session) {

        final int userId = Login.currentLoginId(session);

        if (userId <= 0) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return ErrorHandler.modelError("Authentication Error");
        }

        try {
            User user = userDao.findById(userId).orElseThrow(NotFoundException::new);
            UserContact existingContact = contactDao.findByUser(user);

            if (existingContact == null) {
                existingContact = new UserContact();
                existingContact.setUser(user);
            }

            // Update fields
            existingContact.setEmail(contact.getEmail());
            existingContact.setPhone(contact.getPhone());
            existingContact.setX(contact.getX());
            existingContact.setInstagram(contact.getInstagram());
            existingContact.setFacebook(contact.getFacebook());
            existingContact.setTelegram(contact.getTelegram());
            existingContact.setLinkedin(contact.getLinkedin());
            existingContact.setReddit(contact.getReddit());
            existingContact.setHpTitle(contact.getHpTitle());
            existingContact.setHpUri(contact.getHpUri());

            contactDao.save(existingContact);

            return java.util.Collections.singletonMap("status", "success");
        } catch (Exception e) {
            log.error("Error updating contact information", e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ErrorHandler.modelError("Unable to save contact information");
        }
    }
}
