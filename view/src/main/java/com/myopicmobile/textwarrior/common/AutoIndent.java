package com.myopicmobile.textwarrior.common;

import com.androlua.LuaLexer;
import com.androlua.LuaTokenTypes;

public class AutoIndent {
    public static int createAutoIndent(CharSequence text) {
        LuaLexer lexer = new LuaLexer(text);
        int idt = 0;
        try {
            while (true) {
                LuaTokenTypes type = lexer.advance();
                if (type == null) {
                    break;
                }
                if (lexer.yytext().equals("switch"))
                    idt += 1;
                else
                    idt += indent(type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return idt;
    }

    private static int indent(LuaTokenTypes t) {
        switch (t) {
            case FOR:
            case WHILE:
            case FUNCTION:
            case IF:
            case REPEAT:
            case LCURLY:
            case SWITCH:
            case WHEN:
            case CATCH:
            case FINALLY:
            case TRY:
            case DEFER:
                return 1;
            case DO:
                // DO 仅在行首成对出现（do ... end），
                // 行中（for/while ... do）另加一档会造成双重缩进，故此处恒为 0，
                // 行首 DO 的 +1 在 format() 中单独处理
                return 0;
            case UNTIL:
            case END:
            case RCURLY:
                return -1;
            default:
                return 0;
        }
    }

    public static CharSequence format(CharSequence text, int width) {
        StringBuilder builder = new StringBuilder();
        boolean isNewLine = true;
        LuaLexer lexer = new LuaLexer(text);
        try {
            int idt = 0;

            while (true) {
                LuaTokenTypes type = lexer.advance();
                if (type == null)
                    break;

                if (type == LuaTokenTypes.NEW_LINE) {
                    if (builder.length() > 0 && builder.charAt(builder.length() - 1) == ' ')
                        builder.deleteCharAt(builder.length() - 1);
                    isNewLine = true;
                    builder.append('\n');
                    idt = Math.max(0, idt);
                } else if (isNewLine) {
                    switch (type) {
                        case WHITE_SPACE:
                            break;
                        case ELSE:
                        case ELSEIF:
                        case CASE:
                        case DEFAULT:
                        case CATCH:
                        case FINALLY:
                            builder.append(createIndent(idt * width - width / 2));
                            builder.append(lexer.yytext());
                            isNewLine = false;
                            break;
                        case DOUBLE_COLON:
                        case AT:
                            // label 行：按当前块级缩进输出，不再顶格
                            builder.append(createIndent(idt * width));
                            builder.append(lexer.yytext());
                            isNewLine = false;
                            break;
                        case DO:
                            // 行首 DO（do ... end 块）：正常缩进并 开栈 +1，
                            // 由随后的 END 平衡；行中 for/while 的 DO 走默认分支保持 0
                            builder.append(createIndent(idt * width));
                            builder.append(lexer.yytext());
                            idt += 1;
                            isNewLine = false;
                            break;
                        case END:
                        case UNTIL:
                        case RCURLY:
                            idt--;
                            builder.append(createIndent(idt * width));
                            builder.append(lexer.yytext());
                            isNewLine = false;
                            break;
                        default:
                            builder.append(createIndent(idt * width));
                            builder.append(lexer.yytext());
                            idt += indent(type);
                            isNewLine = false;
                            break;
                    }
                } else if (type == LuaTokenTypes.WHITE_SPACE) {
                    builder.append(' ');
                } else {
                    builder.append(lexer.yytext());
                    idt += indent(type);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return builder;
    }

    private static char[] createIndent(int n) {
        if (n < 0)
            return new char[0];
        char[] idts = new char[n];
        for (int i = 0; i < n; i++)
            idts[i] = ' ';
        return idts;
    }
}