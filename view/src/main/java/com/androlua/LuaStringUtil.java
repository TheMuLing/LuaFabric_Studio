package com.androlua;

import java.util.Arrays;
import java.util.Map;

public class LuaStringUtil {
    public static MatchResult find(String s, String pat) {
        return find(s, pat, 1, false);
    }

    public static MatchResult find(String s, String pat, int init) {
        return find(s, pat, init, false);
    }

    public static MatchResult find(String s, String pat, int init, boolean fastMatch) {
        return str_find_aux(s, pat, init, fastMatch, true);
    }

    public static GMatchAux gmatch(String src, String pat) {
        return new GMatchAux(src, pat, false);
    }

    public static GMatchAux gfind(String src, String pat) {
        return new GMatchAux(src, pat, true);
    }

    public static class GMatchAux {
        private final int srclen;
        private final MatchState ms;
        private final boolean find;
        private int soffset;
        private int lastmatch;

        public GMatchAux(String src, String pat, boolean find) {
            this.srclen = src.length();
            this.ms = new MatchState(src, pat);
            this.soffset = 0;
            this.lastmatch = -1;
            this.find = find;
        }

        public MatchResult invoke() {
            for (; soffset <= srclen; soffset++) {
                ms.reset();
                int res = ms.match(soffset, 0);
                if (res >= 0 && res != lastmatch) {
                    int soff = soffset;
                    lastmatch = soffset = res;
                    if (find)
                        return new MatchResult(soff + 1, res, ms.push_captures(false, soff, res));
                    else
                        return new MatchResult(ms.push_captures(true, soff, res));
                }
            }
            return null;
        }
    }

    public static String sub(String s, int start) {
        return sub(s, start, -1);
    }

    public static String sub(String s, int start, int end) {
        final int l = s.length();
        start = posrelat(start, l);
        end = posrelat(end, l);

        if (start < 1)
            start = 1;
        if (end > l)
            end = l;

        if (start <= end) {
            return s.substring(start - 1, end);
        } else {
            return "";
        }
    }

    public static MatchResult gsub(String src, String p, Object repl) {
        try {
            return gsub(src, p, repl, src.length() + 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static MatchResult gsub(String src, String p, Object repl, int max_s) {
        final int srclen = src.length();
        int lastmatch = -1; /* end of last match */
        final boolean anchor = p.length() > 0 && p.charAt(0) == '^';
        Buffer lbuf = new Buffer(srclen);
        MatchState ms = new MatchState(src, p);

        int soffset = 0;
        int n = 0;
        while (n < max_s) {
            ms.reset();
            int res = ms.match(soffset, anchor ? 1 : 0);
            if (res != -1 && res != lastmatch) {  /* match? */
                n++;
                ms.add_value(lbuf, soffset, res, repl);  /* add replacement to buffer */
                soffset = lastmatch = res;
            } else if (soffset < srclen) /* otherwise, skip one character */
                lbuf.append(src.charAt(soffset++));
            else break;   /* end of subject */
            if (anchor) break;
        }
        lbuf.append(src.substring(soffset, srclen));
        return new MatchResult(lbuf.tostring(), n);
    }

    public static MatchResult match(String s, String pat) {
        return match(s, pat, 1, false);
    }

    public static MatchResult match(String s, String pat, int init) {
        return match(s, pat, init, false);
    }

    public static MatchResult match(String s, String pat, int init, boolean fastMatch) {
        return str_find_aux(s, pat, init, fastMatch, false);
    }

    public static String rep(String s, int n) {
        StringBuilder buf = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) {
            buf.append(s);
        }
        return buf.toString();
    }

    private static MatchResult str_find_aux(String s, String pat, int init, boolean fastMatch, boolean find) {
        if (init > 0) {
            init = Math.min(init - 1, s.length());
        } else if (init < 0) {
            init = Math.max(0, s.length() + init);
        }

        fastMatch = find && (fastMatch || indexOfAny(pat, SPECIALS) == -1);

        if (fastMatch) {
            int result = s.indexOf(pat, init);
            if (result != -1) {
                return new MatchResult(result + 1, result + pat.length());
            }
        } else {
            MatchState ms = new MatchState(s, pat);
            boolean anchor = false;
            int poff = 0;
            if (pat.length() > 0 && pat.charAt(0) == '^') {
                anchor = true;
                poff = 1;
            }

            int soff = init;
            do {
                int res;
                ms.reset();
                if ((res = ms.match(soff, poff)) != -1) {
                    if (find) {
                        return new MatchResult(soff + 1, res, ms.push_captures(false, soff, res));
                    } else {
                        return new MatchResult(ms.push_captures(true, soff, res));
                    }
                }
            } while (soff++ < s.length() && !anchor);
        }
        return null;
    }

    private static int indexOfAny(String pat, char[] specials) {
        for (char c : specials) {
            if (pat.indexOf(c) > -1)
                return 1;
        }
        return -1;
    }

    private static int posrelat(int pos, int len) {
        return (pos >= 0) ? pos : len + pos + 1;
    }

    private static final int L_ESC = '%';
    private static final char[] SPECIALS = "^$*+?.([%-".toCharArray();
    private static final int MAX_CAPTURES = 32;

    private static final int MAXCCALLS = 200;

    private static final int CAP_UNFINISHED = -1;
    private static final int CAP_POSITION = -2;

    private static final byte MASK_ALPHA = 0x01;
    private static final byte MASK_LOWERCASE = 0x02;
    private static final byte MASK_UPPERCASE = 0x04;
    private static final byte MASK_DIGIT = 0x08;
    private static final byte MASK_PUNCT = 0x10;
    private static final byte MASK_SPACE = 0x20;
    private static final byte MASK_CONTROL = 0x40;
    private static final byte MASK_HEXDIGIT = (byte) 0x80;

    private static final byte[] CHAR_TABLE;
    private static final int[] EMOJI_TABLE = new int[]{8211, 8212, 8224, 8225, 8252, 8265, 8315, 8364, 8377, 8381, 8471, 8482, 8505, 8592, 8593, 8594, 8595, 8596, 8597, 8598, 8599, 8600, 8601, 8617, 8618, 8645, 8646, 8710, 8711, 8712, 8730, 8734, 8745, 8746, 8801, 8834, 8986, 8987, 9000, 9167, 9193, 9194, 9195, 9196, 9197, 9198, 9199, 9200, 9201, 9202, 9203, 9208, 9209, 9210, 9410, 9642, 9643, 9650, 9654, 9660, 9664, 9674, 9675, 9679, 9711, 9723, 9724, 9725, 9726, 9728, 9729, 9730, 9731, 9732, 9742, 9745, 9748, 9749, 9752, 9757, 9760, 9762, 9763, 9766, 9770, 9774, 9775, 9784, 9785, 9786, 9792, 9794, 9800, 9801, 9802, 9803, 9804, 9805, 9806, 9807, 9808, 9809, 9810, 9811, 9823, 9824, 9827, 9829, 9830, 9832, 9851, 9854, 9855, 9874, 9875, 9876, 9877, 9878, 9879, 9881, 9883, 9884, 9888, 9889, 9895, 9898, 9899, 9904, 9905, 9917, 9918, 9924, 9925, 9928, 9934, 9935, 9937, 9939, 9940, 9961, 9962, 9968, 9969, 9970, 9971, 9972, 9973, 9975, 9976, 9977, 9978, 9981, 9986, 9989, 9992, 9993, 9994, 9995, 9996, 9997, 9999, 10002, 10004, 10006, 10013, 10017, 10024, 10035, 10036, 10052, 10055, 10060, 10062, 10067, 10068, 10069, 10071, 10083, 10084, 10133, 10134, 10135, 10145, 10160, 10175, 10548, 10549, 11013, 11014, 11015, 11035, 11036, 11088, 11093, 12336, 12349, 12951, 12953, 126980, 127183, 127344, 127345, 127358, 127359, 127374, 127377, 127378, 127379, 127380, 127381, 127382, 127383, 127384, 127385, 127386, 127489, 127490, 127514, 127535, 127538, 127539, 127540, 127541, 127542, 127543, 127544, 127545, 127546, 127568, 127569, 127744, 127745, 127746, 127747, 127748, 127749, 127750, 127751, 127752, 127753, 127754, 127755, 127756, 127757, 127758, 127759, 127760, 127761, 127762, 127763, 127764, 127765, 127766, 127767, 127768, 127769, 127770, 127771, 127772, 127773, 127774, 127775, 127776, 127777, 127780, 127781, 127782, 127783, 127784, 127785, 127786, 127787, 127788, 127789, 127790, 127791, 127792, 127793, 127794, 127795, 127796, 127797, 127798, 127799, 127800, 127801, 127802, 127803, 127804, 127805, 127806, 127807, 127808, 127809, 127810, 127811, 127812, 127813, 127814, 127815, 127816, 127817, 127818, 127819, 127820, 127821, 127822, 127823, 127824, 127825, 127826, 127827, 127828, 127829, 127830, 127831, 127832, 127833, 127834, 127835, 127836, 127837, 127838, 127839, 127840, 127841, 127842, 127843, 127844, 127845, 127846, 127847, 127848, 127849, 127850, 127851, 127852, 127853, 127854, 127855, 127856, 127857, 127858, 127859, 127860, 127861, 127862, 127863, 127864, 127865, 127866, 127867, 127868, 127869, 127870, 127871, 127872, 127873, 127874, 127875, 127876, 127877, 127878, 127879, 127880, 127881, 127882, 127883, 127884, 127885, 127886, 127887, 127888, 127889, 127890, 127891, 127894, 127895, 127897, 127898, 127899, 127902, 127903, 127904, 127905, 127906, 127907, 127908, 127909, 127910, 127911, 127912, 127913, 127914, 127915, 127916, 127917, 127918, 127919, 127920, 127921, 127922, 127923, 127924, 127925, 127926, 127927, 127928, 127929, 127930, 127931, 127932, 127933, 127934, 127935, 127936, 127937, 127938, 127939, 127940, 127941, 127942, 127943, 127944, 127945, 127946, 127947, 127948, 127949, 127950, 127951, 127952, 127953, 127954, 127955, 127956, 127957, 127958, 127959, 127960, 127961, 127962, 127963, 127964, 127965, 127966, 127967, 127968, 127969, 127970, 127971, 127972, 127973, 127974, 127975, 127976, 127977, 127978, 127979, 127980, 127981, 127982, 127983, 127984, 127987, 127988, 127989, 127991, 127992, 127993, 127994, 127995, 127996, 127997, 127998, 127999, 128000, 128001, 128002, 128003, 128004, 128005, 128006, 128007, 128008, 128009, 128010, 128011, 128012, 128013, 128014, 128015, 128016, 128017, 128018, 128019, 128020, 128021, 128022, 128023, 128024, 128025, 128026, 128027, 128028, 128029, 128030, 128031, 128032, 128033, 128034, 128035, 128036, 128037, 128038, 128039, 128040, 128041, 128042, 128043, 128044, 128045, 128046, 128047, 128048, 128049, 128050, 128051, 128052, 128053, 128054, 128055, 128056, 128057, 128058, 128059, 128060, 128061, 128062, 128063, 128064, 128065, 128066, 128067, 128068, 128069, 128070, 128071, 128072, 128073, 128074, 128075, 128076, 128077, 128078, 128079, 128080, 128081, 128082, 128083, 128084, 128085, 128086, 128087, 128088, 128089, 128090, 128091, 128092, 128093, 128094, 128095, 128096, 128097, 128098, 128099, 128100, 128101, 128102, 128103, 128104, 128105, 128106, 128107, 128108, 128109, 128110, 128111, 128112, 128113, 128114, 128115, 128116, 128117, 128118, 128119, 128120, 128121, 128122, 128123, 128124, 128125, 128126, 128127, 128128, 128129, 128130, 128131, 128132, 128133, 128134, 128135, 128136, 128137, 128138, 128139, 128140, 128141, 128142, 128143, 128144, 128145, 128146, 128147, 128148, 128149, 128150, 128151, 128152, 128153, 128154, 128155, 128156, 128157, 128158, 128159, 128160, 128161, 128162, 128163, 128164, 128165, 128166, 128167, 128168, 128169, 128170, 128171, 128172, 128173, 128174, 128175, 128176, 128177, 128178, 128179, 128180, 128181, 128182, 128183, 128184, 128185, 128186, 128187, 128188, 128189, 128190, 128191, 128192, 128193, 128194, 128195, 128196, 128197, 128198, 128199, 128200, 128201, 128202, 128203, 128204, 128205, 128206, 128207, 128208, 128209, 128210, 128211, 128212, 128213, 128214, 128215, 128216, 128217, 128218, 128219, 128220, 128221, 128222, 128223, 128224, 128225, 128226, 128227, 128228, 128229, 128230, 128231, 128232, 128233, 128234, 128235, 128236, 128237, 128238, 128239, 128240, 128241, 128242, 128243, 128244, 128245, 128246, 128247, 128248, 128249, 128250, 128251, 128252, 128253, 128255, 128256, 128257, 128258, 128259, 128260, 128261, 128262, 128263, 128264, 128265, 128266, 128267, 128268, 128269, 128270, 128271, 128272, 128273, 128274, 128275, 128276, 128277, 128278, 128279, 128280, 128281, 128282, 128283, 128284, 128285, 128286, 128287, 128288, 128289, 128290, 128291, 128292, 128293, 128294, 128295, 128296, 128297, 128298, 128299, 128300, 128301, 128302, 128303, 128304, 128305, 128306, 128307, 128308, 128309, 128310, 128311, 128312, 128313, 128314, 128315, 128316, 128317, 128329, 128330, 128331, 128332, 128333, 128334, 128336, 128337, 128338, 128339, 128340, 128341, 128342, 128343, 128344, 128345, 128346, 128347, 128348, 128349, 128350, 128351, 128352, 128353, 128354, 128355, 128356, 128357, 128358, 128359, 128367, 128368, 128371, 128372, 128373, 128374, 128375, 128376, 128377, 128378, 128391, 128394, 128395, 128396, 128397, 128400, 128405, 128406, 128420, 128421, 128424, 128433, 128434, 128444, 128450, 128451, 128452, 128465, 128466, 128467, 128476, 128477, 128478, 128481, 128483, 128488, 128495, 128499, 128506, 128507, 128508, 128509, 128510, 128511, 128512, 128513, 128514, 128515, 128516, 128517, 128518, 128519, 128520, 128521, 128522, 128523, 128524, 128525, 128526, 128527, 128528, 128529, 128530, 128531, 128532, 128533, 128534, 128535, 128536, 128537, 128538, 128539, 128540, 128541, 128542, 128543, 128544, 128545, 128546, 128547, 128548, 128549, 128550, 128551, 128552, 128553, 128554, 128555, 128556, 128557, 128558, 128559, 128560, 128561, 128562, 128563, 128564, 128565, 128566, 128567, 128568, 128569, 128570, 128571, 128572, 128573, 128574, 128575, 128576, 128577, 128578, 128579, 128580, 128581, 128582, 128583, 128584, 128585, 128586, 128587, 128588, 128589, 128590, 128591, 128640, 128641, 128642, 128643, 128644, 128645, 128646, 128647, 128648, 128649, 128650, 128651, 128652, 128653, 128654, 128655, 128656, 128657, 128658, 128659, 128660, 128661, 128662, 128663, 128664, 128665, 128666, 128667, 128668, 128669, 128670, 128671, 128672, 128673, 128674, 128675, 128676, 128677, 128678, 128679, 128680, 128681, 128682, 128683, 128684, 128685, 128686, 128687, 128688, 128689, 128690, 128691, 128692, 128693, 128694, 128695, 128696, 128697, 128698, 128699, 128700, 128701, 128702, 128703, 128704, 128705, 128706, 128707, 128708, 128709, 128715, 128716, 128717, 128718, 128719, 128720, 128721, 128722, 128725, 128726, 128727, 128736, 128737, 128738, 128739, 128740, 128741, 128745, 128747, 128748, 128752, 128755, 128756, 128757, 128758, 128759, 128760, 128761, 128762, 128763, 128764, 128992, 128993, 128994, 128995, 128996, 128997, 128998, 128999, 129000, 129001, 129002, 129003, 129292, 129293, 129294, 129295, 129296, 129297, 129298, 129299, 129300, 129301, 129302, 129303, 129304, 129305, 129306, 129307, 129308, 129309, 129310, 129311, 129312, 129313, 129314, 129315, 129316, 129317, 129318, 129319, 129320, 129321, 129322, 129323, 129324, 129325, 129326, 129327, 129328, 129329, 129330, 129331, 129332, 129333, 129334, 129335, 129336, 129337, 129338, 129340, 129341, 129342, 129343, 129344, 129345, 129346, 129347, 129348, 129349, 129351, 129352, 129353, 129354, 129355, 129356, 129357, 129358, 129359, 129360, 129361, 129362, 129363, 129364, 129365, 129366, 129367, 129368, 129369, 129370, 129371, 129372, 129373, 129374, 129375, 129376, 129377, 129378, 129379, 129380, 129381, 129382, 129383, 129384, 129385, 129386, 129387, 129388, 129389, 129390, 129391, 129392, 129393, 129394, 129395, 129396, 129397, 129398, 129399, 129400, 129402, 129403, 129404, 129405, 129406, 129407, 129408, 129409, 129410, 129411, 129412, 129413, 129414, 129415, 129416, 129417, 129418, 129419, 129420, 129421, 129422, 129423, 129424, 129425, 129426, 129427, 129428, 129429, 129430, 129431, 129432, 129433, 129434, 129435, 129436, 129437, 129438, 129439, 129440, 129441, 129442, 129443, 129444, 129445, 129446, 129447, 129448, 129449, 129450, 129451, 129452, 129453, 129454, 129455, 129456, 129457, 129458, 129459, 129460, 129461, 129462, 129463, 129464, 129465, 129466, 129467, 129468, 129469, 129470, 129471, 129472, 129473, 129474, 129475, 129476, 129477, 129478, 129479, 129480, 129481, 129482, 129483, 129485, 129486, 129487, 129488, 129489, 129490, 129491, 129492, 129493, 129494, 129495, 129496, 129497, 129498, 129499, 129500, 129501, 129502, 129503, 129504, 129505, 129506, 129507, 129508, 129509, 129510, 129511, 129512, 129513, 129514, 129515, 129516, 129517, 129518, 129519, 129520, 129521, 129522, 129523, 129524, 129525, 129526, 129527, 129528, 129529, 129530, 129531, 129532, 129533, 129534, 129535, 129648, 129649, 129650, 129651, 129652, 129656, 129657, 129658, 129664, 129665, 129666, 129667, 129668, 129669, 129670, 129680, 129681, 129682, 129683, 129684, 129685, 129686, 129687, 129688, 129689, 129690, 129691, 129692, 129693, 129694, 129695, 129696, 129697, 129699, 129700, 129701, 129702, 129703, 129704, 129712, 129713, 129714, 129715, 129716, 129717, 129718, 129728, 129729, 129730, 129744, 129745, 129746, 129747, 129748, 129749, 129750};

    static {
        CHAR_TABLE = new byte[256];

        for (int i = 0; i < 128; ++i) {
            final char c = (char) i;
            CHAR_TABLE[i] = (byte) ((Character.isDigit(c) ? MASK_DIGIT : 0) |
                    (Character.isLowerCase(c) ? MASK_LOWERCASE : 0) |
                    (Character.isUpperCase(c) ? MASK_UPPERCASE : 0) |
                    ((c < ' ' || c == 0x7F) ? MASK_CONTROL : 0));
            if ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || (c >= '0' && c <= '9')) {
                CHAR_TABLE[i] |= MASK_HEXDIGIT;
            }
            if ((c >= '!' && c <= '/') || (c >= ':' && c <= '@') || (c >= '[' && c <= '`') || (c >= '{' && c <= '~')) {
                CHAR_TABLE[i] |= MASK_PUNCT;
            }
            if ((CHAR_TABLE[i] & (MASK_LOWERCASE | MASK_UPPERCASE)) != 0) {
                CHAR_TABLE[i] |= MASK_ALPHA;
            }
        }

        CHAR_TABLE[' '] = MASK_SPACE;
        CHAR_TABLE['\r'] |= MASK_SPACE;
        CHAR_TABLE['\n'] |= MASK_SPACE;
        CHAR_TABLE['\t'] |= MASK_SPACE;
        CHAR_TABLE[0x0B /* '\v' */] |= MASK_SPACE;
        CHAR_TABLE['\f'] |= MASK_SPACE;
    }

    public static class MatchState {
        char[] c;
        int matchdepth;  /* control for recursive depth (to avoid C stack overflow) */
        final String s;
        final String p;
        int level;
        int[] cinit;
        int[] clen;

        MatchState(String s, String pattern) {
            this.s = s;
            this.c = s.toCharArray();
            this.p = pattern;
            this.level = 0;
            this.cinit = new int[MAX_CAPTURES];
            this.clen = new int[MAX_CAPTURES];
            this.matchdepth = MAXCCALLS;
        }

        void reset() {
            level = 0;
            this.matchdepth = MAXCCALLS;
        }

        private void add_s(Buffer lbuf, String ns, int soff, int e) {
            String news = ns;
            int l = news.length();
            for (int i = 0; i < l; ++i) {
                char b = news.charAt(i);
                if (b != L_ESC) {
                    lbuf.append(b);
                } else {
                    ++i; // skip ESC
                    b = (i < l ? news.charAt(i) : 0);
                    if (!Character.isDigit(b)) {
                        if (b != L_ESC)
                            error("invalid use of '" + (char) L_ESC +
                                    "' in replacement string: after '" + (char) L_ESC +
                                    "' must be '0'-'9' or '" + (char) L_ESC +
                                    "', but found " + (i < l ? "symbol '" + b + "' with code " + b +
                                    " at pos " + (i + 1) :
                                    "end of string"));
                        lbuf.append(b);
                    } else if (b == '0') {
                        lbuf.append(s.substring(soff, e));
                    } else {
                        lbuf.append(push_onecapture(b - '1', soff, e));
                    }
                }
            }
        }

        public void add_value(Buffer lbuf, int soffset, int end, Object repl) {
            if (repl instanceof Map m) {
                repl = m.get(push_onecapture(0, soffset, end));
            } else if (repl instanceof MatchFunc f) {
                repl = f.invoke(push_captures(true, soffset, end));
            } else {
                add_s(lbuf, repl.toString(), soffset, end);
                return;
            }
            if (!toboolean(repl)) {
                repl = s.substring(soffset, end);
            } else if (!(repl instanceof String)) {
                error("invalid replacement value (a " + repl.getClass() + ")");
            }
            lbuf.append(repl.toString());
        }

        private void error(String str) {

        }

        private boolean toboolean(Object repl) {
            if (repl == null)
                return false;
            if (repl instanceof Boolean)
                return (Boolean) repl;
            return true;
        }

        String[] push_captures(boolean wholeMatch, int soff, int end) {
            int nlevels = (this.level == 0 && wholeMatch) ? 1 : this.level;
            switch (nlevels) {
                case 0:
                    return null;
                case 1:
                    return new String[]{push_onecapture(0, soff, end)};
            }
            String[] v = new String[nlevels];
            for (int i = 0; i < nlevels; ++i)
                v[i] = push_onecapture(i, soff, end);
            return v;
        }

        private String push_onecapture(int i, int soff, int end) {
            if (i >= this.level) {
                if (i == 0) {
                    return s.substring(soff, end);
                } else {
                    return ("invalid capture index %" + (i + 1));
                }
            } else {
                int l = clen[i];
                if (l == CAP_UNFINISHED) {
                    return ("unfinished capture");
                }
                if (l == CAP_POSITION) {
                    return String.valueOf(cinit[i] + 1);
                } else {
                    int begin = cinit[i];
                    return s.substring(begin, begin + l);
                }
            }
        }

        private int check_capture(int l) {
            l -= '1';
            if (l < 0 || l >= level || this.clen[l] == CAP_UNFINISHED) {
                error("invalid capture index %" + (l + 1));
            }
            return l;
        }

        private int capture_to_close() {
            int level = this.level;
            for (level--; level >= 0; level--)
                if (clen[level] == CAP_UNFINISHED)
                    return level;
            error("invalid pattern capture");
            return 0;
        }

        int classend(int poffset) {
            switch (p.charAt(poffset++)) {
                case L_ESC:
                    if (poffset == p.length()) {
                        error("malformed pattern (ends with '%')");
                    }
                    return poffset + 1;

                case '[':
                    if (poffset != p.length() && p.charAt(poffset) == '^') poffset++;
                    do {
                        if (poffset == p.length()) {
                            error("malformed pattern (missing ']')");
                        }
                        if (p.charAt(poffset++) == L_ESC && poffset < p.length())
                            poffset++; /* skip escapes (e.g. '%]') */
                    } while (poffset == p.length() || p.charAt(poffset) != ']');
                    return poffset + 1;
                default:
                    return poffset;
            }
        }

        static boolean match_class(int c, int cl) {
            final char lcl = Character.toLowerCase((char) cl);
            int cdata = c > 255 ? c : CHAR_TABLE[c];

            boolean res;
            switch (lcl) {
                case 'a':
                    res = (cdata & MASK_ALPHA) != 0;
                    break;
                case 'd':
                    res = (cdata & MASK_DIGIT) != 0;
                    break;
                case 'l':
                    res = (cdata & MASK_LOWERCASE) != 0;
                    break;
                case 'u':
                    res = (cdata & MASK_UPPERCASE) != 0;
                    break;
                case 'c':
                    res = (cdata & MASK_CONTROL) != 0;
                    break;
                case 'p':
                    res = (cdata & MASK_PUNCT) != 0;
                    break;
                case 's':
                    res = (cdata & MASK_SPACE) != 0;
                    break;
                case 'g':
                    res = (cdata & (MASK_ALPHA | MASK_DIGIT | MASK_PUNCT)) != 0;
                    break;
                case 'w':
                    res = (cdata & (MASK_ALPHA | MASK_DIGIT)) != 0;
                    break;
                case 'x':
                    res = (cdata & MASK_HEXDIGIT) != 0;
                    break;
                case 'z':
                    res = (c == 0);
                    break;  /* deprecated option */
                case 'h':
                    res = (c >= 0x4E00 && c <= 0x9FA5)//基本汉字 20902字 4E00-9FA5
                            || (c >= 0x9FA6 && c <= 0x9FEF)//基本汉字补充 74字 9FA6-9FEF
                            || (c >= 0x3400 && c <= 0x4DB5)//扩展A 6582字 3400-4DB5
                            || (c >= 0x20000 && c <= 0x2A6D6)//扩展B 42711字 20000-
                            || (c >= 0x2A700 && c <= 0x2B734)//扩展C 4149字 2A700-2B734
                            || (c >= 0x2B740 && c <= 0x2B81D)//扩展D 222字 2B740-2B81D
                            || (c >= 0x2B820 && c <= 0x2CEA1)//扩展E 5762字 2B820-2CEA1
                            || (c >= 0x2CEB0 && c <= 0x2EBE0)//扩展F 7473字 2CEB0-2EBE0
                            || (c >= 0x30000 && c <= 0x3134A)//扩展G 4939字 30000-3134A
                            || (c >= 0x2F00 && c <= 0x2FD5)//康熙部首 214字 2F00-2FD5
                            || (c >= 0x2E80 && c <= 0x2EF3)//部首扩展 115字 2E80-2EF3
                            || (c >= 0xF900 && c <= 0xFAD9)//兼容汉字 477字 F900-FAD9
                            || (c >= 0x2F800 && c <= 0x2FA1D)//兼容扩展 542字 2F800-2FA1D
                            || (c >= 0xE400 && c <= 0xE5E8)//部件扩展 452字 E400-E5E8
                            || (c >= 0x31C0 && c <= 0x31E3)//汉字笔画 36字 31C0-31E3
                            || (c >= 0x2FF0 && c <= 0x2FFB)//汉字结构 12字 2FF0-2FFB
                            || (c >= 0x3105 && c <= 0x312F)//汉语注音 43字 3105-312F
                            || (c >= 0x31A0 && c <= 0x31BA)//注音扩展 22字 31A0-31BA
                            || (c == 0x3007)//〇 1字 3007
                    ;
                    break;
                case 'e':
                    return Arrays.binarySearch(EMOJI_TABLE, c) > -1;
                default:
                    return cl == c;
            }
            return (lcl == cl) == res;
        }

        boolean matchbracketclass(int c, int poff, int ec) {
            boolean sig = true;
            if (p.charAt(poff + 1) == '^') {
                sig = false;
                poff++;
            }
            while (++poff < ec) {
                if (p.charAt(poff) == L_ESC) {
                    poff++;
                    if (match_class(c, p.charAt(poff)))
                        return sig;
                } else if ((p.charAt(poff + 1) == '-') && (poff + 2 < ec)) {
                    poff += 2;
                    if (p.charAt(poff - 2) <= c && c <= p.charAt(poff))
                        return sig;
                } else if (p.charAt(poff) == c) return sig;
            }
            return !sig;
        }

        boolean singlematch(int c, int poff, int ep) {
            switch (p.charAt(poff)) {
                case '.':
                    return true;
                case L_ESC:
                    return match_class(c, p.charAt(poff + 1));
                case '[':
                    return matchbracketclass(c, poff, ep - 1);
                default:
                    return p.charAt(poff) == c;
            }
        }

        /**
         * Perform pattern matching. If there is a match, returns offset into s
         * where match ends, otherwise returns -1.
         */
        int match(int soffset, int poffset) {
            if (matchdepth-- == 0) error("pattern too complex");
            try {
                while (true) {
                    // Check if we are at the end of the pattern -
                    // equivalent to the '\0' case in the C version, but our pattern
                    // string is not NUL-terminated.
                    if (poffset == p.length())
                        return soffset;
                    switch (p.charAt(poffset)) {
                        case '(':
                            if (++poffset < p.length() && p.charAt(poffset) == ')')
                                return start_capture(soffset, poffset + 1, CAP_POSITION);
                            else
                                return start_capture(soffset, poffset, CAP_UNFINISHED);
                        case ')':
                            return end_capture(soffset, poffset + 1);
                        case L_ESC:
                            if (poffset + 1 == p.length())
                                error("malformed pattern (ends with '%')");
                            switch (p.charAt(poffset + 1)) {
                                case 'b':
                                    soffset = matchbalance(soffset, poffset + 2);
                                    if (soffset == -1) return -1;
                                    poffset += 4;
                                    continue;
                                case 'f': {
                                    poffset += 2;
                                    if (poffset == p.length() || p.charAt(poffset) != '[') {
                                        error("missing '[' after '%f' in pattern");
                                    }
                                    int ep = classend(poffset);
                                    int previous = (soffset == 0) ? '\0' : s.charAt(soffset - 1);
                                    int next = (soffset == s.length()) ? '\0' : s.charAt(soffset);
                                    if (matchbracketclass(previous, poffset, ep - 1) ||
                                            !matchbracketclass(next, poffset, ep - 1))
                                        return -1;
                                    poffset = ep;
                                    continue;
                                }
                                default: {
                                    int c = p.charAt(poffset + 1);
                                    if (Character.isDigit((char) c)) {
                                        soffset = match_capture(soffset, c);
                                        if (soffset == -1)
                                            return -1;
                                        return match(soffset, poffset + 2);
                                    }
                                }
                            }
                        case '$':
                            if (poffset + 1 == p.length())
                                return (soffset == s.length()) ? soffset : -1;
                    }
                    int ep = classend(poffset);
                    boolean m = soffset < s.length() && singlematch(s.charAt(soffset), poffset, ep);
                    int pc = (ep < p.length()) ? p.charAt(ep) : '\0';

                    switch (pc) {
                        case '?':
                            int res;
                            if (m && ((res = match(soffset + 1, ep + 1)) != -1))
                                return res;
                            poffset = ep + 1;
                            continue;
                        case '*':
                            return max_expand(soffset, poffset, ep);
                        case '+':
                            return (m ? max_expand(soffset + 1, poffset, ep) : -1);
                        case '-':
                            return min_expand(soffset, poffset, ep);
                        default:
                            if (!m)
                                return -1;
                            soffset++;
                            poffset = ep;
                            continue;
                    }
                }
            } finally {
                matchdepth++;
            }
        }

        int max_expand(int soff, int poff, int ep) {
            int i = 0;
            while (soff + i < s.length() &&
                    singlematch(s.charAt(soff + i), poff, ep))
                i++;
            while (i >= 0) {
                int res = match(soff + i, ep + 1);
                if (res != -1)
                    return res;
                i--;
            }
            return -1;
        }

        int min_expand(int soff, int poff, int ep) {
            for (; ; ) {
                int res = match(soff, ep + 1);
                if (res != -1)
                    return res;
                else if (soff < s.length() && singlematch(s.charAt(soff), poff, ep))
                    soff++;
                else return -1;
            }
        }

        int start_capture(int soff, int poff, int what) {
            int res;
            int level = this.level;
            if (level >= MAX_CAPTURES) {
                error("too many captures");
            }
            cinit[level] = soff;
            clen[level] = what;
            this.level = level + 1;
            if ((res = match(soff, poff)) == -1)
                this.level--;
            return res;
        }

        int end_capture(int soff, int poff) {
            int l = capture_to_close();
            int res;
            clen[l] = soff - cinit[l];
            if ((res = match(soff, poff)) == -1)
                clen[l] = CAP_UNFINISHED;
            return res;
        }

        int match_capture(int soff, int l) {
            l = check_capture(l);
            int len = clen[l];
            if ((s.length() - soff) >= len &&
                    equals(c, cinit[l], c, soff, len))
                return soff + len;
            else
                return -1;
        }

        public static boolean equals(char[] a, int i, char[] b, int j, int n) {
            if (a.length < i + n || b.length < j + n)
                return false;
            while (--n >= 0)
                if (a[i++] != b[j++])
                    return false;
            return true;
        }

        int matchbalance(int soff, int poff) {
            final int plen = p.length();
            if (poff == plen || poff + 1 == plen) {
                error("malformed pattern (missing arguments to '%b')");
            }
            final int slen = s.length();
            if (soff >= slen)
                return -1;
            final int b = p.charAt(poff);
            if (s.charAt(soff) != b)
                return -1;
            final int e = p.charAt(poff + 1);
            int cont = 1;
            while (++soff < slen) {
                if (s.charAt(soff) == e) {
                    if (--cont == 0) return soff + 1;
                } else if (s.charAt(soff) == b) cont++;
            }
            return -1;
        }
    }

    public static class MatchResult {

        private int n = 0;
        private String s;
        private String[] matchs;
        private int start = -1;
        private int end = -1;

        public MatchResult(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public MatchResult(String[] v) {
            this.matchs = v;
            n = matchs.length;
            if (n > 0)
                s = matchs[0];
        }

        public MatchResult(int start, int end, String[] v) {
            this.start = start;
            this.end = end;
            this.matchs = v;
        }

        public MatchResult(String s, int n) {
            this.s = s;
            this.n = n;
        }

        public String[] matchs() {
            return matchs;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }

        public int n() {
            return n;
        }

        public String result() {
            return s;
        }

        @Override
        public String toString() {
            return "MatchResult{" +
                    "n=" + n +
                    ", s='" + s + '\'' +
                    ", matchs=" + Arrays.toString(matchs) +
                    ", start=" + start +
                    ", end=" + end +
                    '}';
        }
    }

    private static final class Buffer {

        private static final int DEFAULT_CAPACITY = 64;

        private static final char[] NOBYTES = {};

        private char[] bytes;

        private int length;

        private int offset;

        private String value;

        public Buffer() {
            this(DEFAULT_CAPACITY);
        }

        public Buffer(int initialCapacity) {
            bytes = new char[initialCapacity];
            length = 0;
            offset = 0;
            value = null;
        }

        public Buffer(String value) {
            bytes = NOBYTES;
            length = offset = 0;
            this.value = value;
        }

        public String value() {
            return value != null ? value : this.tostring();
        }

        public Buffer setvalue(String value) {
            bytes = NOBYTES;
            offset = length = 0;
            this.value = value;
            return this;
        }

        public String tostring() {
            realloc(length, 0);
            return new String(bytes, offset, length);
        }

        public String tojstring() {
            return value();
        }

        public String toString() {
            return tojstring();
        }

        public Buffer append(byte b) {
            makeroom(0, 1);
            bytes[offset + length++] = (char) b;
            return this;
        }

        public Buffer append(char c) {
            makeroom(0, 1);
            bytes[offset + length] = c;
            /*if ((c) < 0x80) {
                bytes[j++] = (byte) c;
            } else if (c < 0x800) {
                bytes[j++] = (byte) (0xC0 | ((c >> 6) & 0x1f));
                bytes[j++] = (byte) (0x80 | (c & 0x3f));
            } else {
                bytes[j++] = (byte) (0xE0 | ((c >> 12) & 0x0f));
                bytes[j++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                bytes[j++] = (byte) (0x80 | (c & 0x3f));
            }*/
            length++;
            return this;
        }

        public Buffer append(Object val) {
            append(val.toString());
            return this;
        }

        public Buffer append(String str) {
            char[] c = str.toCharArray();
            final int n = str.length();
            makeroom(0, n);
            encodeToUtf8(c, c.length, bytes, offset + length);
            length += n;
            return this;
        }

        public static int encodeToUtf8(char[] chars, int nchars, char[] bytes, int off) {
            char c;
            int j = off;
            for (int i = 0; i < nchars; i++) {
                c = chars[i];
                bytes[j++] = c;
                /*    if ((c = chars[i]) < 0x80) {
                    bytes[j++] = (byte) c;
                } else if (c < 0x800) {
                    bytes[j++] = (byte) (0xC0 | ((c >> 6) & 0x1f));
                    bytes[j++] = (byte) (0x80 | (c & 0x3f));
                } else {
                    bytes[j++] = (byte) (0xE0 | ((c >> 12) & 0x0f));
                    bytes[j++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                    bytes[j++] = (byte) (0x80 | (c & 0x3f));
                }*/
            }
            return j - off;
        }

        public Buffer concatTo(Object lhs) {
            return setvalue(value.concat(lhs.toString()));
        }

        public Buffer concatTo(String lhs) {
            return value != null ? setvalue(lhs.concat(value)) : prepend(lhs);
        }

        public Buffer concatTo(Number lhs) {
            return value != null ? setvalue(value.concat(lhs.toString())) : prepend(lhs.toString());
        }

        public Buffer prepend(String s) {
            int n = s.length();
            makeroom(n, 0);
            System.arraycopy(s.toCharArray(), 0, bytes, offset - n, n);
            offset -= n;
            length += n;
            value = null;
            return this;
        }

        public void makeroom(int nbefore, int nafter) {
            if (value != null) {
                String s = value;
                value = null;
                length = s.length();
                offset = nbefore;
                bytes = new char[nbefore + length + nafter];
                System.arraycopy(s.toCharArray(), 0, bytes, offset, length);
            } else if (offset + length + nafter > bytes.length || offset < nbefore) {
                int n = nbefore + length + nafter;
                int m = n < 32 ? 32 : n < length * 2 ? length * 2 : n;
                realloc(m, nbefore == 0 ? 0 : m - length - nafter);
            }
        }

        private void realloc(int newSize, int newOffset) {
            if (newSize != bytes.length) {
                char[] newBytes = new char[newSize];
                System.arraycopy(bytes, offset, newBytes, newOffset, length);
                bytes = newBytes;
                offset = newOffset;
            }
        }

    }

    public interface MatchFunc {
        String invoke(String[] r);
    }
}
