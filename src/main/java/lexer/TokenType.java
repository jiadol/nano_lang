package lexer;

public enum TokenType {
    // Single-char tokens
    LEFT_PAREN, RIGHT_PAREN,
    LEFT_BRACE, RIGHT_BRACE,
    LEFT_BRACKET, RIGHT_BRACKET,
    COMMA, DOT, MINUS, PLUS,
    SEMICOLON, SLASH, STAR,
    COLON, QUESTION, // ? and :

    // Double-colon
    COLON_COLON, // '::'

    // One or two character tokens
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,
    ARROW, // ->

    // Identifiers and literals
    IDENTIFIER, STRING, NUMBER,

    // Keywords
    AND, OR,
    DEF, RETURN,
    IF, ELSE, ELIF,
    WHILE, FOR,
    TRUE, FALSE,
    NONE,
    IN,

    // End of file
    EOF
}
