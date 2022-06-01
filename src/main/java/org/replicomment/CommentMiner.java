package org.replicomment;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.semgraph.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.replicomment.extractor.DocumentedExecutable;
import org.replicomment.extractor.DocumentedType;
import org.replicomment.extractor.JavadocExtractor;
import org.replicomment.util.StanfordParser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentMiner {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        final JavadocExtractor javadocExtractor = new JavadocExtractor();
        List<String> sourceFolders = FileUtils.readLines(new File(
                CommentMiner.class.getResource("/sources.txt").getPath()));

        int count = 0;
        for (String sourceFolder : sourceFolders) {
            //Collect all sources
//            try {
                Collection<File> list = FileUtils.listFiles(
                        new File(
                                sourceFolder),
                        new RegexFileFilter("(.*).java"),
                        TrueFileFilter.INSTANCE);
                String[] selectedClassNames = getClassesInFolder(list, sourceFolder);
                FileWriter writer = new FileWriter("Regex_match.csv");
                writer.append("Class");
                writer.append(';');
                writer.append("Method");
                writer.append(';');
                writer.append("Type");
                writer.append(';');
                writer.append("Comment");
                writer.append('\n');
                System.out.println("[INFO] Analyzing " + sourceFolder + " ...");
                searchKeywords(writer, javadocExtractor, sourceFolder, selectedClassNames);

                writer.flush();
                writer.close();
//            } catch (IllegalArgumentException exception) {
//                System.out.println("[ERROR] Are you sure about this path? " + sourceFolder);
//            }
        }

    }

    /**
     * Search for keywords and stores them.
     *
     * @param writer             {@code FileWriter} where to store the clones
     * @param javadocExtractor   the {@code JavadocExtractor} that extracts the Javadocs
     * @param sourcesFolder      folder containing the Java sources to analyze
     * @param selectedClassNames fully qualified names of the Java classes to be analyzed
     * @throws ClassNotFoundException if a class couldn't be found
     * @throws IOException            if there are problems with the file
     */
    private static void searchKeywords(FileWriter writer, JavadocExtractor javadocExtractor, String sourcesFolder, String[] selectedClassNames) throws ClassNotFoundException, IOException {
        for (String className : selectedClassNames) {
                DocumentedType documentedType = javadocExtractor.extract(
                        className, sourcesFolder);

                if (documentedType != null) {
                    List<DocumentedExecutable> executables = documentedType.getDocumentedExecutables();
                    for (int i = 0; i < executables.size(); i++) {
                        DocumentedExecutable first = executables.get(i);
                        if (!freeTextToFilter(first.getJavadocFreeText())) {
                            String cleanFirst = cleanTags(first.getJavadocFreeText());
//                            String regex =
//                                    ".*(calling )?(this method|it) (is|behaves)( \\w+)? equivalent(ly)? to .*";
                            List<String> keywords = Arrays.asList("equivalent", "similar", "analog",
                                    "prefer", "alternative", "same as", "as");
                            if (foundKeywords(cleanFirst, keywords)) {
                                writer.append(className);
                                writer.append(';');
                                writer.append(first.getSignature());
                                writer.append(';');
                                writer.append("Free text");
                                writer.append(';');
                                writer.append(cleanFirst.replaceAll(";", ","));
                                writer.append("\n");
                            }
                        }
                        /*
                        if (first.returnTag() != null) {
                            String cleanFirst = first.returnTag().getComment().getText();
                            if (!cleanFirst.isEmpty()) {
                                if (depFound(cleanFirst)) {
                                    writer.append(className);
                                    writer.append(';');
                                    writer.append(first.getSignature());
                                    writer.append(';');
                                    writer.append("@return");
                                    writer.append(';');
                                    writer.append(first.returnTag().getComment().getText().replaceAll(";", ","));
                                    writer.append("\n");
                                }
                            }
                        }
                        for (ParamTag firstParamTag : first.paramTags()) {
                            String cleanFirst = firstParamTag.getComment().getText();
                            if (!cleanFirst.isEmpty()) {
                                    if (depFound(cleanFirst)) {
                                        writer.append(className);
                                        writer.append(';');
                                        writer.append(first.getSignature());
                                        writer.append(';');
                                        writer.append("@param");
                                        writer.append(';');
                                        writer.append(firstParamTag.getComment().getText().replaceAll(";", ","));
                                        writer.append("\n");
                                    }
                            }
                        }

                        for (ThrowsTag firstThrowTag : first.throwsTags()) {
                            String cleanFirst = firstThrowTag.getComment().getText();
                            if (!cleanFirst.isEmpty()) {
                                    if (depFound(cleanFirst)) {
                                        writer.append(className);
                                        writer.append(';');
                                        writer.append(first.getSignature());
                                        writer.append(';');
                                        writer.append("@throws");
                                        writer.append(';');
                                        writer.append(firstThrowTag.getComment().getText().replaceAll(";", ","));
                                        writer.append("\n");
                                    }
                            }
                        }
                        */
                    }
                }
        }
    }

    private static boolean foundKeywords(String comment, List<String> keywords) {
        String methodRegex = "(\\w+(\\(.*\\)\\.?|\\.\\w+|#\\w+))+";
        for(String word : keywords){
            if (Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE).matcher(comment).find()) {
                if (word.equals("as")){
                    if(comment.matches(".* as " + methodRegex + " .*")){
                        return true;
                    }
                } else if (comment.matches(".* " + methodRegex + " .*")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether string matches regex case insensitively.
     *
     * @param string the string
     * @param regex the regex
     * @return whether string matches regex
     */
    private static boolean matchesRegex(String string, String regex) {
        Pattern mypattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher mymatcher = mypattern.matcher(string);
        return mymatcher.matches();
    }

    /**
     * This method finds Stanford Parser dependencies in the comment in input.
     *
     * @param comment the comment
     * @return whether a dependencies was found
     */
    private static boolean depFound(String comment) {
//      Dep such as:  “advcl”, “aux”, “auxpass”, “vmod”, and “tmod”
        Annotation document =
                new Annotation(comment);
        List<SemanticGraph> graph = StanfordParser.parse(comment);
        for(SemanticGraph sg : graph) {
            for (TypedDependency dep : sg.typedDependencies()) {
                String dependency = dep.reln().toString();
                if (dependency.equals("tmod") || dependency.equals("aux")
                        || dependency.equals("advcl") || dependency.equals("auxpass")
                || dependency.equals("vmod"))
                    return true;

            }
        }
        return false;
    }


//    /**
//     * Returns true if the exception clone must be filtered out
//     *
//     * @param firstThrowTag first tag to compare
//     * @param secThrowTag second tag to compare
//     * @return true if the exception names are the same
//     */
//    private static boolean excepToFilter(ThrowsTag firstThrowTag, ThrowsTag secThrowTag) {
//        return firstThrowTag.getException().equals(secThrowTag.getException());
//    }

    /**
     * Filter out empty free texts and free texts pointing at inheritDoc.
     *
     * @param freeText the freeText to examine
     * @return true if one of the comment must be filtered out
     */
    private static boolean freeTextToFilter(String freeText) {
        String noBlankFreeText = freeText.trim().replaceAll("\n ", "");
        return noBlankFreeText.isEmpty() || noBlankFreeText.equals("{@inheritDoc}");
    }

    /**
     * From a list of files in path, finds the fully qualified names of Java classes.
     *
     * @param list collection of {@code Files}
     * @param path the String path to search in
     * @return fully qualified names found, in an array of Strings
     */
    private static String[] getClassesInFolder(Collection<File> list, String path) {
        String[] selectedClassNames = new String[list.size()];
        int i = 0;
        for (File file : list) {
            String fileName = file.getAbsolutePath();
            String[] unnecessaryPrefix = fileName.split(path);
            String className = unnecessaryPrefix[1].replaceAll("\\\\", ".");
            selectedClassNames[i++] = className.replace(".java", "");
        }
        return selectedClassNames;

    }

    static String cleanTags(String text) {
        text = text.replaceAll("\\s+", " ");

        final String codePattern1 = "<code>([A-Za-z0-9_]+)</code>";
        text = removeTags(codePattern1, text);

        final String codePattern2 = "\\{@code ([^}]+)\\}";
        text = removeTags(codePattern2, text);

        text = removeTags("\\{@link #?([^}]+)\\}", text);
        text = removeHTMLTags(text);
        text = decodeHTML(text);
        return text.trim();
    }

    /** Decodes HTML character entities found in comment text with corresponding characters. */
    private static String decodeHTML(String text) {
        return text
                        .replaceAll("&ge;", ">=")
                        .replaceAll("&le;", "<=")
                        .replaceAll("&gt;", ">")
                        .replaceAll("&lt;", "<")
                        .replaceAll("&amp;", "&");
    }

    /** Removes HTML tags from the comment text. */
    private static String removeHTMLTags(String text) {
        String htmlTagPattern = "<([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*>(.*?)</\\1>|(<(.*)/>)|<p>";
        Matcher matcher = Pattern.compile(htmlTagPattern).matcher(text);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                text = text.replace(matcher.group(0), matcher.group(2));
            } else {
                // Match contains self-closing tag
                text = text.replace(matcher.group(0), "");
            }
        }
        return text;
    }

    /**
     * Removes Javadoc inline tags from the comment text preserving the content of the tags.
     *
     * @param pattern a regular expression
     */
    private static String removeTags(String pattern, String text) {
        Matcher matcher = Pattern.compile(pattern).matcher(text);
        while (matcher.find()) {
            text = text.replace(matcher.group(0), matcher.group(1));
        }
        return text;
    }
}
