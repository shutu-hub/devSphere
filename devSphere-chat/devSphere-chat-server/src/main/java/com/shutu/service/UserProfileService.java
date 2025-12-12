package com.shutu.service;


import com.shutu.model.entity.UserProfile;

public interface UserProfileService {
    UserProfile getByUserId(Long userId);
    void updateProfile(UserProfile profile);
}
