/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mphjutils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author mphj
 */
public class PojoMakerFromSql {

    public static void main(String[] args) {
        try {
            if (args.length != 2) {
                BufferedReader bfr = new BufferedReader(
                        new InputStreamReader(System.in)
                );
                args = new String[2];
                System.out.println("Please inter file path:");
                args[0] = bfr.readLine();
                System.out.println("Please inter output file path:");
                args[1] = bfr.readLine();
            }
            try (
                    InputStream givenFileStream = new FileInputStream(args[0]);
                    BufferedReader givenFileReader = new BufferedReader(
                            new InputStreamReader(givenFileStream)
                    )) {
                String line;
                String tableName = null;
                Writer writer = null;
                JavaBuilder rowMapperClass = JavaBuilder.makeNew();
                JavaBuilder jBuilder = JavaBuilder.makeNew();
                while ((line = givenFileReader.readLine()) != null) {
                    if (line.startsWith("CREATE TABLE")) {
                        tableName = TextFormatter.firstCharToUpper(
                                        TextFormatter.validVarName(
                                                LineHelper.getTableName(line)
                                        )
                                );
                        jBuilder.startJavaClass(
                                tableName
                        );
                        writer = new PrintWriter(
                                PathHelper.combine(args[1], 
                                tableName + ".java")
                        );
                        rowMapperClass.startJavaClass(
                                tableName + "RowMapper");
                        rowMapperClass.strBuilder.append(
                                "	public Object mapRow(ResultSet rs, int rowNum) throws SQLException {\n"
                        );
                        rowMapperClass.strBuilder.append(
                                "    "
                        ).append(
                                tableName
                        ).append(
                                " "
                        ).append(
                                TextFormatter.firstCharToLower(
                                        tableName
                                )
                        ).append(
                                " = new "
                        ).append(
                                tableName
                        ).append(
                                "();\n"
                        );
                    } else if (line.startsWith(")")) {
                        jBuilder.endClass();
                        rowMapperClass.strBuilder.append("return ")
                                .append(
                                        TextFormatter.firstCharToLower(
                                                tableName
                                        )
                                ).append(";\n");
                        rowMapperClass.strBuilder.append("    }\n");
                        rowMapperClass.endClass();
                        writer.write(jBuilder.toString());
                        writer.write("\n\n\n");
                        writer.append(rowMapperClass.toString());
                        writer.close();
                        jBuilder.reset();
                        rowMapperClass.reset();
                    } else if (!jBuilder.isClassClosed()) {
                        if (LineHelper.getCoulmnType(line) == null) {
                            continue;
                        }
                        jBuilder.addVar(
                                LineHelper.getCoulmnType(line),
                                TextFormatter.validVarName(
                                        LineHelper.getCoulmnName(line)
                                )
                        );
                        rowMapperClass.strBuilder
                                .append(
                                        TextFormatter.firstCharToLower(
                                                tableName
                                        )
                                ).append(".set")
                                .append(
                                        TextFormatter.firstCharToUpper(
                                                TextFormatter.validVarName(
                                                        LineHelper.getCoulmnName(line)
                                                )
                                        )
                                ).append("(")
                                .append(LineHelper.getResultSetExtractorByTypeAndName(
                                        LineHelper.getCoulmnType(line),
                                        LineHelper.getCoulmnName(line)
                                ))
                                .append(");\n");
                    }
                }
                writer.close();
            }
        } catch (IOException e) {
            Logger.getLogger(PojoMakerFromSql.class.getName())
                    .log(Level.SEVERE, e.getMessage(), e);
        }
    }

    public static class LineHelper {

        public static String getTableName(String line) {
            line = line.replace("CREATE TABLE", "");
            return line.split("`")[1];
        }

        public static boolean isNullAccepted(String line) {
            return !line.contains("NOT NULL");
        }

        public static Class getCoulmnType(String line) {
            if (line.contains(" int")) {
                return LineHelper.isNullAccepted(line) ? Integer.class : int.class;
            } else if (line.contains(" text") || line.contains(" varchar")) {
                return String.class;
            } else if (line.contains(" tinyint")) {
                return LineHelper.isNullAccepted(line) ? Boolean.class : boolean.class;
            } else if (line.contains(" timestamp")) {
                return Timestamp.class;
            }
            return null;
        }

        public static String getResultSetExtractorByTypeAndName(Class type, String name) {
            StringBuilder stringBuilder = new StringBuilder();
            if (type.equals(Integer.class)) {
                stringBuilder.append("DaoHelper.getInteger(rs, ");
            } else if (type.equals(int.class)) {
                stringBuilder.append("rs.getInt(");
            } else if (type.equals(String.class)) {
                stringBuilder.append("rs.getString(");
            } else if (type.equals(Timestamp.class)) {
                stringBuilder.append("DaoHelper.getTimestamp(rs, ");
            } else if (type.equals(Boolean.class)) {
                stringBuilder.append("DaoHelper.getBoolean(rs, ");
            } else if (type.equals(boolean.class)) {
                stringBuilder.append("rs.getBoolean(");
            }
            stringBuilder.append("\"")
                    .append(name)
                    .append("\"")
                    .append(")");
            return stringBuilder.toString();
        }

        public static String getCoulmnName(String line) {
            return line.split("`")[1];
        }
    }

    public static class JavaBuilder {

        private StringBuilder strBuilder = new StringBuilder();
        private boolean isClassClosed = true;

        public static JavaBuilder makeNew() {
            return new JavaBuilder();
        }

        public JavaBuilder startJavaClass(String className) {
            strBuilder.append("public class ")
                    .append(className)
                    .append(" {\n");
            isClassClosed = false;
            return this;
        }

        public JavaBuilder addVar(Class varType, String varName) {
            strBuilder.append("    private ")
                    .append(varType.getSimpleName())
                    .append(" ")
                    .append(varName)
                    .append(";\n");
            return this;
        }

        public boolean isClassClosed() {
            return isClassClosed;
        }

        public JavaBuilder endClass() {
            strBuilder.append("}");
            isClassClosed = true;
            return this;
        }

        public void reset() {
            strBuilder = new StringBuilder();
        }

        @Override
        public String toString() {
            return strBuilder.toString();
        }
    }

    public static class TextFormatter {

        public static String validVarName(String colName) {
            String[] parts = colName.split("_");
            if (parts.length == 1) {
                return colName;
            }
            StringBuilder strBuilder = new StringBuilder();
            strBuilder.append(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                strBuilder.append(firstCharToUpper(parts[i]));
            }
            return strBuilder.toString();
        }

        public static String firstCharToUpper(String str) {
            return new StringBuilder()
                    .append(str.substring(0, 1).toUpperCase())
                    .append(str.substring(1))
                    .toString();
        }

        public static String firstCharToLower(String str) {
            return new StringBuilder()
                    .append(str.substring(0, 1).toLowerCase())
                    .append(str.substring(1))
                    .toString();
        }
    }

    public static class PathHelper {

        public static String combine(String path1, String path2) {
            File file1 = new File(path1);
            File file2 = new File(file1, path2);
            return file2.getPath();
        }
    }

}
