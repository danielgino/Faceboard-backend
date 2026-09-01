package org.example.apimywebsite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// SUG-002: response shape for GET /friendship/suggestions. Reuses PublicFriendDTO (the same
// allowlist - id/username/fullName/profilePictureUrl - already used for a friend entry on
// another user's profile) rather than a new per-suggestion DTO.
//
// The whole cursor contract is carried statelessly in this response and echoed back verbatim by
// the client as request params on the next "Show more" call - no server-side session/DB state:
//   - nextCursor: where the next call should resume from (meaning depends on `wrapped`, see
//                 FriendshipService.getSuggestedFriends).
//   - seed:       this traversal's fixed starting boundary (the very first request's effective
//                 cursor). Must be echoed back unchanged for the life of one traversal - it's
//                 what lets phase B ("wrapped") know where it must stop instead of climbing back
//                 into ids phase A already returned.
//   - wrapped:    false while still in phase A (id > cursor, unbounded above); true once the
//                 traversal has moved into phase B (bounded strictly below `seed`).
// hasMore reflects an actual probe of the next page, not just "this page happened to be full".
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SuggestedFriendsResponseDTO {
    private List<PublicFriendDTO> users;
    private Integer nextCursor;
    private Integer seed;
    private boolean wrapped;
    private boolean hasMore;
}
