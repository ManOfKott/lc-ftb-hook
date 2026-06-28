package dev.malik.lcftbhook;

import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates that required companion mods match the versions this hook was built against.
 */
public final class ModCompatibility {
    /** Must match {@code ftb_chunks_version} in gradle.properties. */
    public static final String REQUIRED_FTB_CHUNKS_VERSION = "2101.1.20";
    /** Must match {@code lightmanscurrency_version} in gradle.properties. */
    public static final String REQUIRED_LIGHTMANS_CURRENCY_VERSION = "1.21-2.3.0.4g";

    private ModCompatibility() {
    }

    public static void validateOrThrow() {
        List<String> errors = new ArrayList<>();
        requireExactVersion("ftbchunks", "FTB Chunks", REQUIRED_FTB_CHUNKS_VERSION, errors);
        requireExactVersion("lightmanscurrency", "Lightman's Currency", REQUIRED_LIGHTMANS_CURRENCY_VERSION, errors);

        if (errors.isEmpty()) {
            return;
        }

        for (String error : errors) {
            LCFtbHook.LOGGER.error(error);
        }

        throw new IllegalStateException(
                "LC FTB Hook cannot load with incompatible mod versions. "
                        + "Expected FTB Chunks "
                        + REQUIRED_FTB_CHUNKS_VERSION
                        + " and Lightman's Currency "
                        + REQUIRED_LIGHTMANS_CURRENCY_VERSION
                        + ". Details: "
                        + String.join(" | ", errors)
        );
    }

    private static void requireExactVersion(
            String modId,
            String displayName,
            String requiredVersion,
            List<String> errorsOut
    ) {
        Optional<String> installed = ModList.get()
                .getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString());
        if (installed.isEmpty()) {
            errorsOut.add(displayName + " (" + modId + ") is missing.");
            return;
        }

        if (!requiredVersion.equals(installed.get())) {
            errorsOut.add(displayName + " version mismatch: required " + requiredVersion + ", found " + installed.get() + ".");
        }
    }
}
