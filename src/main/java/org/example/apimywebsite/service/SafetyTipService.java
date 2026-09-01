package org.example.apimywebsite.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// TIP-001: Safety Tip feature, deliberately AI-free per product decision - a curated static pool
// served through a lightweight in-memory shuffle/rotation, no Gemini, no external API, no API
// key, no scheduled refresh, no database table, no Redis. GET /safety-tips/random calls
// getRandomTip() once per request; every other line of state here lives only in this singleton
// bean's memory for the life of the process.
//
// Rotation: the pool is shuffled once at construction, then served front-to-back via `position`.
// Once the shuffled deck is exhausted, it's reshuffled and `position` resets to 0 - a fresh
// reshuffle can coincidentally put the tip that was JUST served back at the front, so
// reshuffleAvoidingImmediateRepeat swaps it out of the first slot in that one case. Within a
// single pass, every tip in TIPS is distinct, so no other repeat is possible until exhaustion.
//
// Thread safety: getRandomTip() is `synchronized` on this singleton instance. Given the small
// pool size and the fact this is nowhere near a hot path, a plain synchronized method is simpler
// and just as correct as any lock-free scheme, and keeps the whole feature intentionally small.
@Service
public class SafetyTipService {

    private static final List<String> TIPS = List.of(
            // Privacy
            "Review your privacy settings regularly to control who can see your posts, photos, and profile information.",
            "Limit your profile's visibility to friends only if you don't want strangers browsing your posts.",
            "Periodically review which third-party apps have access to your account and remove ones you no longer use.",

            // Password security
            "Use a strong, unique password for each of your online accounts.",
            "Avoid using easily guessed passwords like birthdays, pet names, or \"123456\".",
            "Consider using a password manager to generate and store strong passwords safely.",
            "Change your password promptly if you suspect it may have been exposed in a data breach.",

            // Phishing
            "Be wary of messages urging you to \"verify your account\" by clicking a link and entering your password.",
            "Legitimate companies rarely ask for your password or payment details over chat or email.",
            "Double-check the sender's actual email address or username, not just the display name.",

            // Suspicious links
            "Avoid clicking links from unfamiliar accounts, even if the message looks urgent or exciting.",
            "Hover over a link before clicking to check where it actually leads.",
            "Shortened links can hide their real destination - be extra cautious with those from strangers.",
            "Be cautious of links promising \"see who viewed your profile\" - these are almost always scams.",

            // Fake accounts / impersonation
            "Watch out for duplicate accounts pretending to be someone you already know.",
            "If a friend's account starts messaging you strangely, verify through another channel before responding.",
            "Report impersonation accounts to the platform so others aren't fooled too.",

            // Friend requests from strangers
            "Think twice before accepting friend requests from people you don't actually know.",
            "A profile with few photos, few friends, and a recent creation date can be a red flag.",
            "Mutual friends alone don't guarantee a stranger's request is genuine.",

            // Location sharing
            "Avoid posting your real-time location - consider sharing where you've been only after you've left.",
            "Turn off location tagging on photos if you don't want others to know exactly where they were taken.",

            // Personal information
            "Avoid sharing your home address, phone number, or financial details in public posts.",
            "Be cautious about posting details that could answer common security questions, like your first pet's name.",
            "Avoid posting photos of tickets, IDs, or documents that contain personal information.",

            // Two-factor authentication
            "Turn on two-factor authentication wherever it's offered for an extra layer of account protection.",
            "Prefer an authenticator app over SMS codes for two-factor authentication when possible.",
            "Store your two-factor backup codes somewhere safe in case you lose access to your device.",

            // Public / shared devices
            "Always log out of your account when using a public or shared computer.",
            "Avoid saving your password in a browser on a device other people can access.",
            "Use private/incognito browsing when checking your account on a device that isn't yours.",

            // Scams
            "Be skeptical of offers that seem too good to be true, like free giveaways requiring personal information.",
            "Never send money or gift cards to someone you've only spoken with online.",
            "Be cautious of urgent messages claiming your account will be suspended unless you act immediately.",

            // Reporting / blocking suspicious users
            "Use the platform's report and block features whenever you encounter suspicious or abusive behavior.",
            "Blocking a suspicious account is often faster and safer than trying to reason with it.",

            // Oversharing
            "Think before posting details about your daily routine that could reveal when you're away from home.",
            "Consider whether a post reveals more about your workplace, school, or family than you intend.",

            // Account recovery / security
            "Keep your recovery email and phone number up to date so you can regain access if locked out.",
            "Set up account recovery options before you need them, not after you're locked out."
    );

    private List<String> shuffledDeck;
    private int position;
    private String lastServedTip;

    public SafetyTipService() {
        this.shuffledDeck = shuffledCopy();
        this.position = 0;
    }

    public synchronized String getRandomTip() {
        if (position >= shuffledDeck.size()) {
            reshuffleAvoidingImmediateRepeat();
        }
        String tip = shuffledDeck.get(position);
        position++;
        lastServedTip = tip;
        return tip;
    }

    private void reshuffleAvoidingImmediateRepeat() {
        List<String> next = shuffledCopy();
        if (TIPS.size() > 1 && next.get(0).equals(lastServedTip)) {
            String first = next.get(0);
            next.set(0, next.get(1));
            next.set(1, first);
        }
        shuffledDeck = next;
        position = 0;
    }

    private List<String> shuffledCopy() {
        List<String> copy = new ArrayList<>(TIPS);
        Collections.shuffle(copy);
        return copy;
    }
}
