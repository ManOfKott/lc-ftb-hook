package dev.malik.lcftbhook.bank;

import dev.ftb.mods.ftbchunks.api.ClaimResult;
import net.minecraft.network.chat.MutableComponent;

public final class BulkInsufficientFundsClaimResult implements ClaimResult {
    public static final String RESULT_ID = "message.lc_ftb_hook.insufficient_funds_bulk";

    private final MutableComponent message;

    public BulkInsufficientFundsClaimResult(MutableComponent message) {
        this.message = message;
    }

    @Override
    public String getResultId() {
        return RESULT_ID;
    }

    @Override
    public MutableComponent getMessage() {
        return message;
    }
}
