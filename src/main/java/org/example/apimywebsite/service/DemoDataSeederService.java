package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.Friends;
import org.example.apimywebsite.api.model.Like;
import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.api.model.Notification;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.PostImage;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.enums.FriendshipStatus;
import org.example.apimywebsite.enums.Gender;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// Demo Mode dataset seeding. Split out from DemoDataSeeder (a @Configuration/CommandLineRunner
// registrar) into its own @Service specifically so @Transactional actually takes effect:
// Spring's transaction advice only runs when a @Transactional method is called through the
// bean's proxy - i.e. from a DIFFERENT bean - never via self-invocation (this.method()) inside
// the same class. DemoDataSeeder calls seedIfNeeded() on this injected bean, so the proxy is
// genuinely in the call path and the whole operation is atomic: either the complete seed
// dataset (and any partial-state cleanup) commits together, or none of it does.
@Service
public class DemoDataSeederService {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeederService.class);

    private static final List<String> SEED_USERNAMES = List.of(
            DemoDataSeeder.DEMO_USERNAME, "demo_alex", "demo_jamie", "demo_sam");

    // Reconciliation keys for syncDemoFields() below. Seed users have no other stable business
    // key to key off of besides username.
    private static final Map<String, String> USERNAME_TO_PROFILE_IMAGE = Map.of(
            DemoDataSeeder.DEMO_USERNAME, DemoAssets.PROFILE_DEMO_USER,
            "demo_alex", DemoAssets.PROFILE_ALEX,
            "demo_jamie", DemoAssets.PROFILE_JAMIE,
            "demo_sam", DemoAssets.PROFILE_SAM
    );
    private static final Map<String, String> USERNAME_TO_BIO = Map.of(
            DemoDataSeeder.DEMO_USERNAME, "This is a Demo account created to help you explore how Faceboard works. " +
                    "To use interactive features and create your own content, please register for an account.",
            "demo_alex", "Frontend developer, night photographer, and coffee enthusiast.",
            "demo_jamie", "Software engineer who loves travel, good coffee, and building side projects.",
            "demo_sam", "Weekend hiker, tech enthusiast, and always looking for the next great view."
    );

    // A seed post has no business key at all besides its caption text, and captions have already
    // changed once across seed-code revisions - keying reconciliation on exact caption text (the
    // original approach) silently stops matching a post the moment its intended caption changes,
    // which is exactly what caused M-IMG1 below. Keyed by (username, ordinal position among that
    // user's image-bearing posts) instead: robust to caption drift, and a position past the end
    // of what currently exists means "this intended post doesn't exist yet - create it" rather
    // than "nothing to sync here".
    private record ImagePostSpec(String caption, String imageUrl) {
    }

    private static final Map<String, List<ImagePostSpec>> USERNAME_TO_IMAGE_POSTS = Map.of(
            "demo_alex", List.of(
                    new ImagePostSpec("The city lights never get old 🌃", DemoAssets.POST_CITY_NIGHT)
            ),
            "demo_jamie", List.of(
                    new ImagePostSpec("Coffee and code, the perfect combo ☕", DemoAssets.POST_COFFEE_WORKSPACE),
                    new ImagePostSpec("Wandering new streets on my trip ✈️", DemoAssets.POST_TRAVEL_STREET)
            ),
            "demo_sam", List.of(
                    new ImagePostSpec("Chasing trail views this weekend 🥾", DemoAssets.POST_HIKING_VIEW)
            ),
            DemoDataSeeder.DEMO_USERNAME, List.of(
                    new ImagePostSpec("Testing out the new Faceboard features!", DemoAssets.POST_LAPTOP_PROJECT)
            )
    );

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeederService(UserRepository userRepository, FriendshipRepository friendshipRepository,
                                  PostRepository postRepository, PostImageRepository postImageRepository,
                                  CommentRepository commentRepository, LikeRepository likeRepository,
                                  MessageRepository messageRepository, NotificationRepository notificationRepository,
                                  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.postRepository = postRepository;
        this.postImageRepository = postImageRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.messageRepository = messageRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void seedIfNeeded() {
        List<User> existing = userRepository.findAllByUserNameIn(SEED_USERNAMES);
        if (existing.size() == SEED_USERNAMES.size()) {
            // Already fully seeded - still worth a cheap pass to catch a DemoAssets/bio/intended-
            // post-mapping change made after this dataset was originally seeded; see
            // syncDemoFields's own doc comment for exactly what it does and does not touch.
            syncDemoFields(existing);
            return;
        }
        if (!existing.isEmpty()) {
            // Detected an incomplete dataset (e.g. left over from an interrupted run before this
            // transactional path existed) - clean up the partial rows before re-seeding, inside
            // this same transaction, so the recovery attempt itself is all-or-nothing too: if
            // cleanup or re-seeding fails, everything rolls back to exactly the state it found.
            log.warn("Demo dataset was incomplete ({} of {} seed users present) - cleaning up before re-seeding.",
                    existing.size(), SEED_USERNAMES.size());
            cleanupPartialSeed(existing);
            // Forces the pending DELETEs to actually execute before seed() below inserts new
            // rows reusing the same unique usernames/emails - without this, the deletes and the
            // re-seed's inserts are only ordered by Hibernate's own flush heuristics, which (as
            // observed under the "identity"-strategy immediate-insert behavior these entities
            // use) can otherwise attempt the new inserts before the old rows are actually gone.
            userRepository.flush();
        }
        seed();
    }

    private void cleanupPartialSeed(List<User> partialUsers) {
        for (User user : partialUsers) {
            // Messages/notifications never cascade from anything else, and notifications can
            // reference this user's posts (via post_id) even when neither sender nor receiver is
            // this user - both must be cleaned up before the posts themselves are deleted below.
            messageRepository.deleteAllBySenderOrReceiver(user);
            notificationRepository.deleteAllInvolvingUser(user);
            // A partial user can have liked/commented on ANOTHER (still-present) seed user's
            // post - e.g. seed()'s sam likes demoUser's post - which lives outside the cascade
            // that deleting this user's own posts triggers below, so it's removed explicitly
            // first (otherwise userRepository.deleteAll below would hit a FK violation).
            likeRepository.deleteAllByUser(user);
            commentRepository.deleteAllByUser(user);
            // Post.images/likes/comments all cascade + orphanRemoval from the Post side
            // (Post.java), so deleting each of a user's own posts also removes whatever's left
            // of their own comments/likes/images on those posts. Entity-based deleteAll defers
            // the actual DELETE statements to the next flush rather than running them
            // immediately (unlike the bulk queries above) - flush() forces them to execute now,
            // before userRepository.deleteAll below, instead of relying on Hibernate's own
            // dependency-ordering heuristics across a batch of still-pending actions.
            postRepository.deleteAll(postRepository.findByUserId(user.getId()));
            postRepository.flush();
            friendshipRepository.deleteAllInvolvingUser(user);
        }
        userRepository.deleteAll(partialUsers);
    }

    // Self-healing pass for an already-seeded Demo dataset: brings each seed user's
    // profilePictureUrl and bio, plus each intended image-bearing post's caption/PostImage.
    // imageUrl, in line with the current constants/specs above - nothing else. seedIfNeeded()
    // otherwise short-circuits entirely once all 4 seed users already exist, so without this, a
    // DemoAssets/bio/caption change made after this dataset was originally seeded would never
    // reach a dataset seeded before that change.
    //
    // Deliberately narrow in what it's allowed to write: User.profilePictureUrl, User.bio,
    // Post.postText (ONLY for a post already identified as one of USERNAME_TO_IMAGE_POSTS'
    // intended image posts for that user, per the ordinal matching below - never any other
    // post's text), and PostImage.imageUrl. Friendships, comments, likes, messages,
    // notifications, and non-demo users are never read or touched here. Nothing is deleted;
    // creating a missing intended image post (via the same seedPost() helper seed() itself uses)
    // is the only row-creation this method does, and only for a user's OWN missing intended post.
    private void syncDemoFields(List<User> seedUsers) {
        for (User user : seedUsers) {
            String expectedProfileImage = USERNAME_TO_PROFILE_IMAGE.get(user.getUserName());
            String expectedBio = USERNAME_TO_BIO.get(user.getUserName());
            boolean userChanged = false;
            if (expectedProfileImage != null && !expectedProfileImage.equals(user.getProfilePictureUrl())) {
                user.setProfilePictureUrl(expectedProfileImage);
                userChanged = true;
            }
            if (expectedBio != null && !expectedBio.equals(user.getBio())) {
                user.setBio(expectedBio);
                userChanged = true;
            }
            if (userChanged) {
                userRepository.save(user);
            }

            syncImagePosts(user);
        }
    }

    // M-IMG1 fix: re-identifies each of a user's intended image posts by ORDINAL POSITION among
    // their currently-existing image-bearing posts (ascending post id = original creation order),
    // not by caption text - a caption-text match silently stops working the moment an intended
    // caption changes across seed-code revisions, which is exactly what left Alex's city-lights
    // post pointing at a stale image and left Sam's/Jamie's newer intended posts never created at
    // all in a dataset seeded before those posts/captions existed. A post at a given ordinal
    // position gets its caption and image corrected in place if either drifted from the current
    // spec; a position with nothing to correspond to yet gets a brand-new post created via the
    // same seedPost() helper seed() uses. Posts with no image at all (this user's text-only seed
    // posts) are never inspected, so their captions can never be touched by this method.
    private void syncImagePosts(User user) {
        List<ImagePostSpec> intended = USERNAME_TO_IMAGE_POSTS.get(user.getUserName());
        if (intended == null || intended.isEmpty()) return;

        List<Post> existingImagePosts = postRepository.findByUserId(user.getId()).stream()
                .filter(post -> !postImageRepository.findByPost_PostId(post.getPostId()).isEmpty())
                .sorted(Comparator.comparingLong(Post::getPostId))
                .toList();

        for (int i = 0; i < intended.size(); i++) {
            ImagePostSpec spec = intended.get(i);
            if (i >= existingImagePosts.size()) {
                seedPost(user, spec.caption(), spec.imageUrl());
                continue;
            }

            Post post = existingImagePosts.get(i);
            if (!spec.caption().equals(post.getPostText())) {
                post.setPostText(spec.caption());
                postRepository.save(post);
            }
            for (PostImage image : postImageRepository.findByPost_PostId(post.getPostId())) {
                if (!spec.imageUrl().equals(image.getImageUrl())) {
                    image.setImageUrl(spec.imageUrl());
                    postImageRepository.save(image);
                }
            }
        }
    }

    private void seed() {
        String randomPassword = passwordEncoder.encode(randomSecret());

        User demoUser = buildDemoUser(DemoDataSeeder.DEMO_USERNAME, "demo_user@faceboard.demo", "Demo", "User", Gender.FEMALE, randomPassword,
                DemoAssets.PROFILE_DEMO_USER, USERNAME_TO_BIO.get(DemoDataSeeder.DEMO_USERNAME));
        User alex = buildDemoUser("demo_alex", "demo_alex@faceboard.demo", "Alex", "Rivera", Gender.MALE, randomPassword,
                DemoAssets.PROFILE_ALEX, USERNAME_TO_BIO.get("demo_alex"));
        User jamie = buildDemoUser("demo_jamie", "demo_jamie@faceboard.demo", "Jamie", "Chen", Gender.FEMALE, randomPassword,
                DemoAssets.PROFILE_JAMIE, USERNAME_TO_BIO.get("demo_jamie"));
        User sam = buildDemoUser("demo_sam", "demo_sam@faceboard.demo", "Sam", "Okafor", Gender.MALE, randomPassword,
                DemoAssets.PROFILE_SAM, USERNAME_TO_BIO.get("demo_sam"));

        userRepository.save(demoUser);
        userRepository.save(alex);
        userRepository.save(jamie);
        userRepository.save(sam);

        // Full mesh: every seed user is friends with every other, so demo_user's Chat sidebar
        // (built from its own friendsList) has a conversation with all three, not just two.
        befriend(demoUser, alex);
        befriend(demoUser, jamie);
        befriend(demoUser, sam);
        befriend(alex, jamie);
        befriend(alex, sam);
        befriend(jamie, sam);

        // Mixture of text-only and single-image posts, per the "realistic portfolio showcase"
        // request - image posts use the local /demo-assets/posts/... paths from DemoAssets (see
        // that class's javadoc for why these never trigger a Cloudinary or other external
        // request), never Cloudinary or any remote host.
        Post p1 = seedPost(alex, "Just set up my profile on Faceboard!", null);
        Post p2 = seedPost(jamie, "Loving this demo feed so far.", null);
        Post p3 = seedPost(demoUser, "Hi, I'm the Demo account - feel free to look around!", null);
        Post p4 = seedPost(sam, "Another day, another seeded post.", null);
        Post p5 = seedPost(alex, "The city lights never get old 🌃", DemoAssets.POST_CITY_NIGHT);
        Post p6 = seedPost(jamie, "Coffee and code, the perfect combo ☕", DemoAssets.POST_COFFEE_WORKSPACE);
        Post p7 = seedPost(demoUser, "Testing out the new Faceboard features!", DemoAssets.POST_LAPTOP_PROJECT);
        Post p8 = seedPost(sam, "Weekend vibes 🎉", null);
        Post p9 = seedPost(sam, "Chasing trail views this weekend 🥾", DemoAssets.POST_HIKING_VIEW);
        Post p10 = seedPost(jamie, "Wandering new streets on my trip ✈️", DemoAssets.POST_TRAVEL_STREET);

        seedComment(jamie, p1, "Welcome!");
        seedComment(demoUser, p1, "Nice to have you here.");
        seedComment(alex, p2, "Glad you like it!");
        seedComment(sam, p5, "Wow, stunning!");
        seedComment(demoUser, p6, "Same energy, love it.");
        seedComment(alex, p7, "Looks great!");
        seedComment(jamie, p8, "Have a good one!");
        seedComment(alex, p9, "Nice trail!");

        seedLike(demoUser, p1);
        seedLike(alex, p2);
        seedLike(jamie, p3);
        seedLike(sam, p3);
        seedLike(sam, p4);
        seedLike(jamie, p5);
        seedLike(demoUser, p5);
        seedLike(alex, p6);
        seedLike(sam, p7);
        seedLike(demoUser, p8);
        seedLike(demoUser, p9);
        seedLike(sam, p10);

        // Seeded chat history so Chat isn't empty the moment Demo Mode is opened - one
        // conversation per demo_user friend, oldest message first, last incoming message left
        // unread on each thread for a realistic "you have something to catch up on" feel.
        seedConversation(alex, demoUser, List.of(
                new SeedMessage(alex, demoUser, "Hey! How's it going?", 40, true),
                new SeedMessage(demoUser, alex, "Great, just exploring the app!", 35, true),
                new SeedMessage(alex, demoUser, "Nice, let me know what you think!", 30, false)
        ));
        seedConversation(jamie, demoUser, List.of(
                new SeedMessage(jamie, demoUser, "Hi! Did you see my new post?", 20, true),
                new SeedMessage(demoUser, jamie, "Yes, looks awesome!", 15, true)
        ));
        seedConversation(sam, demoUser, List.of(
                new SeedMessage(sam, demoUser, "Welcome to Faceboard!", 10, true),
                new SeedMessage(demoUser, sam, "Thanks! Excited to be here.", 5, false)
        ));

        // Seeded notifications for demo_user's own inbox - the three example types requested,
        // reusing the exact content format each real code path already generates (see
        // LikeService/CommentService/FriendshipService), so they read as authentic rather than
        // obviously synthetic.
        seedNotification(demoUser, alex, "LIKE", alex.getFullName() + " Liked Your Post", p3, false);
        seedNotification(demoUser, jamie, "COMMENT", jamie.getFullName() + " Comment on your post", p3, false);
        seedNotification(demoUser, sam, "FRIEND_ACCEPTED", sam.getFullName() + " Accept You Friend Request", null, true);

        log.info("Demo Mode dataset seeded (4 users, 10 posts).");
    }

    private User buildDemoUser(String username, String email, String name, String lastname, Gender gender, String password, String profilePictureUrl, String bio) {
        User user = new User();
        user.setUserName(username);
        user.setEmail(email);
        user.setName(name);
        user.setLastname(lastname);
        user.setGender(gender);
        user.setBirthDate(LocalDate.of(1995, 1, 1));
        user.setPassword(password);
        user.setDemo(true);
        // Local frontend asset path (DemoAssets), not a Cloudinary/remote URL - see that class's
        // javadoc. Real users are entirely unaffected: their own registration/default-avatar path
        // (Constants.DEFAULT_PROFILE_PICTURE_MALE/FEMALE) is untouched.
        user.setProfilePictureUrl(profilePictureUrl);
        user.setBio(bio);
        return user;
    }

    private void befriend(User a, User b) {
        friendshipRepository.save(new Friends(a, b, FriendshipStatus.ACCEPTED, LocalDateTime.now()));
        friendshipRepository.save(new Friends(b, a, FriendshipStatus.ACCEPTED, LocalDateTime.now()));
    }

    private Post seedPost(User author, String text, String imageUrl) {
        Post post = new Post();
        post.setUser(author);
        post.setPostText(text);
        post.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        Post saved = postRepository.save(post);
        if (imageUrl != null) {
            // Saved directly via PostImageRepository (matching PostService.addPost's own
            // pattern) rather than via Post's mappedBy collection, avoiding any ambiguity about
            // whether cascade-through-dirty-checking fires before this method returns.
            PostImage image = new PostImage();
            image.setPost(saved);
            image.setImageUrl(imageUrl);
            postImageRepository.save(image);
        }
        return saved;
    }

    private void seedComment(User author, Post post, String text) {
        Comment comment = new Comment();
        comment.setUser(author);
        comment.setPost(post);
        comment.setText(text);
        comment.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        commentRepository.save(comment);
    }

    private void seedLike(User author, Post post) {
        Like like = new Like();
        like.setUser(author);
        like.setPost(post);
        like.setCreatedAt(LocalDateTime.now());
        likeRepository.save(like);
    }

    private record SeedMessage(User sender, User receiver, String text, int minutesAgo, boolean read) {
    }

    private void seedConversation(User a, User b, List<SeedMessage> messages) {
        for (SeedMessage sm : messages) {
            Message message = new Message();
            message.setSender(sm.sender());
            message.setReceiver(sm.receiver());
            message.setMessage(sm.text());
            message.setSentTime(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(sm.minutesAgo()));
            message.setRead(sm.read());
            messageRepository.save(message);
        }
    }

    private void seedNotification(User receiver, User sender, String type, String content, Post post, boolean read) {
        Notification notification = new Notification(receiver, sender, type, content, post);
        notification.setRead(read);
        notificationRepository.save(notification);
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
