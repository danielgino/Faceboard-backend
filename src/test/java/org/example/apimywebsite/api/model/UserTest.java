package org.example.apimywebsite.api.model;

import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * H5: User entity unsafe @Data.
 * These tests define the safe equals/hashCode/toString contract for the JPA entity
 * regardless of which Lombok annotations (or hand-written methods) implement it.
 */
class UserTest {

    // ---- toString must never disclose secrets or traverse relationships ----

    @Test
    void toString_doesNotContainPassword() {
        User user = User.builder().id(1).userName("alice").password("s3cr3t-hash").build();

        String result = user.toString();

        assertFalse(result.contains("s3cr3t-hash"), "toString must not disclose the password value");
    }

    @Test
    void toString_doesNotIncludeRelationshipFields() {
        User user = User.builder().id(1).userName("alice").build();

        String result = user.toString();

        assertFalse(result.contains("posts"), "toString must not traverse the posts relationship");
    }

    // M-DUP2: toString_withCyclicFriendship_completesSafely was removed here - it existed to
    // prove toString() didn't recurse through the mutual User<->User `friends` @ManyToMany
    // relation. That relation no longer exists on User (removed as part of M-DUP2), so no
    // cyclic User<->User relationship remains possible on this entity at all; the guarantee the
    // test proved no longer has a code path to protect.

    // ---- equals/hashCode must use safe JPA identity semantics, not full-field structural equality ----

    @Test
    void equals_twoTransientInstancesWithDefaultId_areNotEqual() {
        User transientA = new User();
        User transientB = new User();

        assertNotEquals(transientA, transientB,
                "two distinct transient (unpersisted) User instances must never be equal");
    }

    @Test
    void equals_sameTransientInstance_isEqualToItself() {
        User transientA = new User();

        assertEquals(transientA, transientA);
    }

    @Test
    void equals_differentNonNullIds_areNotEqual() {
        User userA = User.builder().id(1).userName("alice").build();
        User userB = User.builder().id(2).userName("alice").build();

        assertNotEquals(userA, userB, "entities with different persisted ids must never be equal");
    }

    @Test
    void equals_samePersistedIdentity_isConsistentAcrossDifferentlyLoadedInstances() {
        // Simulates the same DB row loaded twice (or before/after a field mutation) - JPA identity
        // must be based on id alone, not on the current in-memory field values.
        User loadedInstance1 = User.builder().id(5).userName("alice").bio("old bio").build();
        User loadedInstance2 = User.builder().id(5).userName("alice-renamed").bio("new bio").build();

        assertEquals(loadedInstance1, loadedInstance2,
                "same persisted id must compare equal even if other mutable fields currently differ");
        assertEquals(loadedInstance1.hashCode(), loadedInstance2.hashCode());
    }

    @Test
    void hashCode_staysStableAfterIdIsAssignedPostConstruction() {
        // Simulates inserting a transient entity into a hash-based collection before Hibernate
        // assigns the generated id, then the id being assigned (as happens after save()).
        User user = new User();
        Set<User> set = new HashSet<>();
        set.add(user);
        int hashBeforeIdAssigned = user.hashCode();

        user.setId(42);
        int hashAfterIdAssigned = user.hashCode();

        assertEquals(hashBeforeIdAssigned, hashAfterIdAssigned,
                "hashCode must not change when the generated id is assigned after construction");
        assertTrue(set.contains(user), "entity must remain findable in a HashSet after id assignment");
    }

    // ---- Hibernate proxy compatibility ----
    //
    // FaithfulProxyFixture mirrors what a real Hibernate (ByteBuddy) proxy shell looks like:
    // it is a distinct runtime subclass of User, and its identifier is served exclusively
    // through the LazyInitializer - exactly how Hibernate's own generated identifier-getter
    // override behaves - rather than being copied into the inherited `id` field. A genuine
    // uninitialized proxy shell never has that field populated, so a fixture that copies the id
    // into the field (as an earlier version of this test did via setId()) cannot reveal an
    // equals()/hashCode() implementation that only works because it happens to read that field.

    private static final class FaithfulProxyFixture extends User implements HibernateProxy {
        private final LazyInitializer lazyInitializer;

        FaithfulProxyFixture(int identifier) {
            this.lazyInitializer = mock(LazyInitializer.class);
            when(lazyInitializer.getPersistentClass()).thenAnswer(inv -> User.class);
            when(lazyInitializer.getIdentifier()).thenReturn(identifier);
            when(lazyInitializer.isUninitialized()).thenReturn(true);
        }

        @Override
        public LazyInitializer getHibernateLazyInitializer() {
            return lazyInitializer;
        }

        @Override
        public Object writeReplace() {
            return this;
        }

        // Mirrors Hibernate's own generated proxy: the identifier getter is specifically
        // answered from the LazyInitializer, without touching the inherited (unset) entity
        // field or initializing/fetching the real target.
        @Override
        public int getId() {
            return lazyInitializer.getIdentifier() == null ? 0 : (Integer) lazyInitializer.getIdentifier();
        }
    }

    @Test
    void equals_realUserEqualsProxy_forSamePersistedId() {
        User realUser = User.builder().id(7).userName("alice").build();
        FaithfulProxyFixture proxy = new FaithfulProxyFixture(7);

        assertEquals(realUser, proxy, "real.equals(proxy) must hold for the same persisted id");
    }

    @Test
    void equals_proxyEqualsRealUser_forSamePersistedId() {
        User realUser = User.builder().id(7).userName("alice").build();
        FaithfulProxyFixture proxy = new FaithfulProxyFixture(7);

        assertEquals(proxy, realUser, "proxy.equals(real) must hold for the same persisted id");
    }

    @Test
    void equals_isSymmetric_betweenRealUserAndProxy() {
        User realUser = User.builder().id(7).userName("alice").build();
        FaithfulProxyFixture proxy = new FaithfulProxyFixture(7);

        assertTrue(realUser.equals(proxy) && proxy.equals(realUser),
                "equals must be symmetric in both directions for the real/proxy pair");
    }

    @Test
    void hashCode_isEqualForRealUserAndProxy_representingSameId() {
        User realUser = User.builder().id(7).userName("alice").build();
        FaithfulProxyFixture proxy = new FaithfulProxyFixture(7);

        assertEquals(realUser.hashCode(), proxy.hashCode(),
                "equal representations of the same persisted id must produce identical hash codes");
    }

    @Test
    void hashSet_containingRealUser_locatesProxyForSameId() {
        User realUser = User.builder().id(7).userName("alice").build();
        FaithfulProxyFixture proxy = new FaithfulProxyFixture(7);
        Set<User> set = new HashSet<>();
        set.add(realUser);

        assertTrue(set.contains(proxy), "a HashSet keyed by the real entity must locate the proxy for the same id");
    }

    @Test
    void hashSet_containingProxy_locatesRealUserForSameId() {
        User realUser = User.builder().id(7).userName("alice").build();
        FaithfulProxyFixture proxy = new FaithfulProxyFixture(7);
        Set<User> set = new HashSet<>();
        set.add(proxy);

        assertTrue(set.contains(realUser), "a HashSet keyed by the proxy must locate the real entity for the same id");
    }

    @Test
    void equals_doesNotRequireRelationshipsToBeInitialized() {
        // Simulates comparing against an uninitialized proxy target whose lazy relationships
        // were never populated - equals/hashCode must never dereference posts.
        User realUser = User.builder().id(9).userName("alice").build();
        realUser.setPosts(null);
        FaithfulProxyFixture proxy = new FaithfulProxyFixture(9);
        proxy.setPosts(null);

        assertDoesNotThrow(() -> {
            assertEquals(realUser, proxy);
            realUser.hashCode();
            proxy.hashCode();
        });
    }
}
