package ru.inversion.dataset.sql;

import ru.inversion.dataset.IParameters;
import ru.inversion.utils.parser.sql.transform.PreparedSql;
import ru.inversion.utils.parser.sql.transform.SqlBindingKind;
import ru.inversion.utils.parser.sql.transform.SqlParameterBinding;
import ru.inversion.utils.Jt2St;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Разрешает значения {@link PreparedSql} из параметров DataSet.
 *
 * <p>Результат расположен в физическом JDBC-порядке:
 * первый элемент предназначен для позиции {@code 1}, второй —
 * для позиции {@code 2} и так далее.</p>
 *
 * <p>Индексированные параметры {@link IParameters} имеют
 * нумерацию с нуля, тогда как {@link SqlParameterBinding}
 * хранит исходную позицию с единицы. Поэтому позиция
 * {@code sourcePosition == 1} читается через
 * {@code parameters.getParameter(0)}.</p>
 *
 * <p>Каждое значение преобразуется через {@link Jt2St#convert(Object)},
 * как и в существующем {@code JDBCDataReader}.</p>
 */
public final class DataSetPreparedSqlResolver {

   private static final BigInteger INTEGER_MIN =
           BigInteger.valueOf(Integer.MIN_VALUE);

   private static final BigInteger INTEGER_MAX =
           BigInteger.valueOf(Integer.MAX_VALUE);

   private static final BigInteger LONG_MIN =
           BigInteger.valueOf(Long.MIN_VALUE);

   private static final BigInteger LONG_MAX =
           BigInteger.valueOf(Long.MAX_VALUE);

   private DataSetPreparedSqlResolver() {
   }

   /**
    * Возвращает JDBC-значения для полного binding plan.
    *
    * <p>Методы {@code IParameters.hasParameter(...)} намеренно
    * не вызываются. Это сохраняет существующий контракт DataSet:
    * отсутствующее значение и явно установленный {@code null}
    * связываются как JDBC {@code null}, если реализация
    * {@link IParameters} не различает эти состояния.</p>
    *
    * @param preparedSql SQL и полный план привязки
    * @param parameters  параметры DataSet
    *
    * @return неизменяемый список преобразованных JDBC-значений
    */
   public static List<Object> resolve(
           PreparedSql preparedSql,
           IParameters parameters
   ) {
      Objects.requireNonNull(
              preparedSql,
              "preparedSql"
      );

      Objects.requireNonNull(
              parameters,
              "parameters"
      );

      List<SqlParameterBinding> bindings =
              preparedSql.bindings();

      List<Object> result =
              new ArrayList<Object>(
                      bindings.size()
              );

      for (SqlParameterBinding binding : bindings) {
         Object value =
                 resolveRawValue(
                         binding,
                         parameters
                 );

         result.add(
                 Jt2St.convert(value)
         );
      }

      return Collections.unmodifiableList(result);
   }

   private static Object resolveRawValue(
           SqlParameterBinding binding,
           IParameters parameters
   ) {
      SqlBindingKind kind =
              binding.kind();

      switch (kind) {
         case JDBC_POSITIONAL:
         case NUMBERED_POSITIONAL:
            return positionalValue(
                    binding,
                    parameters
            );

         case NAMED:
            return parameters.getParameter(
                    binding.name()
            );

         case GENERATED_LITERAL:
            return legacyGeneratedLiteral(
                    binding.generatedValue()
            );

         default:
            throw new IllegalStateException(
                    "Unsupported SQL binding kind: "
                            + kind
            );
      }
   }

   /**
    * Сохраняет фактическую типизацию целочисленных литералов
    * существующего DataSet SQLParser.
    *
    * <p>Общий SQL parser хранит целое число без потери точности
    * как {@link BigInteger}. Legacy DataSet parser для обычных
    * значений возвращает более узкие JDBC-типы:
    * {@link Integer}, затем {@link Long}.</p>
    *
    * <p>Преобразование применяется только к литералам,
    * созданным SQL-компилятором. Значения {@link BigInteger},
    * явно переданные пользователем через {@link IParameters},
    * не изменяются.</p>
    */
   private static Object legacyGeneratedLiteral(
           Object value
   ) {
      if (!(value instanceof BigInteger)) {
         return value;
      }

      BigInteger integer =
              (BigInteger) value;

      if (isBetween(
              integer,
              INTEGER_MIN,
              INTEGER_MAX
      )) {
         return Integer.valueOf(
                 integer.intValue()
         );
      }

      if (isBetween(
              integer,
              LONG_MIN,
              LONG_MAX
      )) {
         return Long.valueOf(
                 integer.longValue()
         );
      }

      return integer;
   }

   private static boolean isBetween(
           BigInteger value,
           BigInteger minimum,
           BigInteger maximum
   ) {
      return value.compareTo(minimum) >= 0
              && value.compareTo(maximum) <= 0;
   }

   private static Object positionalValue(
           SqlParameterBinding binding,
           IParameters parameters
   ) {
      Integer sourcePosition =
              binding.sourcePosition();

      if (sourcePosition == null
              || sourcePosition < 1) {

         throw new IllegalStateException(
                 "Invalid source position for JDBC position "
                         + binding.jdbcPosition()
                         + ": "
                         + sourcePosition
         );
      }

      return parameters.getParameter(
              sourcePosition - 1
      );
   }
}
