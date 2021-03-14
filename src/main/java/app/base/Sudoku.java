package app.base;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Sudoku {

    public static void main(String[] args) {
        final var times = new ArrayList<Double>(100);
        for (int i = 0; i < 100; i++) {
            final var startTime = System.nanoTime();
            final var solution = solve(new Board());
            times.add((System.nanoTime() - startTime) / Math.pow(10, 9));
            System.out.println(solution.isSolved() ?
                    """
                            Solution found!
                                                        
                            Computation time: %.3fs                                               
                            Board state:
                            %s
                            """.formatted(times.get(i), solution)
                    :
                    """
                            Solution was not found :(
                                                
                            Computation time: %.3fs
                            Board state:
                            %s
                            """.formatted(times.get(i), solution));
        }
        System.out.printf("Average time: %.3fs%n", times
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow());
    }

    private static Board solve(Board board) {
        return Objects.requireNonNull(board)
                .nextUpdates()
                .parallel()
                .map(it -> solve(board.apply(it)))
                .filter(Board::isSolved)
                .findAny()
                .orElse(board);
    }

    static class Board {
        private static final int SQUARE_SIZE = 3;

        private final int rows;
        private final int columns;
        private final Set<Field> fields;
        private final Set<Group> groups;

        Board() {
            this(9, 9);
        }

        Board(int rows, int columns) {
            this.rows = rows;
            this.columns = columns;
            this.fields = Collections.unmodifiableSet(Objects.requireNonNull(generateFields()));
            this.groups = Collections.unmodifiableSet(Objects.requireNonNull(generateGroups()));
        }

        private Board(int rows, int columns, Set<Field> fields, Set<Group> groups) {
            this.rows = rows;
            this.columns = columns;
            this.fields = Collections.unmodifiableSet(Objects.requireNonNull(fields));
            this.groups = Collections.unmodifiableSet(Objects.requireNonNull(groups));
        }

        @Override
        public String toString() {
            return getBoardState();
        }

        boolean isSolved() {
            return getFields()
                    .stream()
                    .allMatch(Field::isFilled);
        }

        Board apply(Update update) {
            final var groups = getGroups()
                    .stream()
                    .filter(it -> it.contains(update.getField()))
                    .collect(Collectors.toUnmodifiableSet());
            // apply new board state
            final var fields = getFields()
                    .stream()
                    .map(updateField(update, groups))
                    .collect(Collectors.toUnmodifiableSet());
            return new Board(getRows(), getColumns(), fields, getGroups());
        }

        Stream<Update> nextUpdates() {
            return getFields()
                    .stream()
                    .filter(Field::isEmpty)
                    .findAny()
                    .stream()
                    .flatMap(field -> field.getPossibleValues()
                            .stream()
                            .map(it -> new Update(field, it)));
        }

        Set<Field> getFields() {
            return fields;
        }

        Set<Group> getGroups() {
            return groups;
        }

        int getRows() {
            return rows;
        }

        int getColumns() {
            return columns;
        }

        private Function<Field, Field> updateField(Update update, Set<Group> groups) {
            return field -> {
                if (field.equals(update.getField())) {
                    return assignValue(update, field);
                } else if (isNeighbour(groups, field)) {
                    return restrictPossibleValues(update, field);
                } else {
                    return field;
                }
            };
        }

        private Field assignValue(Update update, Field current) {
            return new Field(
                    current.getRow(),
                    current.getColumn(),
                    update.getValue(),
                    current.getPossibleValues()
                            .stream()
                            .filter(n -> n != update.getValue())
                            .collect(Collectors.toUnmodifiableSet()));
        }

        private boolean isNeighbour(Set<Group> groups, Field current) {
            return groups
                    .stream()
                    .anyMatch(group -> group.contains(current));
        }

        private Field restrictPossibleValues(Update update, Field current) {
            return new Field(
                    current.getRow(),
                    current.getColumn(),
                    current.getValue(),
                    current.getPossibleValues()
                            .stream()
                            .filter(n -> n != update.getValue())
                            .collect(Collectors.toUnmodifiableSet()));
        }

        private Set<Field> generateFields() {
            final var fields = new HashSet<Field>();
            for (int row = 0; row < getRows(); row++) {
                for (int column = 0; column < getColumns(); column++) {
                    fields.add(new Field(row, column, Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9)));
                }
            }
            return fields;
        }

        private Set<Group> generateGroups() {
            final var groups = new HashSet<Group>();
            for (int xIndex = 0; xIndex < (getColumns() / SQUARE_SIZE); xIndex++) {
                for (int yIndex = 0; yIndex < (getRows() / SQUARE_SIZE); yIndex++) {
                    groups.add(new Group(getSquare(xIndex, yIndex)));
                }
            }
            for (int row = 0; row < getRows(); row++) {
                groups.add(new Group(getRow(row)));
            }
            for (int column = 0; column < getColumns(); column++) {
                groups.add(new Group(getColumn(column)));
            }
            return groups;
        }

        private Set<Field> getRow(int index) {
            return getFields()
                    .stream()
                    .filter(it -> it.getRow() == index)
                    .collect(Collectors.toUnmodifiableSet());
        }

        private Set<Field> getColumn(int index) {
            return getFields()
                    .stream()
                    .filter(it -> it.getColumn() == index)
                    .collect(Collectors.toUnmodifiableSet());
        }

        private Set<Field> getSquare(int xIndex, int yIndex) {
            return getFields()
                    .stream()
                    .filter(it -> it.getColumn() / SQUARE_SIZE == xIndex)
                    .filter(it -> it.getRow() / SQUARE_SIZE == yIndex)
                    .collect(Collectors.toUnmodifiableSet());
        }

        private String getBoardState() {
            return getFields()
                    .stream()
                    .collect(Collectors.groupingBy(Field::getRow))
                    .entrySet()
                    .stream()
                    .sorted(Comparator.comparingInt(Map.Entry::getKey))
                    .map(it -> it.getValue()
                            .stream()
                            .sorted(Comparator.comparingInt(Field::getColumn))
                            .map(Field::toString)
                            .collect(Collectors.joining(" ")))
                    .collect(Collectors.joining("\n"));
        }

        private Optional<Field> getField(int row, int column) {
            return getFields()
                    .stream()
                    .filter(it -> it.getRow() == row)
                    .filter(it -> it.getColumn() == column)
                    .findAny();
        }

        static final class Group {
            private final Set<Field> fields;

            Group(Set<Field> fields) {
                this.fields = Collections.unmodifiableSet(Objects.requireNonNull(fields));
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Group group = (Group) o;

                return getFields().equals(group.getFields());
            }

            @Override
            public int hashCode() {
                return getFields().hashCode();
            }

            boolean contains(Field field) {
                return getFields().contains(field);
            }

            Set<Field> getFields() {
                return fields;
            }
        }

        static final class Field {
            private final int row;
            private final int column;
            private final int value;
            private final Set<Integer> possibleValues;

            Field(int row, int column, Set<Integer> possibleValues) {
                this(row, column, 0, possibleValues);
            }

            Field(int row, int column, int value, Set<Integer> possibleValues) {
                this.row = row;
                this.column = column;
                this.value = value;
                this.possibleValues = Collections.unmodifiableSet(Objects.requireNonNull(possibleValues));
            }

            @Override
            public String toString() {
                return isFilled() ? String.valueOf(getValue()) : "⬜";
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Field field = (Field) o;

                if (getRow() != field.getRow()) return false;
                return getColumn() == field.getColumn();
            }

            @Override
            public int hashCode() {
                int result = getRow();
                result = 31 * result + getColumn();
                return result;
            }

            boolean isFilled() {
                return getValue() > 0;
            }

            boolean isEmpty() {
                return !isFilled();
            }

            int getRow() {
                return row;
            }

            int getColumn() {
                return column;
            }

            int getValue() {
                return value;
            }

            Set<Integer> getPossibleValues() {
                return possibleValues;
            }
        }

        static final class Update {
            private final Field field;
            private final int value;

            Update(Field field, int value) {
                this.field = Objects.requireNonNull(field);
                this.value = value;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Update update = (Update) o;

                if (value != update.value) return false;
                return field.equals(update.field);
            }

            @Override
            public int hashCode() {
                int result = field.hashCode();
                result = 31 * result + value;
                return result;
            }

            Field getField() {
                return field;
            }

            int getValue() {
                return value;
            }
        }
    }

}
