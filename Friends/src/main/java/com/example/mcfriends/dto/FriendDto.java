package com.example.mcfriends.dto;

import com.example.mcfriends.model.FriendshipStatus;
import lombok.Data;

@Data
public class FriendDto {
    private AccountDto account;
    private FriendshipStatus status;
}