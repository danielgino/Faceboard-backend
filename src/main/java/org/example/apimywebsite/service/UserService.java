package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Gender;
import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.*;
import org.example.apimywebsite.mapper.UserMapper;
import org.example.apimywebsite.repository.MessageRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.Constants;
import org.example.apimywebsite.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
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
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("user not found"));
        String imageUrl = cloudinaryService.uploadImage(file);
        user.setProfilePictureUrl(imageUrl);
        userRepository.save(user);
        return imageUrl;
    }

    public void removeProfilePicture(int userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("user not found"));
        user.setProfilePictureUrl(user.getGender() == Gender.FEMALE ? Constants.DEFAULT_PROFILE_PICTURE_FEMALE : Constants.DEFAULT_PROFILE_PICTURE_MALE);
        userRepository.save(user);
    }

    public String login(String username, String password) {
        User user = userRepository.findByUserName(username);
        if (user != null && passwordEncoder.matches(password, user.getPassword())|| user.getPassword().equals(password)) { // 🔥 אם לא מוצפן) {
            return jwtUtil.generateToken(username);
        }
        return null;
    }

    public void register(RegisterDTO dto) {
        if (userRepository.findByUserName(dto.getUsername()) != null) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.findByEmail(dto.getEmail()) != null) {
            throw new RuntimeException("Email already exists");
        }
        if (dto.getBirthDate() == null || !isOldEnough(dto.getBirthDate(), 13)) {
            throw new RuntimeException("User must be at least 13 years old");
        }

        User user = new User();
        user.setUserName(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setLastname(dto.getLastname());
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
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        } else if (dto.getNewPassword() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required");
        }

        if (dto.getName() != null) user.setName(dto.getName());
        if (dto.getLastname() != null) user.setLastname(dto.getLastname());
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

    public User getUser(Integer id) {
        return userRepository.findById(id).orElse(null);
    }

    public UserFriendsDTO getFriendsByUserId(int userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        List<User> acceptedFriends = friendshipService.getAcceptedFriends(user);
        return UserFriendsDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .username(user.getUserName())
                .profilePictureUrl(user.getProfilePictureUrl())
                .friendList(acceptedFriends.stream().map(userMapper::toFriendDTO).toList())
                .build();
    }

    public List<UserDTO> findUsersByName(String name) {
        List<User> users = userRepository.findByNameContaining(name);
        if (users.isEmpty()) return Collections.emptyList();

        List<UserDTO> userDTOs = new ArrayList<>();
        for (User user : users) {
            List<User> friends = friendshipService.getAcceptedFriends(user);
            userDTOs.add(userMapper.toUserDTOWithFriends(user, friends));
        }
        return userDTOs;
    }

    public UserDTO getUserDTOById(int id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("user not found"));

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
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("user not found"));
    }

    public int getUserIdByUsername(String username) {
        User user = userRepository.findByUserName(username);
        if (user == null) {
            throw new RuntimeException("User not found: " + username);
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



    public User save(User user) {
        return userRepository.save(user);
    }
}
