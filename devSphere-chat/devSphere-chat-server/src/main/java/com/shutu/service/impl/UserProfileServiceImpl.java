package com.shutu.service.impl;


import com.shutu.mapper.UserProfileMapper;
import com.shutu.model.entity.UserProfile;
import com.shutu.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private final UserProfileMapper userProfileMapper;

    @Override
    public UserProfile getByUserId(Long userId) {
        return userProfileMapper.selectById(userId);
    }

    @Override
    public void updateProfile(UserProfile profile) {
        if (userProfileMapper.selectById(profile.getUserId()) == null) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }
    }
}
