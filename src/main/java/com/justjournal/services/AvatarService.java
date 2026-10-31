package com.justjournal.services;

import com.justjournal.model.ModerationStatus;
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
     * Validates the user has an avatar, wants it shown, and the content filter approved it.
     * @param user user to check
     * @return true if avatar, false otherwise
     */
    public boolean isAvatarAvailable(final User user) {
        var userpref = user.getUserPref().getShowAvatar().equals(PrefBool.Y);
        var userpic = userPicRepository.findById(user.getId())
                .map(pic -> pic.getModerationStatus() == ModerationStatus.APPROVED)
                .orElse(false);

        return userpref && userpic;
    }
}
