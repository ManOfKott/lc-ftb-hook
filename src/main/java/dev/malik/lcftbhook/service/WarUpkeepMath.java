package dev.malik.lcftbhook.service;



import dev.malik.lcftbhook.config.LCFtbHookConfig;



/**

 * War upkeep uses a geometric series: for base upkeep {@code b}, {@code k} incoming

 * war declarers, and exponent {@code l}, incoming war terms are

 * {@code b * l^n} for {@code n = 0..k-1}. Outgoing wars use the same

 * {@code targetBase * l^n} with {@code n} starting at 0 for the first declared war.

 * Total upkeep is {@code base + incoming + outgoing}.

 */

public final class WarUpkeepMath {

    private WarUpkeepMath() {

    }



    public static double warExponent() {

        return LCFtbHookConfig.SERVER.warCostMultiplier.get();

    }



    /** {@code sum(i=0..k) l^i} */

    public static double geometricFactor(int k, double l) {

        if (k < 0) {

            return 1.0D;

        }

        if (l == 1.0D) {

            return k + 1.0D;

        }

        return (Math.pow(l, k + 1) - 1.0D) / (l - 1.0D);

    }



    /** Incoming war copper for {@code k} declarers: {@code b * sum(i=0..k-1) l^i}. */

    public static long incomingWarSurchargeCopper(long baseCopper, int incomingWarCount) {

        return sumOrdinalIncomingTerms(baseCopper, incomingWarCount);

    }



    /** Copper for war term {@code n} (0-based): {@code base * l^n}. The first war uses {@code n = 0} (multiplier 1). */

    public static long ordinalWarTermCopper(long baseCopper, int n, double l) {

        if (baseCopper <= 0L || n < 0) {

            return 0L;

        }

        double result = baseCopper * Math.pow(l, n);

        if (!Double.isFinite(result) || result >= Long.MAX_VALUE) {

            return Long.MAX_VALUE;

        }

        return (long) Math.floor(result);

    }



    /** Outgoing war term at 0-based index {@code n}. */

    public static long outgoingWarTermCopper(long targetBaseCopper, int n) {

        return ordinalWarTermCopper(targetBaseCopper, n, warExponent());

    }



    /**

     * Total protection+incoming multiplier on base upkeep for {@code k} incoming wars:

     * {@code 1 + sum(i=0..k-1) l^i}.

     */

    public static double totalUpkeepFactor(int incomingWarCount, double l) {

        if (incomingWarCount <= 0) {

            return 1.0D;

        }

        return 1.0D + geometricFactor(incomingWarCount - 1, l);

    }



    public static String formatGeometricFactor(int k) {

        return formatGeometricFactor(k, warExponent());

    }



    public static String formatGeometricFactor(int k, double l) {

        if (k <= 0) {

            return "1";

        }

        if (l == 1.0D) {

            return String.valueOf(k + 1);

        }

        return trimDouble(geometricFactor(k, l));

    }



    public static String formatTotalUpkeepFactor(int incomingWarCount) {

        return formatTotalUpkeepFactor(incomingWarCount, warExponent());

    }



    public static String formatTotalUpkeepFactor(int incomingWarCount, double l) {

        if (incomingWarCount <= 0) {

            return "1";

        }

        return trimDouble(totalUpkeepFactor(incomingWarCount, l));

    }



    public static String formatExtraFactor(int k) {

        return formatExtraFactor(k, warExponent());

    }



    public static String formatExtraFactor(int k, double l) {

        if (k <= 0) {

            return "0";

        }

        if (l == 1.0D) {

            return String.valueOf(k);

        }

        return trimDouble(geometricFactor(k - 1, l));

    }



    /** Human-readable incoming term sum {@code 1 + l + ... + l^(k-1)}. */

    public static String formatExtraTermSum(int k) {

        return formatExtraTermSum(k, warExponent());

    }



    public static String formatExtraTermSum(int k, double l) {

        if (k <= 0) {

            return "0";

        }

        StringBuilder terms = new StringBuilder();

        for (int n = 0; n < k; n++) {

            if (n > 0) {

                terms.append(" + ");

            }

            terms.append(formatTermPower(l, n));

        }

        return terms.toString();

    }



    public static long sumOrdinalIncomingTerms(long baseCopper, int incomingWarCount) {

        return sumOrdinalIncomingTerms(baseCopper, incomingWarCount, warExponent());

    }



    static long sumOrdinalIncomingTerms(long baseCopper, int incomingWarCount, double l) {

        if (incomingWarCount <= 0 || baseCopper <= 0L) {

            return 0L;

        }

        long sum = 0L;

        for (int n = 0; n < incomingWarCount; n++) {

            long term = ordinalWarTermCopper(baseCopper, n, l);

            if (term >= Long.MAX_VALUE - sum) {

                return Long.MAX_VALUE;

            }

            sum += term;

        }

        return sum;

    }



    private static String formatTermPower(double l, int n) {

        if (n == 0) {

            return "1";

        }

        if (l == 1.0D) {

            return "1";

        }

        if (n == 1) {

            return trimDouble(l);

        }

        if (l == 2.0D && n <= 4) {

            return String.valueOf((long) Math.pow(2, n));

        }

        return trimDouble(Math.pow(l, n)) + '^' + n;

    }



    private static String trimDouble(double value) {

        if (Math.rint(value) == value) {

            return String.valueOf((long) value);

        }

        return String.format(java.util.Locale.ROOT, "%.2f", value);

    }

}

