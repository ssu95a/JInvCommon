package ru.inversion.dataset.parser.lexer.recognizer;

import ru.inversion.dataset.parser.lexer.TokenRecognizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Small fixed lexer rule sets used by sparser.
 * They intentionally do not cover the complete Oracle/PostgreSQL grammar.
 */
public final class RecognizerSets {
    private RecognizerSets() { }

    public static List<TokenRecognizer> oracle() {
        return oracle(false);
    }

    public static List<TokenRecognizer> oracle(boolean call) {
        List<TokenRecognizer> rules = new ArrayList<>(Arrays.asList(
                new CommonRecognizers.Whitespace(),
                new CommonRecognizers.LineComment(),
                new ParameterizeDirectiveRecognizer(),
                new OptimizerHintRecognizer(false),
                new CommonRecognizers.BlockComment(false)
        ));
        if( !call )
            rules.add(new UnsupportedQuotedLiteralRecognizer(true));
        rules.addAll(Arrays.asList(
                new CommonRecognizers.StringLiteral(),
                new CommonRecognizers.QuotedIdentifier(),
                new OperatorRecognizer(false, call),
                new CommonRecognizers.Parameter(true, call),
                new NumberRecognizer(),
                new CommonRecognizers.Word(true),
                new CommonRecognizers.Symbol(call)
        ));
        return rules;
    }

    public static List<TokenRecognizer> postgres() {
        return postgres(false);
    }

    public static List<TokenRecognizer> postgres(boolean procedural) {
        return Arrays.<TokenRecognizer>asList(
                new CommonRecognizers.Whitespace(),
                new CommonRecognizers.LineComment(),
                new ParameterizeDirectiveRecognizer(),
                new OptimizerHintRecognizer(true),
                new CommonRecognizers.BlockComment(true),
                procedural ? new PostgresRecognizers.QuotedLiteral()
                           : new UnsupportedQuotedLiteralRecognizer(false),
                new PostgresRecognizers.NumberedParameter(),
                new CommonRecognizers.StringLiteral(),
                new CommonRecognizers.QuotedIdentifier(),
                new OperatorRecognizer(true),
                new CommonRecognizers.Parameter(false, false),
                new NumberRecognizer(),
                new CommonRecognizers.Word(false),
                new CommonRecognizers.Symbol(procedural)
        );
    }
}
