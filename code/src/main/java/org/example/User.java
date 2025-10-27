package org.example;

/**
 * Represents a user with a username and hashed password.
 * Tracks whether a plain password has been found.
 */

public final class User {
    private final String username;
    private final String hashedPassword;
    private String foundPassword;
    private boolean isFound;

    public User(String username, String hashedPassword) {
        if (username == null || hashedPassword == null) {
            throw new IllegalArgumentException("username and hashedPassword cannot be null");
        }
        this.username = username;
        this.hashedPassword = hashedPassword;
        this.isFound = false;
        this.foundPassword = null;
    }

    // Read-only accessors for core fields
    public String getUsername() {
        return username;
    }

    public String getHashedPassword() {
        return hashedPassword;
    }

    // Accessors for mutable state
    public boolean isFound() {
        return isFound;
    }

    public String getFoundPassword() {
        return foundPassword;
    }

    /**
     * Sets the found password and marks this user as found.
     * @param foundPassword the plain text password that matches the hash
     */
    public void markFound(String foundPassword) {
        if (foundPassword == null) {
            throw new IllegalArgumentException("foundPassword cannot be null");
        }
        this.foundPassword = foundPassword;
        this.isFound = true;
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", hashedPassword='" + hashedPassword + '\'' +
                ", isFound=" + isFound +
                ", foundPassword='" + foundPassword + '\'' +
                '}';
    }
}

