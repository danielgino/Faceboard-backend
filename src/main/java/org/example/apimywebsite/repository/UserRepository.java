package org.example.apimywebsite.repository;


import org.example.apimywebsite.api.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Integer> {
    @Query("SELECT u FROM User u WHERE u.email = :email")
    User findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.userName = :userName")
    User findByUserName(@Param("userName") String userName);

    // L-DB4A: same searched fields as before (name, lastname, concatenated full name) - only
    // the addition of a deterministic ORDER BY and a Pageable parameter changed. Spring Data
    // JPA applies the Pageable as a real LIMIT/OFFSET at the SQL level, so the database itself
    // never materializes more than the requested page - a plain List is still returned (not
    // Page<>), preserving the existing flat-array response contract.
    @Query("""
    SELECT u FROM User u
    WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))
       OR LOWER(u.lastname) LIKE LOWER(CONCAT('%', :query, '%'))
       OR LOWER(CONCAT(u.name, ' ', u.lastname)) LIKE LOWER(CONCAT('%', :query, '%'))
    ORDER BY u.name ASC, u.lastname ASC, u.id ASC
""")
    List<User> searchByFullName(@Param("query") String query, Pageable pageable);

    // SUG-002 (Suggested Friends): keyset-paginated candidate scan driven off users.id (the
    // primary key, already indexed) - no ORDER BY RAND(), no OFFSET. Excludes the caller and
    // anyone with a `friends` row in either direction (pending or accepted) via two explicit
    // NOT EXISTS checks against the existing friends(user_id, friend_id) composite primary key -
    // each is a direct composite-PK point lookup (one fixed column + one correlated column),
    // never a scan, regardless of table size.
    //
    // One traversal is split into two bounded phases so it can never revisit an id it has
    // already shown (see FriendshipService.getSuggestedFriends for the full protocol):
    //   phase A - findSuggestedFriendsAfter(cursor):  id > cursor,          unbounded above
    //   phase B - findSuggestedFriendsBetween(lo,hi): lo < id < hi (= seed), bounded above by
    //             the traversal's own starting seed, so phase B can never climb back into
    //             phase A's already-covered id > seed range.
    //
    // SUG-004: `extraExcludeId` additionally excludes one more id from the candidate scan - used
    // to keep the reserved creator slot from ever double-counting the creator as an ordinary
    // candidate too, by making the "regular" query simply never see them in the first place
    // (rather than fetching them and removing/trimming afterward, which is what silently
    // dropped a real candidate - see FriendshipService.getSuggestedFriends). Pass -1 (never a
    // real user id) when there is nothing extra to exclude.
    @Query("""
    SELECT u FROM User u
    WHERE u.id > :cursor AND u.id <> :meId AND u.id <> :extraExcludeId
      AND NOT EXISTS (SELECT 1 FROM Friends f WHERE f.id.userId = :meId AND f.id.friendId = u.id)
      AND NOT EXISTS (SELECT 1 FROM Friends f2 WHERE f2.id.userId = u.id AND f2.id.friendId = :meId)
    ORDER BY u.id ASC
""")
    List<User> findSuggestedFriendsAfter(@Param("meId") int meId, @Param("cursor") int cursor,
                                          @Param("extraExcludeId") int extraExcludeId, Pageable pageable);

    @Query("""
    SELECT u FROM User u
    WHERE u.id > :lowExclusive AND u.id < :highExclusive AND u.id <> :meId AND u.id <> :extraExcludeId
      AND NOT EXISTS (SELECT 1 FROM Friends f WHERE f.id.userId = :meId AND f.id.friendId = u.id)
      AND NOT EXISTS (SELECT 1 FROM Friends f2 WHERE f2.id.userId = u.id AND f2.id.friendId = :meId)
    ORDER BY u.id ASC
""")
    List<User> findSuggestedFriendsBetween(@Param("meId") int meId, @Param("lowExclusive") int lowExclusive,
                                            @Param("highExclusive") int highExclusive,
                                            @Param("extraExcludeId") int extraExcludeId, Pageable pageable);
}
