package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.TokenRecognizer;
import java.util.Arrays;
import java.util.List;

/** Состав и порядок лексических правил каждого диалекта. */
public final class RecognizerSets {
    private RecognizerSets() { }

    public static List<TokenRecognizer> oracle() {
        return Arrays.<TokenRecognizer>asList(
                new CommonRecognizers.Whitespace(),
                new CommonRecognizers.LineComment(),
                new ParameterizeDirectiveRecognizer(),
                new OptimizerHintRecognizer(false),
                new CommonRecognizers.BlockComment(false),
                new OracleRecognizers.QQuotedString(),
                new OracleRecognizers.NationalString(),
                new CommonRecognizers.StringLiteral(),
                new CommonRecognizers.QuotedIdentifier(),
                new OperatorRecognizer(false),
                new CommonRecognizers.Parameter(true),
                new NumberRecognizer(false),
                new CommonRecognizers.Word(true)
        );
    }

    public static List<TokenRecognizer> postgres() {
        return Arrays.<TokenRecognizer>asList(
                new CommonRecognizers.Whitespace(),
                new CommonRecognizers.LineComment(),
                new ParameterizeDirectiveRecognizer(),
                new OptimizerHintRecognizer(true),
                new CommonRecognizers.BlockComment(true),
                new PostgresRecognizers.EscapeString(),
                new PostgresRecognizers.UnicodeQuoted(),
                new PostgresRecognizers.DollarQuoted(),
                new PostgresRecognizers.NumberedParameter(),
                new CommonRecognizers.StringLiteral(),
                new CommonRecognizers.QuotedIdentifier(),
                new OperatorRecognizer(true),
                new CommonRecognizers.Parameter(false),
                new NumberRecognizer(true),
                new CommonRecognizers.Word(false)
        );
    }
}
