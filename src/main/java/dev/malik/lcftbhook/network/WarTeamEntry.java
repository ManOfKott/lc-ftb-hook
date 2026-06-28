package dev.malik.lcftbhook.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record WarTeamEntry(
        UUID teamId,
        String displayName,
        long targetBaseUpkeepCopper,
        long warCostCopper,
        WarEntryStatus status,
        boolean opponentPendingDeclareOnViewer,
        boolean blockEditProtected,
        boolean explosionProtected,
        boolean pvpProtected
) {
    public WarTeamEntry(
            UUID teamId,
            String displayName,
            long targetBaseUpkeepCopper,
            long warCostCopper
    ) {
        this(
                teamId,
                displayName,
                targetBaseUpkeepCopper,
                warCostCopper,
                WarEntryStatus.ACTIVE,
                false,
                true,
                true,
                true
        );
    }

    public boolean isPending() {
        return status.isPending();
    }

    public boolean hasWarVulnerability() {
        return !blockEditProtected || !explosionProtected || !pvpProtected;
    }

    public static final StreamCodec<FriendlyByteBuf, WarTeamEntry> STREAM_CODEC = StreamCodec.of(
            (buffer, entry) -> {
                buffer.writeUUID(entry.teamId);
                buffer.writeUtf(entry.displayName);
                buffer.writeVarLong(entry.targetBaseUpkeepCopper);
                buffer.writeVarLong(entry.warCostCopper);
                buffer.writeVarInt(entry.status.id());
                buffer.writeBoolean(entry.opponentPendingDeclareOnViewer);
                buffer.writeBoolean(entry.blockEditProtected);
                buffer.writeBoolean(entry.explosionProtected);
                buffer.writeBoolean(entry.pvpProtected);
            },
            buffer -> new WarTeamEntry(
                    buffer.readUUID(),
                    buffer.readUtf(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    WarEntryStatus.fromId(buffer.readVarInt()),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            )
    );
}
