package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Gender;
import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.*;
import org.example.apimywebsite.repository.MessageRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.Constants;
import org.example.apimywebsite.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    @Autowired
    private JwtUtil jwtUtil;

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
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("user not found"));;
         String imageUrl = cloudinaryService.uploadImage(file);
            user.setProfilePictureUrl(imageUrl);
            userRepository.save(user);
            return imageUrl;
    }
    public void removeProfilePicture(int userId) {
        User user =  userRepository.findById(userId).orElseThrow(() -> new RuntimeException("user not found"));
           if (user.getGender()== Gender.FEMALE){
               user.setProfilePictureUrl(Constants.DEFAULT_PROFILE_PICTURE_FEMALE);
           }
           else {
               user.setProfilePictureUrl(Constants.DEFAULT_PROFILE_PICTURE_MALE);
           }
            userRepository.save(user);
        }


    public String login(String username, String password) {
        User user = userRepository.findByUserName(username);
        if (user != null && passwordEncoder.matches(password, user.getPassword())|| user.getPassword().equals(password)) { // 🔥 אם לא מוצפן) {
            return jwtUtil.generateToken(username);
        }
        return null;
    }
//    public User addUser(User user) {
//        return userRepository.save(user);
//    }
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
            switch (dto.getGender()) {
                case MALE -> user.setProfilePictureUrl(Constants.DEFAULT_PROFILE_PICTURE_MALE);
                case FEMALE -> user.setProfilePictureUrl(Constants.DEFAULT_PROFILE_PICTURE_FEMALE);
                default -> user.setProfilePictureUrl(null);
            }
        }
        userRepository.save(user);
    }
    public boolean isOldEnough(LocalDate birthDate, int minAge) {
        return Period.between(birthDate, LocalDate.now()).getYears() >= minAge;
    }

    public UserDTO updateUserDetails(int userId, UpdateUserDTO dto) {
        System.out.println("📥 Reached updateUserDetails");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (dto.getNewPassword() != null && dto.getCurrentPassword() != null) {
            if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
            }

            if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different from current password");
            }

            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }

        if (dto.getNewPassword() != null && dto.getCurrentPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required to change password");
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
        System.out.println("Searching for user with id: " + id);
        Optional<User> user = userRepository.findById(id);
        if (user.isPresent()) {
            System.out.println("Found user: " + user.get());
            return user.get();
        } else {
            return null;
        }
    }
    public UserFriendsDTO getFriendsByUserId(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<User> acceptedFriends = friendshipService.getAcceptedFriends(user);

        List<FriendDTO> friendsList = acceptedFriends.stream()
                .map(friend -> new FriendDTO(
                        friend.getId(),
                        friend.getName(),
                        friend.getLastname(),
                        friend.getUserName(),
                        friend.getProfilePictureUrl()
                ))
                .toList();

        return UserFriendsDTO.builder()
                .id(user.getId())
                .fullName(user.getName() + " " + user.getLastname())
                .username(user.getUserName())
                .profilePictureUrl(user.getProfilePictureUrl())
                .friendList(friendsList)
                .build();
    }

    public List<UserDTO> findUsersByName(String name) {
        List<User> users = userRepository.findByNameContaining(name);

        if (users.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserDTO> userDTOs = new ArrayList<>();
        for (User user : users) {
            List<FriendDTO> friendsList = new ArrayList<>();
            List<User> acceptedFriends = friendshipService.getAcceptedFriends(user);

            for (User friend : acceptedFriends) {
                friendsList.add(new FriendDTO(friend.getId(), friend.getName(), friend.getLastname(),friend.getUserName(),friend.getProfilePictureUrl()));
            }

            UserDTO userDTO = UserDTO.builder()
                    .id(user.getId())
                    .username(user.getUserName())
                    .name(user.getName())
                    .lastname(user.getLastname())
                    .birthDate(user.getBirthDate())
                    .gender(user.getGender())
                    .profilePictureUrl(user.getProfilePictureUrl())
                    .friendsList(friendsList)
                    .build();
            userDTOs.add(userDTO);


        }

        return userDTOs;
    }
    public UserDTO getUserDTOById(int id) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("user not found"));  // מחפש את המשתמש לפי ה-id
//        if (user == null) {  // אם לא נמצא משתמש, מחזירים null
//            return null;
//        }
        List<FriendDTO> friendsList = new ArrayList<>();
        List<User> acceptedFriends = friendshipService.getAcceptedFriends(user);

        for (User friend : acceptedFriends) {
            FriendDTO dto = new FriendDTO(
                    friend.getId(),
                    friend.getName(),
                    friend.getLastname(),
                    friend.getUserName(),
                    friend.getProfilePictureUrl()
            );
            System.out.println("🔍 מחפש הודעות בין המשתמש: " + id + " לחבר: " + friend.getId());

            Message lastMessage = messageRepository
                    .findMessagesBetweenUsers(id, friend.getId(), PageRequest.of(0, 1))
                    .stream()
                    .findFirst()
                    .orElse(null);

            if (lastMessage != null) {
                dto.setLastMessageContent(lastMessage.getMessage());
                dto.setLastMessageTime(lastMessage.getSentTime());
                dto.setSentByCurrentUser(lastMessage.getSender().getId() == id);
            }else {
                dto.setLastMessageContent("No messages yet");
                dto.setLastMessageTime(null);
                dto.setSentByCurrentUser(false);
            }

            friendsList.add(dto);
        }

        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUserName())
                .name(user.getName())
                .lastname(user.getLastname())
                .email(user.getEmail())
                .birthDate(user.getBirthDate())
                .gender(user.getGender())
                .bio(user.getBio())
                .facebookUrl(user.getFacebookUrl())
                .instagramUrl(user.getInstagramUrl())
                .friendsList(friendsList)
                .profilePictureUrl(user.getProfilePictureUrl())
                .build();
    }
    public User findByEmail(String email) {
        return userRepository.findByEmail(email); // מחזיר User ישירות
    }

    public User findById(int id){
        return  userRepository.findById(id).orElseThrow(() -> new RuntimeException("user not found"));
    }
    public int getUserIdByUsername(String username) {
        User user = userRepository.findByUserName(username);
        if (user == null) {
            throw new RuntimeException("User not found: " + username);
        }
        return user.getId();
    }
    public User findByUserName(String userName){
        return userRepository.findByUserName(userName);
    }



    public User findByLastName(String lastname){
        return userRepository.findByLastname(lastname);
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}