package com.myopicmobile.textwarrior.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;

/**
 * Created by nirenr on 2019/12/1.
 */

public class llex {
    /* ORDER RESERVED */
    final static String[] luaX_tokens = {
            "and", "break", "case", "continue", "default", "do", "else", "elseif",
            "end", "false", "for", "function", "goto", "if",
            "in", "local", "nil", "not", "or", "repeat",
            "return", "switch", "then", "true", "until", "while",
            "..", "...", "==", ">=", "<=", "~=", "//", "<<", ">>",
            "::", "<eos>", "<number>", "<name>", "<string>", "<eof>",
    };
    final static int
            /* terminal symbols denoted by reserved words */
            TK_AND = 257, TK_BREAK = 258, TK_CASE = 259, TK_CONTINUE = 260, TK_DEFAULT = 261, TK_DO = 262, TK_ELSE = 263, TK_ELSEIF = 264,
            TK_END = 265, TK_FALSE = 266, TK_FOR = 267, TK_FUNCTION = 268, TK_GOTO = 269, TK_IF = 270,
            TK_IN = 271, TK_LOCAL = 272, TK_NIL = 273, TK_NOT = 274, TK_OR = 275, TK_REPEAT = 276,
            TK_RETURN = 277, TK_SWITCH = 278, TK_THEN = 279, TK_TRUE = 280, TK_UNTIL = 281, TK_WHILE = 282,
    /* other terminal symbols */
    TK_CONCAT = 283, TK_DOTS = 284, TK_EQ = 285, TK_GE = 286, TK_LE = 287, TK_NE = 288,
            TK_IDIV = 289, TK_SHL = 290, TK_SHR = 291,
            TK_DBCOLON = 292, TK_EOS = 293, TK_NUMBER = 294, TK_NAME = 295, TK_STRING = 296, TK_COMMENT = 297;
    final static int FIRST_RESERVED = TK_AND;
    final static int NUM_RESERVED = TK_WHILE + 1 - FIRST_RESERVED;

    final static Hashtable<String, Integer> RESERVED = new Hashtable<>();

    private static final int UCHAR_MAX = 255;
    private static final int EOZ = (-1);
    private static final int MAX_INT = Integer.MAX_VALUE - 2;
    private final static int[] luai_ctype_ = {
            0x00,  /* EOZ */
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,    /* 0. */
            0x00, 0x08, 0x08, 0x08, 0x08, 0x08, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,    /* 1. */
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x0c, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,    /* 2. */
            0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,
            0x16, 0x16, 0x16, 0x16, 0x16, 0x16, 0x16, 0x16,    /* 3. */
            0x16, 0x16, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,
            0x04, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x05,    /* 4. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* 5. */
            0x05, 0x05, 0x05, 0x04, 0x04, 0x04, 0x04, 0x05,
            0x04, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x05,    /* 6. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* 7. */
            0x05, 0x05, 0x05, 0x04, 0x04, 0x04, 0x04, 0x00,

            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* e. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* e. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* e. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* e. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,


            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,    /* c. */
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,    /* d. */
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,


            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,    /* e. */
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,


            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,    /* f. */
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    };
    private final static int ALPHABIT = 0, DIGITBIT = 1, PRINTBIT = 2, SPACEBIT = 3, XDIGITBIT = 4;

    static {
        for (int i = 0; i < NUM_RESERVED; i++) {
            RESERVED.put(luaX_tokens[i], FIRST_RESERVED + i);
        }
    }

    final Token t = new Token();  /* current token */
    final Token lookahead = new Token();  /* look ahead token */
    int currentidx;  /* current character (charint) */
    int current;  /* current character (charint) */
    int linenumber;  /* input line counter */
    int lastline;  /* line of last token `consumed' */
    InputStream z;  /* input stream */
    char[] buff;  /* buffer for tokens */
    int nbuff; /* length of buffer */
    byte decpoint;  /* locale decimal point */
    private final int LUA_COMPAT_LSTR = 1;
    private int lastidx;

    private static int MASK(int B) {
        return (1 << (B));
    }

    public llex(InputStream in) {
        z = in;
    }

    public llex(CharSequence in) {
        z = new CharInputSteam(in);
    }

    private void lexerror(String s, int tkEos) {

    }

    private void syntaxerror(String s) {

    }

    private void _assert(boolean b) {

    }

    static int[] realloc(int[] v, int n) {
        int[] a = new int[n];
        if (v != null)
            System.arraycopy(v, 0, a, 0, Math.min(v.length, n));
        return a;
    }

    static char[] realloc(char[] v, int n) {
        char[] a = new char[n];
        if (v != null)
            System.arraycopy(v, 0, a, 0, Math.min(v.length, n));
        return a;
    }

    private boolean testprop(int c, int p) {
        return (luai_ctype_[(c) + 1] & (p)) != 0;
    }

    private boolean isalnum(int c) {
        return testprop(c, (MASK(ALPHABIT) | MASK(DIGITBIT)));
    }

    private boolean isalpha(int c) {
        return testprop(c, MASK(ALPHABIT));
    }

    private boolean isdigit(int c) {
        return (c >= '0' && c <= '9');
    }

    private boolean isxdigit(int c) {
        return testprop(c, MASK(XDIGITBIT));
    }

    private boolean isspace(int c) {
        return testprop(c, MASK(SPACEBIT));
    }

    boolean currIsNewline() {
        return current == '\n' || current == '\r';
    }

    void inclinenumber() {
        int old = current;
        _assert(currIsNewline());
        nextChar(); /* skip '\n' or '\r' */
        if (currIsNewline() && current != old)
            nextChar(); /* skip '\n\r' or '\r\n' */
        if (++linenumber >= MAX_INT)
            syntaxerror("chunk has too many lines");
    }


    void nextChar() {
        try {
            current = z.read();
            currentidx++;
        } catch (IOException e) {
            e.printStackTrace();
            current = EOZ;
        }
    }

    void save_and_next() {
        save(current);
        nextChar();
    }

    void save(int c) {
        if (buff == null || nbuff + 1 > buff.length)
            buff = realloc(buff, nbuff * 2 + 1);
        buff[nbuff++] = (char) c;
    }

    String newstring(char[] chars, int offset, int len) {
        String s = new String(chars, offset, len);
        currentidx -= len;
        currentidx += s.length();
        return s;
    }

    boolean check_next(String set) {
        if (set.indexOf(current) < 0)
            return false;
        save_and_next();
        return true;
    }

    void buffreplace(char from, char to) {
        int n = nbuff;
        char[] p = buff;
        while ((--n) >= 0)
            if (p[n] == from)
                p[n] = to;
    }

    Number strx2number(String str, SemInfo seminfo) {
        char[] c = str.toCharArray();
        int s = 0;
        while (s < c.length && isspace(c[s]))
            ++s;
        // Check for negative sign
        double sgn = 1.0;
        if (s < c.length && c[s] == '-') {
            sgn = -1.0;
            ++s;
        }
        /* Check for "0x" */
        if (s + 2 >= c.length)
            return 0;
        if (c[s++] != '0')
            return 0;
        if (c[s] != 'x' && c[s] != 'X')
            return 0;
        ++s;

        // read integer part.
        double m = 0;
        int e = 0;
        while (s < c.length && isxdigit(c[s]))
            m = (m * 16) + hexvalue(c[s++]);
        if (s < c.length && c[s] == '.') {
            ++s;  // skip dot
            while (s < c.length && isxdigit(c[s])) {
                m = (m * 16) + hexvalue(c[s++]);
                e -= 4;  // Each fractional part shifts right by 2^4
            }
        }
        if (s < c.length && (c[s] == 'p' || c[s] == 'P')) {
            ++s;
            int exp1 = 0;
            boolean neg1 = false;
            if (s < c.length && c[s] == '-') {
                neg1 = true;
                ++s;
            }
            while (s < c.length && isdigit(c[s]))
                exp1 = exp1 * 10 + c[s++] - '0';
            if (neg1)
                exp1 = -exp1;
            e += exp1;
        }
        return sgn * m * Math.pow(2.0, e);
    }

    boolean str2d(String str, SemInfo seminfo) {
        if (str.indexOf('n') >= 0 || str.indexOf('N') >= 0)
            seminfo.r = 0;
        else if (str.indexOf('x') >= 0 || str.indexOf('X') >= 0)
            seminfo.r = strx2number(str, seminfo);
        else
            seminfo.r = Double.parseDouble(str.trim());
        return true;
    }

    void read_numeral(SemInfo seminfo) {
        String expo = "Ee";
        int first = current;
        _assert(isdigit(current));
        save_and_next();
        if (first == '0' && check_next("Xx"))
            expo = "Pp";
        while (true) {
            if (check_next(expo))
                check_next("+-");
            if (isxdigit(current) || current == '.')
                save_and_next();
            else
                break;
        }
        save('\0');
        String str = new String(buff, 0, nbuff);
        str2d(str, seminfo);
    }

    int skip_sep() {
        int count = 0;
        int s = current;
        _assert(s == '[' || s == ']');
        save_and_next();
        while (current == '=') {
            save_and_next();
            count++;
        }
        return (current == s) ? count : (-count) - 1;
    }

    void read_long_string(SemInfo seminfo, int sep) {
        int cont = 0;
        save_and_next(); /* skip 2nd `[' */
        if (currIsNewline()) /* string starts with a newline? */
            inclinenumber(); /* skip it */
        for (boolean endloop = false; !endloop; ) {
            switch (current) {
                case EOZ:
                    lexerror((seminfo != null) ? "unfinished long string"
                            : "unfinished long comment", TK_EOS);
                    break; /* to avoid warnings */
                case '[': {
                    if (skip_sep() == sep) {
                        save_and_next(); /* skip 2nd `[' */
                        cont++;
                        if (LUA_COMPAT_LSTR == 1) {
                            if (sep == 0)
                                lexerror("nesting of [[...]] is deprecated", '[');
                        }
                    }
                    break;
                }
                case ']': {
                    if (skip_sep() == sep) {
                        save_and_next(); /* skip 2nd `]' */
                        if (LUA_COMPAT_LSTR == 2) {
                            cont--;
                            if (sep == 0 && cont >= 0)
                                break;
                        }
                        endloop = true;
                    }
                    break;
                }
                case '\n':
                case '\r': {
                    save('\n');
                    inclinenumber();
                    if (seminfo == null)
                        nbuff = 0; /* avoid wasting space */
                    break;
                }
                default: {
                    if (seminfo != null)
                        save_and_next();
                    else
                        nextChar();
                }
            }
        }
        if (seminfo != null)
            seminfo.ts = newstring(buff, 2 + sep, nbuff - 2 * (2 + sep));
    }

    int hexvalue(int c) {
        return c <= '9' ? c - '0' : c <= 'F' ? c + 10 - 'A' : c + 10 - 'a';
    }

    int readhexaesc() {
        nextChar();
        int c1 = current;
        nextChar();
        int c2 = current;
        if (!isxdigit(c1) || !isxdigit(c2))
            lexerror("hexadecimal digit expected 'x" + ((char) c1) + ((char) c2), TK_STRING);
        return (hexvalue(c1) << 4) + hexvalue(c2);
    }

    void read_string(int del, SemInfo seminfo) {
        save_and_next();
        while (current != del) {
            switch (current) {
                case EOZ:
                    lexerror("unfinished string", TK_EOS);
                    continue; /* to avoid warnings */
                case '\n':
                case '\r':
                    lexerror("unfinished string", TK_STRING);
                    continue; /* to avoid warnings */
                case '\\': {
                    int c;
                    nextChar(); /* do not save the `\' */
                    switch (current) {
                        case 'a': /* bell */
                            c = '\u0007';
                            break;
                        case 'b': /* backspace */
                            c = '\b';
                            break;
                        case 'f': /* form feed */
                            c = '\f';
                            break;
                        case 'n': /* newline */
                            c = '\n';
                            break;
                        case 'r': /* carriage return */
                            c = '\r';
                            break;
                        case 't': /* tab */
                            c = '\t';
                            break;
                        case 'v': /* vertical tab */
                            c = '\u000B';
                            break;
                        case 'x':
                            c = readhexaesc();
                            break;
                        case '\n': /* go through */
                        case '\r':
                            save('\n');
                            inclinenumber();
                            continue;
                        case EOZ:
                            continue; /* will raise an error next loop */
                        case 'z': {  /* zap following span of spaces */
                            nextChar();  /* skip the 'z' */
                            while (isspace(current)) {
                                if (currIsNewline()) inclinenumber();
                                else nextChar();
                            }
                            continue;
                        }
                        default: {
                            if (!isdigit(current))
                                save_and_next(); /* handles \\, \", \', and \? */
                            else { /* \xxx */
                                int i = 0;
                                c = 0;
                                do {
                                    c = 10 * c + (current - '0');
                                    nextChar();
                                } while (++i < 3 && isdigit(current));
                                if (c > UCHAR_MAX)
                                    lexerror("escape sequence too large", TK_STRING);
                                save(c);
                            }
                            continue;
                        }
                    }
                    save(c);
                    nextChar();
                    continue;
                }
                default:
                    save_and_next();
            }
        }
        save_and_next(); /* skip delimiter */
        seminfo.ts = newstring(buff, 1, nbuff - 2);
    }


    int llex(SemInfo seminfo) {
        nbuff = 0;
        while (true) {
            switch (current) {
                case '\n':
                case '\r': {
                    inclinenumber();
                    continue;
                }
                case '-': {
                    nextChar();
                    if (current != '-')
                        return '-';
                    /* else is a comment */
                    nextChar();
                    if (current == '[') {
                        int sep = skip_sep();
                        if (sep >= 0) {
                            read_long_string(seminfo, sep); /* long comment */
                            return TK_COMMENT;
                        }
                    }
                    /* else short comment */
                    while (!currIsNewline() && current != EOZ)
                        save_and_next();
                    newstring(buff, 0, nbuff);
                    return TK_COMMENT;
                }
                case '[': {
                    int sep = skip_sep();
                    if (sep >= 0) {
                        read_long_string(seminfo, sep);
                        return TK_STRING;
                    } else if (sep == -1)
                        return '[';
                    else
                        lexerror("invalid long string delimiter", TK_STRING);
                }
                case '=': {
                    nextChar();
                    if (current != '=')
                        return '=';
                    else {
                        nextChar();
                        return TK_EQ;
                    }
                }
                case '<': {
                    nextChar();
                    if (current == '<') {
                        nextChar();
                        return TK_SHL;
                    }
                    if (current != '=')
                        return '<';
                    else {
                        nextChar();
                        return TK_LE;
                    }
                }
                case '>': {
                    nextChar();
                    if (current == '>') {
                        nextChar();
                        return TK_SHR;
                    }
                    if (current != '=')
                        return '>';
                    else {
                        nextChar();
                        return TK_GE;
                    }
                }
                case '/': {
                    nextChar();
                    if (current != '/')
                        return '/';
                    else {
                        nextChar();
                        return TK_IDIV;
                    }
                }
                case '~': {
                    nextChar();
                    if (current != '=')
                        return '~';
                    else {
                        nextChar();
                        return TK_NE;
                    }
                }
                case ':': {
                    nextChar();
                    if (current != ':')
                        return ':';
                    else {
                        nextChar();
                        return TK_DBCOLON;
                    }
                }
                case '"':
                case '\'': {
                    read_string(current, seminfo);
                    return TK_STRING;
                }
                case '.': {
                    save_and_next();
                    if (check_next(".")) {
                        if (check_next("."))
                            return TK_DOTS; /* ... */
                        else
                            return TK_CONCAT; /* .. */
                    } else if (!isdigit(current))
                        return '.';
                    else {
                        read_numeral(seminfo);
                        return TK_NUMBER;
                    }
                }
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9': {
                    read_numeral(seminfo);
                    return TK_NUMBER;
                }
                case EOZ: {
                    return TK_EOS;
                }
                default: {
                    if (isspace(current)) {
                        _assert(!currIsNewline());
                        nextChar();
                        continue;
                    } else if (isdigit(current)) {
                        read_numeral(seminfo);
                        return TK_NUMBER;
                    } else if (isalpha(current) || current == '_') {
                        /* identifier or reserved word */
                        String ts;
                        do {
                            save_and_next();
                        } while (isalnum(current));
                        ts = newstring(buff, 0, nbuff);
                        if (RESERVED.containsKey(ts))
                            return RESERVED.get(ts);
                        else {
                            seminfo.ts = ts;
                            return TK_NAME;
                        }
                    } else {
                        int c = current;
                        nextChar();
                        return c; /* single-char tokens (+ - / ...) */
                    }
                }
            }
        }
    }

    public void next() {
        lastline = linenumber;
        lastidx = currentidx;
        t.token = llex(t.seminfo); /* read next token */
    }


    /* semantics information */
    private static class SemInfo {
        Number r;
        String ts;
    }

    private static class Token {
        final SemInfo seminfo = new SemInfo();
        int token;

        public void set(Token other) {
            this.token = other.token;
            this.seminfo.r = other.seminfo.r;
            this.seminfo.ts = other.seminfo.ts;
        }
    }

    public static class CharInputSteam extends InputStream {

        private final CharSequence mSrc;
        private final int mLen;
        private int idx = 0;

        public CharInputSteam(CharSequence src) {
            mSrc = src;
            mLen = src.length();
        }

        @Override
        public int read() throws IOException {
            idx++;
            if (idx > mLen)
                return -1;
            return mSrc.charAt(idx - 1);
        }
    }

}
