package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.lexer.TokenRecognizer;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.HINT;

/** Подсказка оптимизатору в блочном SQL-комментарии, начинающемся с /*+. */
final class OptimizerHintRecognizer implements TokenRecognizer {

    private final CommonRecognizers.BlockComment blockComment;

    OptimizerHintRecognizer(boolean nestedComments) {
        this.blockComment = new CommonRecognizers.BlockComment(nestedComments);
    }

    @Override
    public SqlScanner.Token recognize(String sql, int at) {
        if (sql.charAt(at) != '/' || !ScanChars.follows(sql, at, '*')
                || at + 2 >= sql.length() || sql.charAt(at + 2) != '+') {
            return null;
        }
        SqlScanner.Token comment = blockComment.recognize(sql, at);
        return new SqlScanner.Token(HINT, at, comment.getEnd());
    }
}
