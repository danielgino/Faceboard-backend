package org.example.apimywebsite.service;

import org.example.apimywebsite.configuration.PasswordPolicy;
import org.example.apimywebsite.enums.Gender;
import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.*;
import org.example.apimywebsite.mapper.UserMapper;
import org.example.apimywebsite.repository.MessageRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.AuthHelper;
import org.example.apimywebsite.util.Constants;
import org.example.apimywebsite.util.DemoDataSeeder;
import org.example.apimywebsite.util.DemoScope;
import org.example.apimywebsite.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private FriendshipService friendshipService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthHelper authHelper;

    // Demo Mode: short-lived tokens (vs. the normal 1-hour default) so a leaked/scraped demo
    // token has a small useful window.
    private static final long DEMO_TOKEN_TTL_MILLIS = 900_000; // 15 minutes
    private static final int DEMO_SEARCH_MIN_LENGTH = 2;
    private static final int DEMO_MAX_SEARCH_RESULTS = 10;

    public UserService(UserRepository userRepository, CloudinaryService cloudinaryService) {
        this.userRepository = userRepository;
        this.cloudinaryService = cloudinaryService;
    }

    public String uploadProfilePicture(int userId, MultipartFile file) throws IOException {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String oldImageUrl = user.getProfilePictureUrl();
        // Cloudinary orphan-image cleanup: upload the new image FIRST - if it throws, nothing
        // below runs, so the old picture is never deleted for a failed replacement (unchanged
        // existing guarantee, now also protecting the old Cloudinary asset, not just the DB row).
        String imageUrl = cloudinaryService.uploadImage(file);
        user.setProfilePictureUrl(imageUrl);
        // COR-010 fix: if the save that would make this user row actually reference the new
        // image fails, the newly-uploaded asset itself becomes the orphan (it was never the old
        // one's turn to be deleted, so that part was already safe - see comment above). Compensate
        // by deleting the just-uploaded new asset before propagating the failure; the old asset
        // is left completely untouched either way.
        try {
            userRepository.save(user);
        } catch (RuntimeException e) {
            cloudinaryService.deleteImage(imageUrl);
            throw e;
        }
        cloudinaryService.deleteImage(oldImageUrl);
        return imageUrl;
    }

    public void removeProfilePicture(int userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String oldImageUrl = user.getProfilePictureUrl();
        user.setProfilePictureUrl(user.getGender() == Gender.FEMALE ? Constants.DEFAULT_PROFILE_PICTURE_FEMALE : Constants.DEFAULT_PROFILE_PICTURE_MALE);
        userRepository.save(user);
        cloudinaryService.deleteImage(oldImageUrl);
    }

public String loginByEmail(String email, String password) {
    if (email == null || email.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
    }

    User user = userRepository.findByEmail(email);
    // Demo Mode: seeded demo accounts (isDemo=true) are only ever reachable through
    // POST /auth/demo, never through normal email/password login - same generic response as any
    // other invalid credential, so a demo account's existence isn't distinguishable this way.
    if (user == null || user.isDemo()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
    if (!passwordEncoder.matches(password, user.getPassword())) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }return jwtUtil.generateToken(user.getUserName(), user.getPassword());
}

    // Demo Mode: issues a token for the single shared, seeded demo_user - never checks a
    // password (there is no legitimate path to reach this account otherwise). Returns null if
    // the seeder hasn't populated the demo dataset yet (e.g. Demo Mode just enabled, seeding
    // still pending on this boot), which the caller turns into a 503.
    public String loginAsDemo() {
        User demoUser = userRepository.findByUserName(DemoDataSeeder.DEMO_USERNAME);
        if (demoUser == null || !demoUser.isDemo()) {
            return null;
        }
        return jwtUtil.generateDemoToken(demoUser.getUserName(), demoUser.getPassword(), DEMO_TOKEN_TTL_MILLIS);
    }

    public void register(RegisterDTO dto) {
        if (userRepository.findByUserName(dto.getUsername()) != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already exists");
        }
        if (userRepository.findByEmail(dto.getEmail()) != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
        }
        if (dto.getBirthDate() == null || !isOldEnough(dto.getBirthDate(), 13)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User must be at least 13 years old");
        }

        User user = new User();
        user.setUserName(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setName(capitalize(dto.getName()));
        user.setLastname(capitalize(dto.getLastname()));
        user.setEmail(dto.getEmail());
        user.setBirthDate(dto.getBirthDate());
        user.setGender(dto.getGender());

        if (dto.getGender() != null) {
            user.setProfilePictureUrl(dto.getGender() == Gender.FEMALE ? Constants.DEFAULT_PROFILE_PICTURE_FEMALE : Constants.DEFAULT_PROFILE_PICTURE_MALE);
        }

        userRepository.save(user);
    }

    public boolean isOldEnough(LocalDate birthDate, int minAge) {
        return Period.between(birthDate, LocalDate.now()).getYears() >= minAge;
    }

    public UserDTO updateUserDetails(int userId, UpdateUserDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (dto.getNewPassword() != null && dto.getCurrentPassword() != null) {
            if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
            }
            if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different");
            }
            if (!PasswordPolicy.isValid(dto.getNewPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PasswordPolicy.MESSAGE);
            }
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        } else if (dto.getNewPassword() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required");
        }

        if (dto.getName() != null) user.setName(capitalize(dto.getName()));
        if (dto.getLastname() != null) user.setLastname(capitalize(dto.getLastname()));
        if (dto.getGender() != null) user.setGender(dto.getGender());
        if (dto.getBio() != null) user.setBio(dto.getBio());
        if (dto.getFacebookUrl() != null) user.setFacebookUrl(dto.getFacebookUrl());
        if (dto.getInstagramUrl() != null) user.setInstagramUrl(dto.getInstagramUrl());

        if (dto.getEmail() != null && !dto.getEmail().equals(user.getEmail())) {
            if (userRepository.findByEmail(dto.getEmail()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use");
            }
            user.setEmail(dto.getEmail());
        }

        userRepository.save(user);

        // M-DB3: PUT /user/settings's known frontend callers (per-field autosave and the
        // password-change form) both discard this response body entirely, so unlike GET /auth/me
        // (unchanged, still via getUserDTOById) there is no reason to pay for the last-message
        // enrichment here. Reuses the existing toUserDTOWithFriends mapper (same UserDTO shape/
        // field set as getUserDTOById - friendsList entries just carry unset lastMessage*/
        // sentByCurrentUser fields instead of populated ones) rather than adding a new DTO/method.
        List<User> friends = friendshipService.getAcceptedFriends(user);
        return userMapper.toUserDTOWithFriends(user, friends);
    }


    public User getUser(Integer id) {
        return userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    }

    public UserFriendsDTO getFriendsByUserId(int userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        List<User> acceptedFriends = friendshipService.getAcceptedFriends(user);
        return UserFriendsDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .username(user.getUserName())
                .profilePictureUrl(user.getProfilePictureUrl())
                .friendList(acceptedFriends.stream().map(userMapper::toFriendDTO).toList())
                .build();
    }

    private static final int MAX_FRIENDS_PAGE_SIZE = 50;

    public UserFriendsDTO getFriendsPageByUserId(int userId, int page, int size, String query) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        DemoScope.assertAccessible(authHelper.getCurrentUser(), user);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_FRIENDS_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        List<User> friendsPage = friendshipService.getAcceptedFriendsPage(user, query, pageable);
        return UserFriendsDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .username(user.getUserName())
                .profilePictureUrl(user.getProfilePictureUrl())
                .friendList(friendsPage.stream().map(userMapper::toFriendDTO).toList())
                .build();
    }


    public UserDTO getUserDTOById(int id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<User> friends = friendshipService.getAcceptedFriends(user);
        List<Integer> friendIds = friends.stream().map(User::getId).toList();
        // M-DB3: skip the query entirely when there's nothing to search - avoids a pointless
        // round-trip for the common case of a new/friendless user, and doesn't rely on Hibernate's
        // empty-IN-clause handling.
        List<Message> lastMessages = friendIds.isEmpty()
                ? List.of()
                : messageRepository.findLastMessagesBetweenUserAndFriends(id, friendIds);
        Map<Integer, Message> messageMap = new HashMap<>();
        for (Message msg : lastMessages) {
            int otherId = (msg.getSender().getId() == id) ? msg.getReceiver().getId() : msg.getSender().getId();
            messageMap.put(otherId, msg);
        }
        List<FriendDTO> friendsDTO = new ArrayList<>();
        for (User friend : friends) {
            Message message = messageMap.get(friend.getId());
            friendsDTO.add(userMapper.toFriendDTOWithMessage(friend, message, id));
        }

        return userMapper.toUserDTOWithFriendsAndLastMessage(user, friendsDTO);
    }


    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User findById(int id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public int getUserIdByUsername(String username) {
        User user = userRepository.findByUserName(username);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user.getId();
    }

    public User findByUserName(String userName) {
        return userRepository.findByUserName(userName);
    }
    // L-DB4A: max results a single search response can ever contain - this is autocomplete/
    // preview UX, not a paginated directory, so a small bounded cap (applied at the DB query
    // level via Pageable, not in-memory) is the correct fix rather than full Page<> pagination.
    private static final int MAX_SEARCH_RESULTS = 20;

    // Public-profile finding: getUserDTOById (full UserDTO, incl. email and message-preview
    // enrichment) is intentionally not reused here - a search result must never carry those
    // fields, regardless of who is searching.
    public List<UserSearchResultDTO> searchUsersByName(String name) {
        // L-DB4A: a blank/whitespace-only query previously became LIKE '%%' - matching (and
        // returning) every user in the table. No legitimate search intent exists for a blank
        // query, so it now short-circuits to an empty result before reaching the repository.
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String trimmed = name.trim();

        // Demo Mode: structurally scoped at the query level - a demo session can never even
        // fetch a real user row here, not just have one filtered out afterward. Slightly
        // tighter caps than the real search, appropriate for the tiny seed dataset.
        if (authHelper.getCurrentUser().isDemo()) {
            if (trimmed.length() < DEMO_SEARCH_MIN_LENGTH) {
                return List.of();
            }
            Pageable demoPageable = PageRequest.of(0, DEMO_MAX_SEARCH_RESULTS);
            return userRepository.searchDemoUsersByFullName(trimmed, demoPageable).stream()
                    .map(userMapper::toUserSearchResultDTO)
                    .toList();
        }

        Pageable pageable = PageRequest.of(0, MAX_SEARCH_RESULTS);
        return userRepository.searchByFullName(trimmed, pageable).stream()
                .map(userMapper::toUserSearchResultDTO)
                .toList();
    }

    // Public-profile finding: one uniform response for GET /user/by-id, self or not. Builds
    // friend entries directly from User (toPublicFriendDTO) - never calls MessageRepository, so
    // the previous private-message-preview leak is closed at the query level, not merely hidden
    // from serialization.
    public PublicUserProfileDTO getPublicUserProfileById(int id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        DemoScope.assertAccessible(authHelper.getCurrentUser(), user);
        List<PublicFriendDTO> friends = friendshipService.getAcceptedFriends(user).stream()
                .map(userMapper::toPublicFriendDTO)
                .toList();
        return userMapper.toPublicUserProfileDTO(user, friends);
    }

    private String capitalize(String input) {
        if (input == null || input.isBlank()) return input;
        return Arrays.stream(input.trim().toLowerCase().split(" "))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }


    public User save(User user) {
        return userRepository.save(user);
    }
}
