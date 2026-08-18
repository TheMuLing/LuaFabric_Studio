package com.luafabric.studio.falling.langs.lua

enum class Tokens {
    LONG_COMMENT_COMPLETE,
    LONG_COMMENT_INCOMPLETE,
    LINE_COMMENT,
    CHARACTER_LITERAL,
    WHITESPACE,
    NEWLINE,
    UNKNOWN,
    EOF,
    IDENTIFIER,
    STRING,
    NUMBER,
    FUNCTION,
    IMPORT,
    REQUIRE,
    TRUE,
    AT,
    FALSE,
    IF,
    THEN,
    ELSE,
    ELSEIF,
    END,
    FOR,
    IN,
    LOCAL,
    REPEAT,
    RETURN,
    BREAK,
    UNTIL,
    WHILE,
    DO,
    FUNCTION_NAME,
    GOTO,
    NIL,
    NOT,
    AND,
    OR,
    EQ,
    NEQ,
    LT,
    GT,
    LEQ,
    GEQ,
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    POW,
    ASSGN,
    SEMICOLON,
    COMMA,
    COLON,
    DOT,
    LBRACK,
    RBRACK,
    LPAREN,
    RPAREN,
    LBRACE,
    RBRACE,
    SWITCH,
    CONTINUE,
    CASE,
    DEFAULT,
    TRY,
    FINALLY,
    CATCH,
    XOR,
    QUESTION,
    EQEQ,
    LTEQ,
    GTEQ,
    DOTEQ,
    LTLT,
    LTGT,
    CLT,
    AEQ,
    GTGT,
    ARROW,
    OP,
    CALL,
    COLLECTGARBAGE,
    COMPILE,
    COROUTINE,
    ASSERT,
    ERROR,
    IPAIRS,
    PAIRS,
    NEXT,
    PRINT,
    RAWEQUAL,
    RAWGET,
    RAWSET,
    SELECT,
    SETMETATABLE,
    GETMETATABLE,
    TONUMBER,
    TOSTRING,
    TYPE,
    UNPACK,
    LAMBDA,
    NEWCLASS,
    _G,
    LONG_STRING,
    LONG_STRING_INCOMPLETE,
    CLASS_NAME,
    DEFER,
    WHEN,
    HEX_COLOR, // 新增：十六进制颜色

    // 三字符运算符
    ARROW_LEFT_LONG,      // <--
    ARROW_RIGHT_LONG,     // -->
    SPACESHIP,            // <=>
    DOT_DOT_EQ,           // ..=
    DOT_DOT_LT,           // ..<
    QUESTION_DOT_DOT,     // ?..
    EQEQEQ,               // ===
    NEQEQ,                // !==
    SLASH_STAR_STAR,      // /**
    HASH_HASH_HASH,       // ###
    SLASH_SLASH_EQ,       // //=
    GTGT_EQ,              // >>=
    LTLT_EQ,              // <<=

    // 两字符运算符
    NOT_NOT,              // !!
    NULL_COALESCING,      // ??
    STAR_STAR,            // **
    TILDE_TILDE,          // ~~
    CARET_CARET,          // ^^
    HASH_HASH,            // ##
    AT_AT,                // @@
    DOLLAR_DOLLAR,        // $$
    COLON_EQ,             // :=
    EQ_COLON,             // =:
    QUESTION_DOT,         // ?.
    QUESTION_COLON,       // ?:
    QUESTION_EQ,          // ?=
    QUESTION_MINUS,       // ?-
    QUESTION_PLUS,        // ?+
    ARROW_LEFT,           // <-
    TILDE_EQ,             // ~=
    FAT_ARROW,            // =>
    PLUS_PLUS,            // ++
    PLUS_EQ,              // +=
    MINUS_EQ,             // -=
    STAR_EQ,              // *=
    SLASH_EQ,             // /=
    PERCENT_EQ,           // %=
    AMP_EQ,               // &=
    BAR_EQ,               // |=
    CARET_EQ,             // ^=
    EQ_LT,                // =<
    COLON_COLON,          // ::
    DOT_DOT,              // ..
    SLASH_SLASH,          // //
    BACKSLASH_BACKSLASH,  // \\
    SLASH_STAR,           // /*
    STAR_SLASH,           // */
    MINUS_BAR,            // -|
    BAR_GT,               // |>
    LT_BAR,                // <|
}