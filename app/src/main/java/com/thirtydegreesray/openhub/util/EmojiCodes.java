package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A small table of the most used GitHub emoji shortcodes (same names as on github.com), for
 * text rendered on the phone. Names that are not in the table stay as they are.
 */
public final class EmojiCodes {

    private static final String[] TABLE = {
            "+1", "\uD83D\uDC4D", "thumbsup", "\uD83D\uDC4D", "-1", "\uD83D\uDC4E", "thumbsdown", "\uD83D\uDC4E",
            "smile", "\uD83D\uDE04", "smiley", "\uD83D\uDE03", "grinning", "\uD83D\uDE00", "grin", "\uD83D\uDE01",
            "laughing", "\uD83D\uDE06", "joy", "\uD83D\uDE02", "rofl", "\uD83E\uDD23", "sweat_smile", "\uD83D\uDE05",
            "wink", "\uD83D\uDE09", "blush", "\uD83D\uDE0A", "slightly_smiling_face", "\uD83D\uDE42",
            "upside_down_face", "\uD83D\uDE43", "innocent", "\uD83D\uDE07", "heart_eyes", "\uD83D\uDE0D",
            "kissing_heart", "\uD83D\uDE18", "yum", "\uD83D\uDE0B", "stuck_out_tongue", "\uD83D\uDE1B",
            "sunglasses", "\uD83D\uDE0E", "nerd_face", "\uD83E\uDD13", "thinking", "\uD83E\uDD14",
            "neutral_face", "\uD83D\uDE10", "expressionless", "\uD83D\uDE11", "unamused", "\uD83D\uDE12",
            "roll_eyes", "\uD83D\uDE44", "smirk", "\uD83D\uDE0F", "relieved", "\uD83D\uDE0C",
            "pensive", "\uD83D\uDE14", "confused", "\uD83D\uDE15", "worried", "\uD83D\uDE1F",
            "frowning_face", "\u2639\uFE0F", "open_mouth", "\uD83D\uDE2E", "astonished", "\uD83D\uDE32",
            "flushed", "\uD83D\uDE33", "pleading_face", "\uD83E\uDD7A", "cry", "\uD83D\uDE22", "sob", "\uD83D\uDE2D",
            "scream", "\uD83D\uDE31", "weary", "\uD83D\uDE29", "tired_face", "\uD83D\uDE2B", "sleepy", "\uD83D\uDE2A",
            "sleeping", "\uD83D\uDE34", "rage", "\uD83D\uDE21", "angry", "\uD83D\uDE20", "exploding_head", "\uD83E\uDD2F",
            "face_with_head_bandage", "\uD83E\uDD15", "mask", "\uD83D\uDE37", "skull", "\uD83D\uDC80",
            "ghost", "\uD83D\uDC7B", "alien", "\uD83D\uDC7D", "robot", "\uD83E\uDD16", "poop", "\uD83D\uDCA9",
            "see_no_evil", "\uD83D\uDE48", "clap", "\uD83D\uDC4F", "wave", "\uD83D\uDC4B", "raised_hands", "\uD83D\uDE4C",
            "pray", "\uD83D\uDE4F", "ok_hand", "\uD83D\uDC4C", "v", "\u270C\uFE0F", "muscle", "\uD83D\uDCAA",
            "point_up", "\u261D\uFE0F", "point_right", "\uD83D\uDC49", "point_left", "\uD83D\uDC48",
            "eyes", "\uD83D\uDC40", "heart", "\u2764\uFE0F", "broken_heart", "\uD83D\uDC94",
            "sparkling_heart", "\uD83D\uDC96", "fire", "\uD83D\uDD25", "sparkles", "\u2728", "star", "\u2B50",
            "star2", "\uD83C\uDF1F", "zap", "\u26A1", "boom", "\uD83D\uDCA5", "tada", "\uD83C\uDF89",
            "confetti_ball", "\uD83C\uDF8A", "rocket", "\uD83D\uDE80", "100", "\uD83D\uDCAF", "trophy", "\uD83C\uDFC6",
            "medal_sports", "\uD83C\uDFC5", "gift", "\uD83C\uDF81", "coffee", "\u2615", "beer", "\uD83C\uDF7A",
            "pizza", "\uD83C\uDF55", "cake", "\uD83C\uDF70", "bug", "\uD83D\uDC1B", "beetle", "\uD83E\uDEB2",
            "penguin", "\uD83D\uDC27", "octopus", "\uD83D\uDC19", "cat", "\uD83D\uDC31", "dog", "\uD83D\uDC36",
            "white_check_mark", "\u2705", "heavy_check_mark", "\u2714\uFE0F", "x", "\u274C",
            "negative_squared_cross_mark", "\u274E", "warning", "\u26A0\uFE0F", "no_entry", "\u26D4",
            "no_entry_sign", "\uD83D\uDEAB", "construction", "\uD83D\uDEA7", "question", "\u2753",
            "exclamation", "\u2757", "bangbang", "\u203C\uFE0F", "information_source", "\u2139\uFE0F",
            "bulb", "\uD83D\uDCA1", "memo", "\uD83D\uDCDD", "pencil2", "\u270F\uFE0F", "book", "\uD83D\uDCD6",
            "books", "\uD83D\uDCDA", "bookmark", "\uD83D\uDD16", "link", "\uD83D\uDD17", "paperclip", "\uD83D\uDCCE",
            "pushpin", "\uD83D\uDCCC", "lock", "\uD83D\uDD12", "unlock", "\uD83D\uDD13", "key", "\uD83D\uDD11",
            "hammer", "\uD83D\uDD28", "wrench", "\uD83D\uDD27", "hammer_and_wrench", "\uD83D\uDEE0\uFE0F",
            "gear", "\u2699\uFE0F", "package", "\uD83D\uDCE6", "computer", "\uD83D\uDCBB", "iphone", "\uD83D\uDCF1",
            "mag", "\uD83D\uDD0D", "chart_with_upwards_trend", "\uD83D\uDCC8", "calendar", "\uD83D\uDCC6",
            "hourglass", "\u231B", "alarm_clock", "\u23F0", "bell", "\uD83D\uDD14", "mega", "\uD83D\uDCE3",
            "speech_balloon", "\uD83D\uDCAC", "email", "\uD83D\uDCE7", "art", "\uD83C\uDFA8", "lipstick", "\uD83D\uDC84",
            "recycle", "\u267B\uFE0F", "arrow_up", "\u2B06\uFE0F", "arrow_down", "\u2B07\uFE0F",
            "arrow_right", "\u27A1\uFE0F", "arrow_left", "\u2B05\uFE0F", "heavy_plus_sign", "\u2795",
            "heavy_minus_sign", "\u2796", "red_circle", "\uD83D\uDD34", "green_circle", "\uD83D\uDFE2",
            "large_blue_circle", "\uD83D\uDD35", "white_circle", "\u26AA", "black_circle", "\u26AB",
            "rainbow", "\uD83C\uDF08", "sunny", "\u2600\uFE0F", "cloud", "\u2601\uFE0F", "snowflake", "\u2744\uFE0F",
            "earth_africa", "\uD83C\uDF0D", "globe_with_meridians", "\uD83C\uDF10",
            "rotating_light", "\uD83D\uDEA8", "checkered_flag", "\uD83C\uDFC1", "triangular_flag_on_post", "\uD83D\uDEA9",
    };

    private static final Map<String, String> CODES = new HashMap<>(TABLE.length);

    static {
        for (int i = 0; i + 1 < TABLE.length; i += 2) CODES.put(TABLE[i], TABLE[i + 1]);
    }

    private EmojiCodes() {
    }

    @Nullable
    public static String get(@NonNull String name) {
        return CODES.get(name);
    }

    public static int size() {
        return CODES.size();
    }
}
