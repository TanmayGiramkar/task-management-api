package com.example.taskapi.security;

import com.example.taskapi.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The logged-in user as Spring Security sees it during login. Carries the id so the access token's
 * subject can be set without a second database lookup.
 */
public record UserPrincipal(Long id, String email, String passwordHash) implements UserDetails {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
    }

    /** No roles in Stage 4: team roles (owner/admin/member) come in Stage 5. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /** Keeps the password hash out of logs. */
    @Override
    public String toString() {
        return "UserPrincipal[id=" + id + ", email=" + email + "]";
    }
}
