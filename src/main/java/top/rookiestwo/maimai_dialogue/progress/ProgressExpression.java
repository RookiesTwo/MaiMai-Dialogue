package top.rookiestwo.maimai_dialogue.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class ProgressExpression {
    public static final Codec<ProgressExpression> CODEC = Codec.STRING.comapFlatMap(
            ProgressExpression::parse,
            ProgressExpression::source
    );

    private final String source;
    private final Node root;

    private ProgressExpression(String source, Node root) {
        this.source = source;
        this.root = root;
    }

    public static DataResult<ProgressExpression> parse(String source) {
        if (source == null || source.isBlank()) {
            return DataResult.error(() -> "Progress expression must not be blank.");
        }

        try {
            Parser parser = new Parser(source);
            Node root = parser.parse();
            return DataResult.success(new ProgressExpression(source, root));
        } catch (ParseException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    public static ProgressExpression parseOrThrow(String source) {
        return parse(source).getOrThrow(IllegalArgumentException::new);
    }

    public String source() {
        return source;
    }

    public boolean evaluate(Set<ProgressNode> nodes) {
        Objects.requireNonNull(nodes, "nodes");
        return evaluate(nodes::contains);
    }

    public boolean evaluate(Predicate<ProgressNode> containsNode) {
        Objects.requireNonNull(containsNode, "containsNode");
        return root.evaluate(containsNode);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ProgressExpression expression
                && source.equals(expression.source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }

    @Override
    public String toString() {
        return source;
    }

    private sealed interface Node permits ProgressNodeNode, NotNode, AndNode, OrNode {
        boolean evaluate(Predicate<ProgressNode> containsNode);
    }

    private record ProgressNodeNode(ProgressNode progressNode) implements Node {
        @Override
        public boolean evaluate(Predicate<ProgressNode> containsNode) {
            return containsNode.test(progressNode);
        }
    }

    private record NotNode(Node operand) implements Node {
        @Override
        public boolean evaluate(Predicate<ProgressNode> containsNode) {
            return !operand.evaluate(containsNode);
        }
    }

    private record AndNode(Node left, Node right) implements Node {
        @Override
        public boolean evaluate(Predicate<ProgressNode> containsNode) {
            return left.evaluate(containsNode) && right.evaluate(containsNode);
        }
    }

    private record OrNode(Node left, Node right) implements Node {
        @Override
        public boolean evaluate(Predicate<ProgressNode> containsNode) {
            return left.evaluate(containsNode) || right.evaluate(containsNode);
        }
    }

    private static final class Parser {
        private final String source;
        private int cursor;

        private Parser(String source) {
            this.source = source;
        }

        private Node parse() throws ParseException {
            Node expression = parseOr();
            skipWhitespace();
            if (!atEnd()) {
                throw error("Unexpected token '" + source.charAt(cursor) + "'");
            }
            return expression;
        }

        private Node parseOr() throws ParseException {
            Node expression = parseAnd();
            while (match("||")) {
                expression = new OrNode(expression, parseAnd());
            }
            return expression;
        }

        private Node parseAnd() throws ParseException {
            Node expression = parseUnary();
            while (match("&&")) {
                expression = new AndNode(expression, parseUnary());
            }
            return expression;
        }

        private Node parseUnary() throws ParseException {
            skipWhitespace();
            if (match("!")) {
                return new NotNode(parseUnary());
            }
            if (match("(")) {
                Node expression = parseOr();
                if (!match(")")) {
                    throw error("Expected ')'");
                }
                return expression;
            }
            return parseProgressNode();
        }

        private Node parseProgressNode() throws ParseException {
            skipWhitespace();
            int start = cursor;
            while (!atEnd() && isNodeCharacter(source.charAt(cursor))) {
                cursor++;
            }
            if (start == cursor) {
                throw error(atEnd()
                        ? "Expected progress node but reached end of expression"
                        : "Expected progress node");
            }

            String value = source.substring(start, cursor);
            DataResult<ProgressNode> parsed = ProgressNode.parse(value);
            return parsed.result()
                    .<Node>map(ProgressNodeNode::new)
                    .orElseThrow(() -> error("Invalid progress node '" + value + "'"));
        }

        private boolean match(String token) {
            skipWhitespace();
            if (!source.startsWith(token, cursor)) {
                return false;
            }
            cursor += token.length();
            return true;
        }

        private void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(source.charAt(cursor))) {
                cursor++;
            }
        }

        private boolean atEnd() {
            return cursor >= source.length();
        }

        private ParseException error(String message) {
            return new ParseException(message + " at index " + cursor
                    + " in '" + source + "'.");
        }

        private static boolean isNodeCharacter(char character) {
            return character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '_'
                    || character == '-'
                    || character == '.';
        }
    }

    private static final class ParseException extends Exception {
        private ParseException(String message) {
            super(message);
        }
    }
}
