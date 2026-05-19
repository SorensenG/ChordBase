package com.chordbase.domain.entities;

import com.chordbase.domain.valueobjects.UserName;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity(name = "User")
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Embedded
    private UserName userName;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String passwordHash;

    private String profileImageUrl;

    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_uuid"),
            inverseJoinColumns = @JoinColumn(name = "role_uuid"))
    private List<Role> roles;

    @OneToMany(mappedBy = "owner", fetch = FetchType.LAZY)
    private List<Setlist> setlists;

    public UUID getUuid() {
        return uuid;
    }

    public String getUserName() {
        return userName == null ? null : userName.value();
    }

    public UserName getUserNameValue() {
        return userName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public List<Setlist> getSetlists() {
        return setlists;
    }

}
