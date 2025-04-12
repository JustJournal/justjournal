package com.justjournal.services;

import com.justjournal.model.PrefBool;
import com.justjournal.model.User;
import com.justjournal.repository.UserPicRepository;
import org.springframework.stereotype.Service;

@Service
public class AvatarService {
    private final UserPicRepository userPicRepository;

    public AvatarService(UserPicRepository userPicRepository) {
        this.userPicRepository = userPicRepository;
    }

    /**
     * Do we have an avatar?
     * Validates the user has an avatar and wants it shown.
     * @param user user to check
     * @return true if avatar, false otherwise
     */
    public boolean isAvatarAvailable(final User user) {
        var userpref = user.getUserPref().getShowAvatar().equals(PrefBool.Y);
        var userpic = userPicRepository.existsById(user.getId());

        return userpref && userpic;
    }
}
