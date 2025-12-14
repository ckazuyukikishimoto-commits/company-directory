package com.example.company_directory.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @Column(length = 50)
    private String userId;

    @Column(nullable = false, length = 255)
    private String passwordHash;
}