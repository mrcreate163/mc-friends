package com.example.mcfriends.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class AccountDto {
    private UUID id;
    private String username;
    private String firstName;
    private String lastName;
    private String profilePictureUrl;
}