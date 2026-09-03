package org.example.apimywebsite.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.apimywebsite.dto.PublicFriendDTO;
import org.example.apimywebsite.dto.PublicUserProfileDTO;
import org.example.apimywebsite.dto.UserSearchResultDTO;
import org.example.apimywebsite.enums.Gender;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.AuthHelper;
import org.example.apimywebsite.util.InMemoryRateLimiter;
import org.example.apimywebsite.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public-profile finding: proves the exact serialized JSON shape of GET /user/by-id and
 * GET /user/name - an explicit allowlist assertion (the full key set), not just a check for a
 * handful of denied fields, so a newly added User/UserDTO field can't silently leak through here
 * without this test catching it.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "JWT_SECRET=test-only-placeholder-not-a-real-secret")
class UserControllerPublicProfileTest {

    private static final Set<String> PUBLIC_PROFILE_KEYS = Set.of(
            "id", "username", "name", "fullName", "profilePictureUrl",
            "bio", "gender", "birthDate", "facebookUrl", "instagramUrl", "friendsList");
    private static final Set<String> PUBLIC_FRIEND_KEYS = Set.of(
            "id", "username", "fullName", "profilePictureUrl");
    private static final Set<String> SEARCH_RESULT_KEYS = Set.of(
            "id", "fullName", "profilePictureUrl");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;
    @MockBean
    private AuthHelper authHelper;
    @MockBean
    private UserRepository userRepository;
    // H4 fix: WebConfig no longer implements WebMvcConfigurer (its addCorsMappings was the
    // duplicate CORS source, now removed), so it's no longer auto-swept into @WebMvcTest slices
    // - this JwtUtil bean (previously supplied incidentally via that sweep) must now be mocked
    // explicitly, matching every other @WebMvcTest slice in this suite.
    @MockBean
    private JwtUtil jwtUtil;
    // Demo Mode: DemoAccessFilter (a Filter, so swept into this slice's beans even though
    // addFilters=false means it never actually runs) needs this to be constructable.
    @MockBean
    private InMemoryRateLimiter rateLimiter;

    private PublicUserProfileDTO fullyPopulatedProfile(int id) {
        PublicFriendDTO friend = new PublicFriendDTO();
        friend.setId(9);
        friend.setUsername("carol");
        friend.setFullName("Carol Jones");
        friend.setProfilePictureUrl("https://cdn.example.com/carol.png");

        PublicUserProfileDTO dto = new PublicUserProfileDTO();
        dto.setId(id);
        dto.setUsername("bob");
        dto.setName("Bob");
        dto.setFullName("Bob Smith");
        dto.setProfilePictureUrl("https://cdn.example.com/bob.png");
        dto.setBio("hello");
        dto.setGender(Gender.MALE);
        dto.setBirthDate(LocalDate.of(2000, 1, 1));
        dto.setFacebookUrl("https://facebook.com/bob");
        dto.setInstagramUrl("https://instagram.com/bob");
        dto.setFriendsList(List.of(friend));
        return dto;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    @Test
    void getUserByIdForAnotherUser_returnsExactPublicProfileKeySet_noEmailOrMessagePreviewAnywhere() throws Exception {
        when(userService.getPublicUserProfileById(2)).thenReturn(fullyPopulatedProfile(2));

        MvcResult result = mockMvc.perform(get("/user/by-id").param("id", "2"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> json = asMap(result);
        assertEquals(PUBLIC_PROFILE_KEYS, json.keySet());
        assertFalse(json.containsKey("email"));

        List<Map<String, Object>> friends = (List<Map<String, Object>>) json.get("friendsList");
        assertEquals(1, friends.size());
        assertEquals(PUBLIC_FRIEND_KEYS, friends.get(0).keySet());
        assertFalse(friends.get(0).containsKey("email"));
        assertFalse(friends.get(0).containsKey("lastMessageContent"));
        assertFalse(friends.get(0).containsKey("lastMessageTime"));
        assertFalse(friends.get(0).containsKey("sentByCurrentUser"));
        assertFalse(friends.get(0).containsKey("birthDate"));
    }

    @Test
    void getUserByIdForSelf_returnsSamePublicProfileSchema_noRequesterDependentShape() throws Exception {
        int selfId = 1000; // outside the Integer cache range, matching the /user/id ID convention
        when(authHelper.getCurrentUser()).thenReturn(org.example.apimywebsite.api.model.User.builder().id(selfId).build());
        when(userService.getPublicUserProfileById(selfId)).thenReturn(fullyPopulatedProfile(selfId));

        MvcResult result = mockMvc.perform(get("/user/by-id").param("id", String.valueOf(selfId)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> json = asMap(result);
        assertEquals(PUBLIC_PROFILE_KEYS, json.keySet());
        assertFalse(json.containsKey("email"));
    }

    @Test
    void getUserByIdUnknownId_returns404() throws Exception {
        when(userService.getPublicUserProfileById(999))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        mockMvc.perform(get("/user/by-id").param("id", "999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUsersByName_returnsExactSearchResultKeySet() throws Exception {
        UserSearchResultDTO result = new UserSearchResultDTO();
        result.setId(3);
        result.setFullName("Dan X");
        result.setProfilePictureUrl("https://cdn.example.com/dan.png");
        when(userService.searchUsersByName("dan")).thenReturn(List.of(result));

        MvcResult mvcResult = mockMvc.perform(get("/user/name").param("name", "dan"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> json = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(), new TypeReference<>() {});
        assertEquals(1, json.size());
        assertEquals(SEARCH_RESULT_KEYS, json.get(0).keySet());
    }
}
