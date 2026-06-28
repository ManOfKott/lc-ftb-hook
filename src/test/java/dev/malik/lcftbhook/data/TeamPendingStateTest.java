package dev.malik.lcftbhook.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamPendingStateTest {

    private static final UUID TEAM_A = UUID.randomUUID();
    private static final UUID TEAM_B = UUID.randomUUID();
    private static final String CHUNK_1 = "overworld:0:0";
    private static final String CHUNK_2 = "overworld:1:0";
    private static final String PROP_PVP = "allow_pvp";

    @Test
    void freshState_isEmpty() {
        assertTrue(new TeamPendingState().isEmpty());
    }

    @Test
    void afterAddingWarDeclare_notEmpty() {
        assertFalse(new TeamPendingState().withPendingWarDeclare(TEAM_A).isEmpty());
    }

    @Test
    void afterAddingAndRemovingWarDeclare_isEmpty() {
        TeamPendingState s = new TeamPendingState()
                .withPendingWarDeclare(TEAM_A)
                .withoutPendingWarDeclare(TEAM_A);
        assertTrue(s.isEmpty());
    }

    @Test
    void withPendingWarDeclare_removesFromWarEnds() {
        TeamPendingState s = new TeamPendingState()
                .withPendingWarEnd(TEAM_A)
                .withPendingWarDeclare(TEAM_A);
        assertTrue(s.isPendingWarDeclare(TEAM_A));
        assertFalse(s.isPendingWarEnd(TEAM_A));
    }

    @Test
    void withPendingWarEnd_removesFromWarDeclares() {
        TeamPendingState s = new TeamPendingState()
                .withPendingWarDeclare(TEAM_A)
                .withPendingWarEnd(TEAM_A);
        assertTrue(s.isPendingWarEnd(TEAM_A));
        assertFalse(s.isPendingWarDeclare(TEAM_A));
    }

    @Test
    void withPendingLandChunk_removesFromBuildPending() {
        TeamPendingState s = new TeamPendingState()
                .withPendingBuildChunk(CHUNK_1)
                .withPendingLandChunk(CHUNK_1);
        assertTrue(s.isPendingLandChunk(CHUNK_1));
        assertFalse(s.isPendingBuildChunk(CHUNK_1));
    }

    @Test
    void withPendingBuildChunk_removesFromLandPending() {
        TeamPendingState s = new TeamPendingState()
                .withPendingLandChunk(CHUNK_1)
                .withPendingBuildChunk(CHUNK_1);
        assertTrue(s.isPendingBuildChunk(CHUNK_1));
        assertFalse(s.isPendingLandChunk(CHUNK_1));
    }

    @Test
    void withPendingForceLoad_removesFromForceUnloads() {
        TeamPendingState s = new TeamPendingState()
                .withPendingForceUnload(CHUNK_1)
                .withPendingForceLoad(CHUNK_1);
        assertTrue(s.isPendingForceLoad(CHUNK_1));
        assertFalse(s.isPendingForceUnload(CHUNK_1));
    }

    @Test
    void withPendingForceUnload_removesFromForceLoads() {
        TeamPendingState s = new TeamPendingState()
                .withPendingForceLoad(CHUNK_1)
                .withPendingForceUnload(CHUNK_1);
        assertTrue(s.isPendingForceUnload(CHUNK_1));
        assertFalse(s.isPendingForceLoad(CHUNK_1));
    }

    @Test
    void withoutWarReferences_removesBothDeclareAndEnd() {
        TeamPendingState s = new TeamPendingState()
                .withPendingWarDeclare(TEAM_A)
                .withPendingWarEnd(TEAM_B)
                .withPendingWarDeclare(TEAM_B)  // overrides end for B
                .withoutWarReferences(TEAM_B);
        assertFalse(s.isPendingWarDeclare(TEAM_B));
        assertFalse(s.isPendingWarEnd(TEAM_B));
        assertTrue(s.isPendingWarDeclare(TEAM_A));
    }

    @Test
    void withoutWarReferences_noOpWhenAbsent() {
        TeamPendingState original = new TeamPendingState().withPendingWarDeclare(TEAM_A);
        TeamPendingState same = original.withoutWarReferences(TEAM_B);
        assertSame(original, same);
    }

    @Test
    void withPendingProperty_thenWithout_isEmpty() {
        TeamPendingState s = new TeamPendingState()
                .withPendingProperty(PROP_PVP, "false")
                .withoutPendingProperty(PROP_PVP);
        assertTrue(s.isEmpty());
        assertFalse(s.hasPendingProperty(PROP_PVP));
    }

    @Test
    void withoutPendingProperty_noOpWhenAbsent() {
        TeamPendingState original = new TeamPendingState();
        assertSame(original, original.withoutPendingProperty(PROP_PVP));
    }

    @Test
    void copy_isIndependentOfOriginal() {
        TeamPendingState original = new TeamPendingState().withPendingWarDeclare(TEAM_A);
        TeamPendingState copy = original.copy();
        TeamPendingState extended = copy.withPendingWarDeclare(TEAM_B);

        assertFalse(original.isPendingWarDeclare(TEAM_B), "Original must not be mutated");
        assertTrue(extended.isPendingWarDeclare(TEAM_B));
        assertTrue(extended.isPendingWarDeclare(TEAM_A));
    }

    @Test
    void cleared_returnsEmptyState() {
        TeamPendingState s = new TeamPendingState()
                .withPendingWarDeclare(TEAM_A)
                .withPendingForceLoad(CHUNK_1)
                .withPendingProperty(PROP_PVP, "false")
                .cleared();
        assertTrue(s.isEmpty());
    }

    @Test
    void multipleChunks_dontInterfere() {
        TeamPendingState s = new TeamPendingState()
                .withPendingForceLoad(CHUNK_1)
                .withPendingForceLoad(CHUNK_2);
        assertTrue(s.isPendingForceLoad(CHUNK_1));
        assertTrue(s.isPendingForceLoad(CHUNK_2));

        s = s.withoutPendingForceLoad(CHUNK_1);
        assertFalse(s.isPendingForceLoad(CHUNK_1));
        assertTrue(s.isPendingForceLoad(CHUNK_2));
    }

    @Test
    void pendingProperties_returnsUnmodifiableView() {
        TeamPendingState s = new TeamPendingState().withPendingProperty(PROP_PVP, "false");
        assertThrows(UnsupportedOperationException.class,
                () -> s.pendingProperties().put("x", "y"));
    }

    @Test
    void pendingWarDeclares_returnsUnmodifiableView() {
        TeamPendingState s = new TeamPendingState().withPendingWarDeclare(TEAM_A);
        assertThrows(UnsupportedOperationException.class,
                () -> s.pendingWarDeclares().add(UUID.randomUUID()));
    }
}
