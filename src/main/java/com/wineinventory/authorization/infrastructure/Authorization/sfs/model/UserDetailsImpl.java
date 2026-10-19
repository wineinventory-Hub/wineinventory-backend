package com.wineinventory.authorization.infrastructure.Authorization.sfs.model;

import com.wineinventory.authorization.domain.model.aggregates.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * This class is responsible for providing the user details to the Spring Security framework.
 * It implements the UserDetails interface.
 */
@Getter
@EqualsAndHashCode
public class UserDetailsImpl implements UserDetails {

    private final Long userId;
    private final String username;
    @JsonIgnore
    private final String password;
    private final boolean accountNonExpired;
    private final boolean accountNonLocked;
    private final boolean credentialsNonExpired;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * This constructor initializes the UserDetailsImpl object.
     * @param userId The user id, which is also the account id of the user.
     * @param username The username.
     * @param password The password.
     * @param authorities The authorities.
     */
    public UserDetailsImpl(Long userId, String username, String password, Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.authorities = authorities;
        this.accountNonExpired = true;
        this.accountNonLocked = true;
        this.credentialsNonExpired = true;
        this.enabled = true;
    }

    /**
     * This method is responsible for building the UserDetailsImpl object from the User object.
     * The user role is mapped to a Spring Security authority with the ROLE_ prefix.
     * @param user The user object.
     * @return The UserDetailsImpl object.
     */
    public static UserDetailsImpl build(User user) {
        var authorities = user.getRole() == null
                ? List.<GrantedAuthority>of()
                : List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return new UserDetailsImpl(
                user.getUserId(),
                user.getUsername(),
                user.getPassword(),
                authorities);
    }

}