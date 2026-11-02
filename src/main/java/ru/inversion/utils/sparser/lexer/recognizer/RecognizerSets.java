package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.TokenRecognizer;

import java.util.Arrays;
import java.util.List;

/**
 * Small fixed lexer rule sets used by sparser.
 * They intentionally do not cover the complete Oracle/PostgreSQL grammar.
 */
public final class RecognizerSets {
    private RecognizerSets() { }

    public static List<TokenRecognizer> oracle() {
        return Arrays.<TokenRecognizer>asList(
                new CommonRecognizers.Whitespace(),
                new CommonRecognizers.LineComment(),
                new ParameterizeDirectiveRecognizer(),
                new OptimizerHintRecognizer(false),
                new CommonRecognizers.BlockComment(false),
                new CommonRecognizers.StringLiteral(),
                new CommonRecognizers.QuotedIdentifier(),
                new OperatorRecognizer(false),
                new CommonRecognizers.Parameter(true),
                new NumberRecognizer(),
                new CommonRecognizers.Word(true),
                new CommonRecognizers.Symbol()
        );
    }

    public static List<TokenRecognizer> postgres() {
        return Arrays.<TokenRecognizer>asList(
                new CommonRecognizers.Whitespace(),
                new CommonRecognizers.LineComment(),
                new ParameterizeDirectiveRecognizer(),
                new OptimizerHintRecognizer(true),
                new CommonRecognizers.BlockComment(true),
                new PostgresRecognizers.NumberedParameter(),
                new CommonRecognizers.StringLiteral(),
                new CommonRecognizers.QuotedIdentifier(),
                new OperatorRecognizer(true),
                new CommonRecognizers.Parameter(false),
                new NumberRecognizer(),
                new CommonRecognizers.Word(false),
                new CommonRecognizers.Symbol()
        );
    }
}
