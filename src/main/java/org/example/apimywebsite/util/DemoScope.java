package org.example.apimywebsite.util;

import org.example.apimywebsite.api.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// Demo Mode data isolation: the ONLY membership signal for "is this row part of the public Demo
// dataset" is User.isDemo (seeded true for demo_user and its seed friends by DemoDataSeeder,
// false for every real account) - never a username convention. Every allowlisted read that can
// return another user's (or another user-owned resource's) data calls this once, after fetching
// the owner, so a ROLE_DEMO caller can only ever see isDemo=true rows. No-ops entirely for
// non-demo callers, so real-user behavior is completely unchanged.
public final class DemoScope {

    private DemoScope() {
    }

    public static void assertAccessible(User currentUser, User owner) {
        if (currentUser != null && currentUser.isDemo() && (owner == null || !owner.isDemo())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
    }
}
