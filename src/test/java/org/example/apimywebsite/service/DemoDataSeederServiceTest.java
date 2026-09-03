package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.PostImage;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.enums.Gender;
import org.example.apimywebsite.enums.FriendshipStatus;
import org.example.apimywebsite.repository.CommentRepository;
import org.example.apimywebsite.repository.FriendshipRepository;
import org.example.apimywebsite.repository.LikeRepository;
import org.example.apimywebsite.repository.MessageRepository;
import org.example.apimywebsite.repository.NotificationRepository;
import org.example.apimywebsite.repository.PostImageRepository;
import org.example.apimywebsite.repository.PostRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.DemoAssets;
import org.example.apimywebsite.util.DemoDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Verifies the two properties the non-transactional, self-invoked version of this seeder could
// not guarantee: (1) calling seedIfNeeded() again against an already-fully-seeded database (the
// real "app redeployed against the same DB" scenario) never duplicates rows, and (2) a detected
// partial/incomplete dataset is cleaned up and correctly re-seeded rather than silently left
// broken. Uses the same isolated H2 "test" profile as ApiMyWebsiteApplicationTests
// (DB_CLOSE_DELAY=-1 keeps the in-memory instance alive across this test's multiple calls).
//
// Deliberately NOT @Transactional at the test level: each call below gets its own independent,
// genuinely-committing transaction (seedIfNeeded() via its own @Transactional; the step-3
// simulation via an explicit TransactionTemplate), the same way separate real application
// restarts each get their own fresh session - this is what's actually being verified, and it
// also sidesteps first-level-cache artifacts that sharing one long-lived Hibernate session
// across simulated "restarts" would otherwise introduce.
@SpringBootTest
@ActiveProfiles("test")
class DemoDataSeederServiceTest {

    private static final List<String> SEED_USERNAMES =
            List.of(DemoDataSeeder.DEMO_USERNAME, "demo_alex", "demo_jamie", "demo_sam");

    // Expanded seed content (see DemoDataSeederService.seed()): 10 posts (5 text-only, 5 with a
    // local demo-asset image), 8 comments, 12 likes, 6 friendship pairs (12 rows), 7 messages
    // across 3 conversations, 3 notifications - all fixed/deterministic, so these counts are
    // exact, not just non-zero checks.
    private static final int EXPECTED_POSTS = 10;
    private static final int EXPECTED_MESSAGES = 7;
    private static final int EXPECTED_NOTIFICATIONS = 3;

    @Autowired
    private DemoDataSeederService demoDataSeederService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostImageRepository postImageRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private LikeRepository likeRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void seedIfNeeded_repeatedCalls_neverDuplicate_andRecoverFromPartialState() {
        // 1) Fresh seed.
        demoDataSeederService.seedIfNeeded();
        List<User> afterFirstSeed = userRepository.findAllByUserNameIn(SEED_USERNAMES);
        assertEquals(4, afterFirstSeed.size(), "first call must create exactly the 4 seed users");
        long postsAfterFirst = countSeedPosts(afterFirstSeed);
        assertEquals(EXPECTED_POSTS, postsAfterFirst, "first call must create exactly the 10 seed posts");
        assertEquals(EXPECTED_MESSAGES, countMessages(afterFirstSeed), "first call must create exactly the 7 seed messages");
        assertEquals(EXPECTED_NOTIFICATIONS, countNotifications(afterFirstSeed), "first call must create exactly the 3 seed notifications");

        // 2) Simulate a redeploy re-running the same CommandLineRunner against the same DB.
        demoDataSeederService.seedIfNeeded();
        List<User> afterSecondCall = userRepository.findAllByUserNameIn(SEED_USERNAMES);
        assertEquals(4, afterSecondCall.size(), "repeated call must not create duplicate seed users");
        assertEquals(postsAfterFirst, countSeedPosts(afterSecondCall),
                "repeated call must not create duplicate seed posts");
        assertEquals(EXPECTED_MESSAGES, countMessages(afterSecondCall), "repeated call must not duplicate seed messages");
        assertEquals(EXPECTED_NOTIFICATIONS, countNotifications(afterSecondCall), "repeated call must not duplicate seed notifications");

        // 3) Simulate an interrupted run: manually remove one seed user (and their owned rows,
        // including their like on demoUser's post and their seeded messages/notification - all
        // cross-user references outside their own posts' cascade), leaving exactly the kind of
        // partial state the old demo_user-only existence check could never detect or recover
        // from. Runs in its own transaction (the @Modifying cleanup queries require one) rather
        // than the test method itself.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User sam = userRepository.findAllByUserNameIn(SEED_USERNAMES).stream()
                    .filter(u -> "demo_sam".equals(u.getUserName())).findFirst().orElseThrow();
            messageRepository.deleteAllBySenderOrReceiver(sam);
            notificationRepository.deleteAllInvolvingUser(sam);
            likeRepository.deleteAllByUser(sam);
            commentRepository.deleteAllByUser(sam);
            postRepository.deleteAll(postRepository.findByUserId(sam.getId()));
            postRepository.flush();
            friendshipRepository.deleteAllInvolvingUser(sam);
            userRepository.delete(sam);
        });
        assertEquals(3, userRepository.findAllByUserNameIn(SEED_USERNAMES).size(),
                "precondition: exactly 3 of 4 seed users remain after simulated partial removal");

        // 4) Recovery pass: must detect the incomplete set, clean up the 3 remaining partial
        // rows, and end up with exactly 4 correctly-seeded users again - no duplicates, no
        // leftover orphans, and never silently no-op just because *some* seed user still exists.
        demoDataSeederService.seedIfNeeded();
        List<User> afterRecovery = userRepository.findAllByUserNameIn(SEED_USERNAMES);
        assertEquals(4, afterRecovery.size(), "recovery pass must restore exactly the 4 seed users");
        assertTrue(afterRecovery.stream().allMatch(User::isDemo), "every recovered seed user must be isDemo=true");
        assertEquals(EXPECTED_POSTS, countSeedPosts(afterRecovery), "recovery pass must restore exactly the 10 seed posts");
        assertEquals(EXPECTED_MESSAGES, countMessages(afterRecovery), "recovery pass must restore exactly the 7 seed messages");
        assertEquals(EXPECTED_NOTIFICATIONS, countNotifications(afterRecovery), "recovery pass must restore exactly the 3 seed notifications");
    }

    // "No external Demo image traffic" verification: every seeded profile picture and post image
    // must be a local /demo-assets/... path (DemoAssets.java) - never Cloudinary or any other
    // remote host - and must end in the exact extension of the files actually placed under
    // Faceboard-frontend/public/demo-assets/ (.jpeg for profiles, .png for posts), not the ".jpg"
    // originally assumed. Static/data-level check, not an HTTP one - consistent with "static
    // tracing / H2 tests only" for this phase.
    @Test
    void seedIfNeeded_allImageUrls_areLocalDemoAssetPaths_withCorrectExtensions_neverExternal() {
        demoDataSeederService.seedIfNeeded();
        List<User> seedUsers = userRepository.findAllByUserNameIn(SEED_USERNAMES);
        assertEquals(4, seedUsers.size());

        for (User user : seedUsers) {
            String url = user.getProfilePictureUrl();
            assertTrue(url != null && url.startsWith("/demo-assets/profiles/"),
                    "seed user " + user.getUserName() + " must use a local /demo-assets/profiles/ path, was: " + url);
            assertTrue(url.endsWith(".jpeg"),
                    "seed user " + user.getUserName() + " profile picture must end in .jpeg (the actual placed file's extension), was: " + url);
        }

        List<Post> seedPosts = seedUsers.stream()
                .flatMap(u -> postRepository.findByUserId(u.getId()).stream())
                .toList();
        List<PostImage> seedImages = seedPosts.stream()
                .flatMap(p -> postImageRepository.findByPost_PostId(p.getPostId()).stream())
                .toList();
        assertEquals(5, seedImages.size(), "expected exactly the 5 seeded posts that carry an image");
        for (PostImage image : seedImages) {
            assertTrue(image.getImageUrl().startsWith("/demo-assets/posts/"),
                    "seed post image must use a local /demo-assets/posts/ path, was: " + image.getImageUrl());
            assertTrue(image.getImageUrl().endsWith(".png"),
                    "seed post image must end in .png (the actual placed file's extension), was: " + image.getImageUrl());
            assertTrue(!image.getImageUrl().toLowerCase().contains("cloudinary"),
                    "seed post image must never reference Cloudinary, was: " + image.getImageUrl());
        }
    }

    // Self-healing verification: an already-seeded Demo dataset whose image URLs are stale (e.g.
    // left over from before the .jpg -> .jpeg/.png extension fix, or an even older Cloudinary
    // URL) must be corrected on the next seedIfNeeded() call - without duplicating any seed row
    // and without touching friendships/comments/likes/messages/notifications/post text at all.
    @Test
    void seedIfNeeded_alreadySeededWithStaleImageUrls_correctsThemWithoutDuplicatingOrDestroyingOtherData() {
        demoDataSeederService.seedIfNeeded();
        List<User> seedUsers = userRepository.findAllByUserNameIn(SEED_USERNAMES);
        assertEquals(4, seedUsers.size());

        List<Post> seedPostsBefore = seedUsers.stream()
                .flatMap(u -> postRepository.findByUserId(u.getId()).stream())
                .toList();
        List<PostImage> seedImagesBefore = seedPostsBefore.stream()
                .flatMap(p -> postImageRepository.findByPost_PostId(p.getPostId()).stream())
                .toList();
        assertEquals(5, seedImagesBefore.size());

        // Baseline of everything the sync step must NOT touch.
        int postCountBefore = seedPostsBefore.size();
        Map<Long, String> postTextByIdBefore = seedPostsBefore.stream()
                .collect(java.util.stream.Collectors.toMap(Post::getPostId, Post::getPostText));
        long commentsBefore = seedPostsBefore.stream().mapToLong(p -> commentRepository.countCommentsByPostId(p.getPostId())).sum();
        long likesBefore = seedPostsBefore.stream().mapToLong(p -> likeRepository.countLikesByPostId(p.getPostId())).sum();
        long friendshipsBefore = seedUsers.stream()
                .mapToLong(u -> friendshipRepository.findAllByUserAndStatus(u, FriendshipStatus.ACCEPTED).size())
                .sum();
        long messagesBefore = countMessages(seedUsers);
        long notificationsBefore = countNotifications(seedUsers);
        List<Long> imageRowIdsBefore = seedImagesBefore.stream().map(PostImage::getId).sorted().toList();

        // Simulate a dataset seeded before the extension fix: overwrite every image URL with a
        // stale value (an old-style Cloudinary URL for users, an old ".jpg" path for posts),
        // exactly the "old code produced these rows, current code expects different ones"
        // scenario syncImageUrls exists to repair.
        for (User user : seedUsers) {
            user.setProfilePictureUrl("https://res.cloudinary.com/dfembms4i/image/upload/stale.png");
            userRepository.save(user);
        }
        for (PostImage image : seedImagesBefore) {
            String staleUrl = image.getImageUrl().replace(".png", ".jpg");
            image.setImageUrl(staleUrl);
            postImageRepository.save(image);
        }
        userRepository.flush();

        // Recovery pass: dataset is already "fully seeded" (all 4 usernames present), so this
        // must go through syncImageUrls rather than a fresh seed/cleanup-and-reseed.
        demoDataSeederService.seedIfNeeded();

        List<User> seedUsersAfter = userRepository.findAllByUserNameIn(SEED_USERNAMES);
        assertEquals(4, seedUsersAfter.size(), "sync must never duplicate seed users");

        Map<String, String> expectedProfileImageByUsername = Map.of(
                DemoDataSeeder.DEMO_USERNAME, DemoAssets.PROFILE_DEMO_USER,
                "demo_alex", DemoAssets.PROFILE_ALEX,
                "demo_jamie", DemoAssets.PROFILE_JAMIE,
                "demo_sam", DemoAssets.PROFILE_SAM
        );
        for (User user : seedUsersAfter) {
            assertEquals(expectedProfileImageByUsername.get(user.getUserName()), user.getProfilePictureUrl(),
                    "stale profilePictureUrl for " + user.getUserName() + " must be corrected back to the current DemoAssets constant");
        }

        List<Post> seedPostsAfter = seedUsersAfter.stream()
                .flatMap(u -> postRepository.findByUserId(u.getId()).stream())
                .toList();
        assertEquals(postCountBefore, seedPostsAfter.size(), "sync must never create or delete a post");
        for (Post post : seedPostsAfter) {
            assertEquals(postTextByIdBefore.get(post.getPostId()), post.getPostText(),
                    "sync must never touch post text (post " + post.getPostId() + ")");
        }

        List<PostImage> seedImagesAfter = seedPostsAfter.stream()
                .flatMap(p -> postImageRepository.findByPost_PostId(p.getPostId()).stream())
                .toList();
        assertEquals(5, seedImagesAfter.size(), "sync must never create or delete a PostImage row");
        assertEquals(imageRowIdsBefore, seedImagesAfter.stream().map(PostImage::getId).sorted().toList(),
                "sync must correct the existing PostImage rows in place, never delete+recreate them");
        for (PostImage image : seedImagesAfter) {
            assertTrue(image.getImageUrl().startsWith("/demo-assets/posts/") && image.getImageUrl().endsWith(".png"),
                    "stale post image URL must be corrected back to the current DemoAssets constant, was: " + image.getImageUrl());
        }

        long commentsAfter = seedPostsAfter.stream().mapToLong(p -> commentRepository.countCommentsByPostId(p.getPostId())).sum();
        long likesAfter = seedPostsAfter.stream().mapToLong(p -> likeRepository.countLikesByPostId(p.getPostId())).sum();
        long friendshipsAfter = seedUsersAfter.stream()
                .mapToLong(u -> friendshipRepository.findAllByUserAndStatus(u, FriendshipStatus.ACCEPTED).size())
                .sum();
        assertEquals(commentsBefore, commentsAfter, "sync must never touch comments");
        assertEquals(likesBefore, likesAfter, "sync must never touch likes");
        assertEquals(friendshipsBefore, friendshipsAfter, "sync must never touch friendships");
        assertEquals(messagesBefore, countMessages(seedUsersAfter), "sync must never touch messages");
        assertEquals(notificationsBefore, countNotifications(seedUsersAfter), "sync must never touch notifications");
    }

    // A normal (non-demo) user's own profile picture and bio must never be read or written by the
    // Demo seeding/sync step, regardless of what values they happen to hold.
    @Test
    void seedIfNeeded_neverTouchesANormalUsersProfilePictureOrBio() {
        User normalUser = new User();
        normalUser.setUserName("regular_jane_" + System.nanoTime());
        normalUser.setEmail("regular_jane_" + System.nanoTime() + "@example.com");
        normalUser.setName("Jane");
        normalUser.setLastname("Doe");
        normalUser.setGender(Gender.FEMALE);
        normalUser.setBirthDate(LocalDate.of(1990, 1, 1));
        normalUser.setPassword("irrelevant-for-this-test");
        normalUser.setDemo(false);
        normalUser.setProfilePictureUrl("https://res.cloudinary.com/dfembms4i/image/upload/some-real-user-photo.png");
        normalUser.setBio("Just a regular person's real bio.");
        User saved = userRepository.save(normalUser);

        demoDataSeederService.seedIfNeeded();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertEquals("https://res.cloudinary.com/dfembms4i/image/upload/some-real-user-photo.png", reloaded.getProfilePictureUrl(),
                "a normal user's profilePictureUrl must be completely untouched by Demo seeding/sync");
        assertEquals("Just a regular person's real bio.", reloaded.getBio(),
                "a normal user's bio must be completely untouched by Demo seeding/sync");
        assertTrue(!reloaded.isDemo(), "a normal user must never be flagged isDemo by seeding/sync");
    }

    // Demo friend/bio content (requirement #3/#5): a fresh seed must produce exactly the intended
    // bios for all 4 seed users, including demo_user's own explanatory bio.
    @Test
    void seedIfNeeded_freshSeed_producesTheIntendedBios() {
        demoDataSeederService.seedIfNeeded();
        List<User> seedUsers = userRepository.findAllByUserNameIn(SEED_USERNAMES);
        assertEquals(4, seedUsers.size());

        Map<String, String> expectedBioByUsername = Map.of(
                DemoDataSeeder.DEMO_USERNAME, "This is a Demo account created to help you explore how Faceboard works. " +
                        "To use interactive features and create your own content, please register for an account.",
                "demo_alex", "Frontend developer, night photographer, and coffee enthusiast.",
                "demo_jamie", "Software engineer who loves travel, good coffee, and building side projects.",
                "demo_sam", "Weekend hiker, tech enthusiast, and always looking for the next great view."
        );
        for (User user : seedUsers) {
            assertEquals(expectedBioByUsername.get(user.getUserName()), user.getBio(),
                    "unexpected bio for " + user.getUserName());
        }
    }

    // Bio self-healing: an already-seeded dataset with a stale/empty bio (e.g. left over from
    // before bios were added to the seeder at all) must have its bio corrected on the next
    // seedIfNeeded() call, without touching profile pictures, posts, or anything else.
    @Test
    void seedIfNeeded_alreadySeededWithStaleOrEmptyBios_correctsThemWithoutTouchingOtherFields() {
        demoDataSeederService.seedIfNeeded();
        List<User> seedUsers = userRepository.findAllByUserNameIn(SEED_USERNAMES);
        Map<String, String> profileImageByIdBefore = seedUsers.stream()
                .collect(java.util.stream.Collectors.toMap(User::getUserName, User::getProfilePictureUrl));

        for (User user : seedUsers) {
            user.setBio("demo_alex".equals(user.getUserName()) ? "" : null);
            userRepository.save(user);
        }
        userRepository.flush();

        demoDataSeederService.seedIfNeeded();

        List<User> seedUsersAfter = userRepository.findAllByUserNameIn(SEED_USERNAMES);
        assertEquals(4, seedUsersAfter.size(), "bio sync must never duplicate seed users");
        for (User user : seedUsersAfter) {
            assertTrue(user.getBio() != null && !user.getBio().isBlank(),
                    user.getUserName() + "'s stale/empty bio must be corrected");
            assertEquals(profileImageByIdBefore.get(user.getUserName()), user.getProfilePictureUrl(),
                    "bio sync must never touch profilePictureUrl for " + user.getUserName());
        }
    }

    // M-IMG1 root-cause reproduction and fix verification (requirements #1/#2): simulates the
    // exact shape of an older Demo dataset - Alex's city-lights post exists but with BOTH a
    // stale caption and a stale image (i.e. a caption-text-keyed sync could never find it again),
    // and Sam's/Jamie's newer intended image posts don't exist at all yet (as if seeded before
    // those posts/captions were introduced). A single seedIfNeeded() call must correct Alex's
    // post in place (not duplicate it) and create the two missing posts - ending with exactly the
    // 5 intended (author, caption, image) triples reachable via findByUserId, with no impact on
    // any other seed content.
    @Test
    void seedIfNeeded_staleCaptionAndMissingIntendedPosts_correctsInPlaceAndCreatesMissingOnes() {
        demoDataSeederService.seedIfNeeded();
        List<User> seedUsers = userRepository.findAllByUserNameIn(SEED_USERNAMES);
        User alex = seedUsers.stream().filter(u -> "demo_alex".equals(u.getUserName())).findFirst().orElseThrow();
        User sam = seedUsers.stream().filter(u -> "demo_sam".equals(u.getUserName())).findFirst().orElseThrow();
        User jamie = seedUsers.stream().filter(u -> "demo_jamie".equals(u.getUserName())).findFirst().orElseThrow();

        long friendshipsBefore = seedUsers.stream()
                .mapToLong(u -> friendshipRepository.findAllByUserAndStatus(u, FriendshipStatus.ACCEPTED).size())
                .sum();
        long messagesBefore = countMessages(seedUsers);
        long notificationsBefore = countNotifications(seedUsers);

        // Rewrite Alex's city-lights post back to an older caption+image (simulating a dataset
        // seeded before this session's caption/image change), and delete Sam's hiking post and
        // Jamie's travel-street post entirely (simulating a dataset seeded before those posts
        // existed at all).
        Post alexCityPost = postRepository.findByUserId(alex.getId()).stream()
                .filter(p -> !postImageRepository.findByPost_PostId(p.getPostId()).isEmpty())
                .findFirst().orElseThrow();
        long alexCityPostId = alexCityPost.getPostId();
        alexCityPost.setPostText("Beautiful sunset today 🌅"); // old pre-polish caption
        postRepository.save(alexCityPost);
        for (PostImage image : postImageRepository.findByPost_PostId(alexCityPostId)) {
            image.setImageUrl("https://res.cloudinary.com/dfembms4i/image/upload/v1745429024/Man1_wavqmk.png");
            postImageRepository.save(image);
        }

        // Post.comments/likes/images are all CascadeType.ALL + orphanRemoval (see Post.java),
        // mappedBy "post" - deleting the post itself cascades away only ITS OWN comments/likes/
        // image, regardless of who authored them, without needing to touch any other post's rows.
        Post samHikingPost = postRepository.findByUserId(sam.getId()).stream()
                .filter(p -> "Chasing trail views this weekend 🥾".equals(p.getPostText()))
                .findFirst().orElseThrow();
        postRepository.delete(samHikingPost);

        Post jamieTravelPost = postRepository.findByUserId(jamie.getId()).stream()
                .filter(p -> "Wandering new streets on my trip ✈️".equals(p.getPostText()))
                .findFirst().orElseThrow();
        postRepository.delete(jamieTravelPost);
        postRepository.flush();

        int postCountBeforeRecovery = postRepository.findByUserId(alex.getId()).size()
                + postRepository.findByUserId(sam.getId()).size()
                + postRepository.findByUserId(jamie.getId()).size();

        // Recovery pass.
        demoDataSeederService.seedIfNeeded();

        List<User> seedUsersAfter = userRepository.findAllByUserNameIn(SEED_USERNAMES);
        assertEquals(4, seedUsersAfter.size(), "must never duplicate seed users");

        // Alex's post must be the SAME row, corrected in place - not a duplicate.
        List<Post> alexPostsAfter = postRepository.findByUserId(alex.getId());
        assertTrue(alexPostsAfter.stream().anyMatch(p -> p.getPostId() == alexCityPostId
                        && "The city lights never get old 🌃".equals(p.getPostText())),
                "Alex's stale post must be corrected in place to the current caption, same row");
        List<PostImage> alexImagesAfter = postImageRepository.findByPost_PostId(alexCityPostId);
        assertEquals(1, alexImagesAfter.size());
        assertEquals(DemoAssets.POST_CITY_NIGHT, alexImagesAfter.get(0).getImageUrl(),
                "Alex's post image must be corrected to city-night.png");

        // Sam's and Jamie's missing intended posts must now exist.
        assertTrue(postRepository.findByUserId(sam.getId()).stream()
                        .anyMatch(p -> "Chasing trail views this weekend 🥾".equals(p.getPostText())
                                && !postImageRepository.findByPost_PostId(p.getPostId()).isEmpty()),
                "Sam's hiking post must be (re)created with its image");
        assertTrue(postRepository.findByUserId(jamie.getId()).stream()
                        .anyMatch(p -> "Wandering new streets on my trip ✈️".equals(p.getPostText())
                                && !postImageRepository.findByPost_PostId(p.getPostId()).isEmpty()),
                "Jamie's travel-street post must be (re)created with its image");

        // All 5 intended image posts across all 4 users are present with correct URLs, matching
        // exactly what a fresh seed would have produced.
        List<User> allSeedUsersAfter = userRepository.findAllByUserNameIn(SEED_USERNAMES);
        long imagePostCountAfter = allSeedUsersAfter.stream()
                .flatMap(u -> postRepository.findByUserId(u.getId()).stream())
                .filter(p -> !postImageRepository.findByPost_PostId(p.getPostId()).isEmpty())
                .count();
        assertEquals(5, imagePostCountAfter, "exactly the 5 intended image posts must exist after recovery");

        // Recreating the 2 missing posts is the only row-creation allowed; nothing else moved.
        assertEquals(postCountBeforeRecovery + 2,
                postRepository.findByUserId(alex.getId()).size() + postRepository.findByUserId(sam.getId()).size()
                        + postRepository.findByUserId(jamie.getId()).size());
        assertEquals(friendshipsBefore, allSeedUsersAfter.stream()
                        .mapToLong(u -> friendshipRepository.findAllByUserAndStatus(u, FriendshipStatus.ACCEPTED).size()).sum(),
                "friendships must be completely unaffected");
        assertEquals(messagesBefore, countMessages(allSeedUsersAfter), "messages must be completely unaffected");
        assertEquals(notificationsBefore, countNotifications(allSeedUsersAfter), "notifications must be completely unaffected");

        // Idempotency: running the recovery again must not create further duplicates.
        demoDataSeederService.seedIfNeeded();
        long imagePostCountAfterSecondRun = userRepository.findAllByUserNameIn(SEED_USERNAMES).stream()
                .flatMap(u -> postRepository.findByUserId(u.getId()).stream())
                .filter(p -> !postImageRepository.findByPost_PostId(p.getPostId()).isEmpty())
                .count();
        assertEquals(5, imagePostCountAfterSecondRun, "a second sync pass must not create any further posts");
    }

    private long countSeedPosts(List<User> seedUsers) {
        return seedUsers.stream()
                .mapToLong(u -> postRepository.findByUserId(u.getId()).size())
                .sum();
    }

    // Messages are directional (sender/receiver), so counting by sender alone (rather than
    // sender-or-receiver, which would double count each message once per participant) gives the
    // true total row count.
    private long countMessages(List<User> seedUsers) {
        User demoUser = seedUsers.stream().filter(u -> DemoDataSeeder.DEMO_USERNAME.equals(u.getUserName()))
                .findFirst().orElseThrow();
        long total = 0;
        for (User u : seedUsers) {
            if (u.getId() == demoUser.getId()) continue;
            total += messageRepository.findMessagesBetweenUsers(demoUser.getId(), u.getId(),
                    org.springframework.data.domain.PageRequest.of(0, 100)).size();
        }
        return total;
    }

    private long countNotifications(List<User> seedUsers) {
        User demoUser = seedUsers.stream().filter(u -> DemoDataSeeder.DEMO_USERNAME.equals(u.getUserName()))
                .findFirst().orElseThrow();
        return notificationRepository.findByReceiverIdOrderByCreatedAtDesc(demoUser.getId(),
                org.springframework.data.domain.PageRequest.of(0, 100)).size();
    }
}
