package com.vigilstack.accessrequest.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"username", "role"}),
    @UniqueConstraint(columnNames = "email"),
    @UniqueConstraint(columnNames = "mobile_number")
})
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    @Column(name = "profile_photo", length = 400000)
    private String profilePhoto;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    protected UserAccount() { }

    public UserAccount(String email, String fullName, String mobileNumber, String passwordHash, UserRole role) {
        this.username = email;
        this.fullName = fullName;
        this.email = email;
        this.mobileNumber = mobileNumber;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getMobileNumber() { return mobileNumber; }
    public String getProfilePhoto() { return profilePhoto; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }

    public void updateProfile(String fullName, String email, String mobileNumber, String profilePhoto) {
        this.username = email;
        this.fullName = fullName;
        this.email = email;
        this.mobileNumber = mobileNumber;
        this.profilePhoto = profilePhoto;
    }
}
