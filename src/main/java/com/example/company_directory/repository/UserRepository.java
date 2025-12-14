package com.example.company_directory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.company_directory.entity.User;

public interface UserRepository extends JpaRepository<User, String>{
    
}
