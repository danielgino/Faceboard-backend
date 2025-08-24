package org.example.apimywebsite.service;

import org.example.apimywebsite.configuration.PasswordPolicy;
import org.example.apimywebsite.enums.Gender;
import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.*;
import org.example.apimywebsite.mapper.UserMapper;
import org.example.apimywebsite.repository.MessageRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.Constants;
import org.example.apimywebsite.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
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

    public UserService(UserRepository userRepository, CloudinaryService cloudinaryService) {
        this.userRepository = userRepository;
        this.cloudinaryService = cloudinaryService;
    }

    public String uploadProfilePicture(int userId, MultipartFile file) throws IOException {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String imageUrl = cloudinaryService.uploadImage(file);
        user.setProfilePictureUrl(imageUrl);
        userRepository.save(user);
        return imageUrl;
    }

    public void removeProfilePicture(int userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setProfilePictureUrl(user.getGender() == Gender.FEMALE ? Constants.DEFAULT_PROFILE_PICTURE_FEMALE : Constants.DEFAULT_PROFILE_PICTURE_MALE);
        userRepository.save(user);
    }

    public String login(String username, String password) {
        User user = userRepository.findByUserName(username);
        if (user != null && passwordEncoder.matches(password, user.getPassword())|| user.getPassword().equals(password)) {
            return jwtUtil.generateToken(username);
        }
        return null;
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
//להחזיר
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
        return getUserDTOById(userId);
    }

//    public UserDTO updateUserDetails(int userId, UpdateUserDTO dto) {
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
//
//        if (dto.getNewPassword() != null) {
//            if (dto.getCurrentPassword() == null) {
//                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required");
//            }
//
//            String current = dto.getCurrentPassword();
//            String next    = dto.getNewPassword();
//
//            boolean currentOk =
//                    passwordEncoder.matches(current, user.getPassword())
//                            || current.equals(user.getPassword()); // תמיכה ב-plaintext
//
//            if (!currentOk) {
//                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
//            }
//
//            boolean sameAsOld =
//                    passwordEncoder.matches(next, user.getPassword())
//                            || next.equals(user.getPassword());
//
//            if (sameAsOld) {
//                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different");
//            }
//
//            if (!PasswordPolicy.isValid(next)) {
//                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PasswordPolicy.MESSAGE);
//            }
//
//            user.setPassword(passwordEncoder.encode(next));
//        }
//
//        if (dto.getName() != null) user.setName(capitalize(dto.getName()));
//        if (dto.getLastname() != null) user.setLastname(capitalize(dto.getLastname()));
//        if (dto.getGender() != null) user.setGender(dto.getGender());
//        if (dto.getBio() != null) user.setBio(dto.getBio());
//        if (dto.getFacebookUrl() != null) user.setFacebookUrl(dto.getFacebookUrl());
//        if (dto.getInstagramUrl() != null) user.setInstagramUrl(dto.getInstagramUrl());
//
//        if (dto.getEmail() != null && !dto.getEmail().equalsIgnoreCase(user.getEmail())) {
//            if (userRepository.findByEmail(dto.getEmail()) != null) {
//                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use");
//            }
//            user.setEmail(dto.getEmail().toLowerCase());
//        }
//
//        userRepository.save(user);
//        return getUserDTOById(userId);
//    }


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


    public UserDTO getUserDTOById(int id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<User> friends = friendshipService.getAcceptedFriends(user);
        List<Integer> friendIds = friends.stream().map(User::getId).toList();
        List<Message> lastMessages = messageRepository.findLastMessagesBetweenUserAndFriends(id, friendIds);
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
public List<UserDTO> findUsersByFullName(String name) {
    List<User> users = userRepository.searchByFullName(name.trim());
    return users.stream()
            .map(userMapper::toUserDTO)
            .toList();
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
