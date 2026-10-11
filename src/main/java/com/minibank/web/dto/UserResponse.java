package com.minibank.web.dto;

import com.minibank.domain.User;

public record UserResponse(String username, String fullName, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getUsername(), user.getFullName(), user.getEmail());
    }
}
