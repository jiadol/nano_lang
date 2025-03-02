package lexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

public class Lexer {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;

    private static final Map<String, TokenType> keywords;
    static {
        keywords = new HashMap<>();
        keywords.put("if", TokenType.IF);
        keywords.put("else", TokenType.ELSE);
        keywords.put("elif", TokenType.ELIF);
        keywords.put("while", TokenType.WHILE);
        keywords.put("for", TokenType.FOR);
        keywords.put("def", TokenType.DEF);
        keywords.put("return", TokenType.RETURN);
        keywords.put("true", TokenType.TRUE);
        keywords.put("false", TokenType.FALSE);
        keywords.put("None", TokenType.NONE);
        keywords.put("and", TokenType.AND);
        keywords.put("or", TokenType.OR);
        keywords.put("in", TokenType.IN);
    }

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line));
        return tokens;
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(': addToken(TokenType.LEFT_PAREN); break;
            case ')': addToken(TokenType.RIGHT_PAREN); break;
            case '{': addToken(TokenType.LEFT_BRACE); break;
            case '}': addToken(TokenType.RIGHT_BRACE); break;
            case '[': addToken(TokenType.LEFT_BRACKET); break;
            case ']': addToken(TokenType.RIGHT_BRACKET); break;
            case ',': addToken(TokenType.COMMA); break;
            case '.': addToken(TokenType.DOT); break;
            case '-':
                if (match('>')) {
                    addToken(TokenType.ARROW); // ->
                } else {
                    addToken(TokenType.MINUS);
                }
                break;
            case '+': addToken(TokenType.PLUS); break;
            case ';': addToken(TokenType.SEMICOLON); break;
            case '*': addToken(TokenType.STAR); break;
            case '?': addToken(TokenType.QUESTION); break;
            case ':':
                if (match(':')) {
                    // Double-colon '::'
                    addToken(TokenType.COLON_COLON);
                } else {
                    addToken(TokenType.COLON);
                }
                break;
            case '!':
                addToken(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
                break;
            case '=':
                addToken(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
                break;
            case '<':
                addToken(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
                break;
            case '>':
                addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
                break;
            case '&':
                // optional support for '&&'
                // we can interpret single '&' as error or not used
                if (match('&')) {
                    addToken(TokenType.AND);
                } else {
                    System.err.println("Unexpected character '&' at line " + line);
                }
                break;
            case '|':
                // optional for '||'
                if (match('|')) {
                    addToken(TokenType.OR);
                } else {
                    System.err.println("Unexpected character '|' at line " + line);
                }
                break;
            case '/':
                if (match('/')) {
                    // comment until end of line
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(TokenType.SLASH);
                }
                break;
            case '#':
                // also treat '#' as a comment
                while (peek() != '\n' && !isAtEnd()) advance();
                break;
            case ' ':
            case '\r':
            case '\t':
                break; // ignore whitespace
            case '\n':
                line++;
                break;
            case '"':
                string();
                break;
            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    System.err.println("Unexpected character '" + c + "' at line " + line);
                }
                break;
        }
    }

    private char advance() {
        current++;
        return source.charAt(current - 1);
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;
        current++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    private boolean isDigit(char c) {
        return (c >= '0' && c <= '9');
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private void string() {
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && peek() != '"') {
            // handle escape or simply read
            char c = advance();
            if (c == '\n') line++;
            if (c == '\\') {
                if (!isAtEnd()) {
                    char nxt = advance();
                    switch (nxt) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        default:
                            // unrecognized escape
                            sb.append('\\').append(nxt);
                            break;
                    }
                }
            } else {
                sb.append(c);
            }
        }
        if (isAtEnd()) {
            System.err.println("Unterminated string at line " + line);
        } else {
            advance(); // consume closing quote
        }
        addToken(TokenType.STRING, sb.toString());
    }

    private void number() {
        while (isDigit(peek())) advance();

        if (peek() == '.' && isDigit(peekNext())) {
            advance();
            while (isDigit(peek())) advance();
        }

        String numStr = source.substring(start, current);
        try {
            BigDecimal val = new BigDecimal(numStr);
            addToken(TokenType.NUMBER, val);
        } catch (NumberFormatException e) {
            System.err.println("Invalid numeric literal '" + numStr + "' at line " + line);
            addToken(TokenType.NUMBER, BigDecimal.ZERO);
        }
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();
        String text = source.substring(start, current);
        TokenType type = keywords.getOrDefault(text, TokenType.IDENTIFIER);
        addToken(type);
    }
}
