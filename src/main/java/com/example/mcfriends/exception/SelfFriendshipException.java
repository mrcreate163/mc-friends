package com.example.mcfriends.exception;

public class SelfFriendshipException extends RuntimeException {
    public SelfFriendshipException() {
        super("Cannot send friend request to yourself");
    }
}
