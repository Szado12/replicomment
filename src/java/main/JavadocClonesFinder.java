package main;

import main.extractor.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class JavadocClonesFinder {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        final JavadocExtractor javadocExtractor = new JavadocExtractor();
        //TODO fix hardcoded paths
        String[] sourceFolders = {
//                "/home/arianna/comment-clones/javadoclones/src/resources/src/rxjava-1.3.5-sources/",
//                "/home/arianna/comment-clones/javadoclones/src/resources/src/hadoop-2.6.5-src/"+
//                        "hadoop-common-project/hadoop-common/src/main/java/",
//                "/home/arianna/comment-clones/javadoclones/src/resources/src/lucene-core-7.2.1-sources/",
//                "/home/arianna/comment-clones/javadoclones/src/resources/src/solr-7.1.0-sources/",
//                "/home/arianna/comment-clones/javadoclones/src/resources/src/guava-19.0-sources/",
//                "/home/arianna/comment-clones/javadoclones/src/resources/src/hadoop-2.6.5-src/" +
//                "hadoop-hdfs-project/hadoop-hdfs/src/main/java/",
//                "/home/arianna/comment-clones/javadoclones/src/resources/src/vertx-core-3.5.0/",
                "/home/arianna/comment-clones/javadoclones/src/resources/src/elasticsearch-6.1.1/"
//                "/home/arianna/comment-clones/javadoclones/src/resources/src/spring-core-5.0.2/",
//                "/home/arianna/comment-clones/javadoclones/src/resources/src/log4j-1.2.17/"
 };

        for(int i=0; i<sourceFolders.length; i++){
            FileWriter writer = new FileWriter("Javadoc_clones_"+i+".csv");
            writer.append("Class");
            writer.append(';');
            writer.append("Method1");
            writer.append(';');
            writer.append("Method2");
            writer.append(';');
            writer.append("Type");
            writer.append(';');
            writer.append("Cloned text");
            writer.append(';');
            writer.append("Legit?");
            writer.append('\n');

            //Collect all sources
            Collection<File> list = FileUtils.listFiles(
                    new File(
                            sourceFolders[i]),
                    new RegexFileFilter("(.*).java"),
                    TrueFileFilter.INSTANCE);

            String[] selectedClassNames  = getClassesInFolder(list, sourceFolders[i]);
            analyzeClones(writer, javadocExtractor, sourceFolders[i], selectedClassNames);
            writer.flush();
            writer.close();
        }

    }

    /**
     * Search for Javadoc clones and stores them.
     *
     * @param writer {@code FileWriter} where to store the clones
     * @param javadocExtractor the {@code JavadocExtractor} that extracts the Javadocs
     * @param sourcesFolder folder containing the Java sources to analyze
     * @param selectedClassNames fully qualified names of the Java classes to be analyzed
     * @throws ClassNotFoundException if a class couldn't be found
     * @throws IOException if there are problems with the file
     */
    private static void analyzeClones(FileWriter writer, JavadocExtractor javadocExtractor, String sourcesFolder, String[] selectedClassNames) throws ClassNotFoundException, IOException {
        for(String className : selectedClassNames) {
            try {
                DocumentedType documentedType = javadocExtractor.extract(
                        className, sourcesFolder);

                if (documentedType != null) {
                    System.out.println("\nIn class " + className + ":");
                    List<DocumentedExecutable> executables = documentedType.getDocumentedExecutables();
                    for (int i = 0; i < executables.size(); i++) {
                        DocumentedExecutable first = executables.get(i);
                        for (int j = i + 1; j < executables.size(); j++) {
                            DocumentedExecutable second = executables.get(j);


                            boolean legit = isCloneLegit(first.getName(), second.getName());

                            if (!freeTextToFilter(first.getJavadocFreeText())) {
                                String cleanFirst = first.getJavadocFreeText().trim().replaceAll("\n ", "");
                                String cleanSecond = second.getJavadocFreeText().trim().replaceAll("\n ", "");
                                if (cleanFirst.equals(cleanSecond)) {
                                    System.out.println("\nFree text clone: " + first.getJavadocFreeText() + "\n" +
                                            " among " + first.getSignature() + " \nand " + second.getSignature());

                                    writer.append(className);
                                    writer.append(';');
                                    writer.append(first.getSignature());
                                    writer.append(';');
                                    writer.append(second.getSignature());
                                    writer.append(';');
                                    writer.append("Free text");
                                    writer.append(';');
                                    writer.append(first.getJavadocFreeText().replaceAll(";", ","));
                                    writer.append(';');
                                    writer.append(String.valueOf(legit));
                                    writer.append("\n");
                                }
                            }
                            if (first.returnTag() != null && second.returnTag() != null) {
                                String cleanFirst = first.returnTag().getComment().getText().trim().replaceAll("\n ", "");
                                if (!cleanFirst.isEmpty()) {
                                    String cleanSecond = second.returnTag().getComment().getText().trim().replaceAll("\n ", "");
                                    if (cleanFirst.equals(cleanSecond)) {
                                        System.out.println("\n@return tag clone: " + first.returnTag().getComment().getText() + "\n" +
                                                " among " + first.getSignature() + " \nand " + second.getSignature());

                                        writer.append(className);
                                        writer.append(';');
                                        writer.append(first.getSignature());
                                        writer.append(';');
                                        writer.append(second.getSignature());
                                        writer.append(';');
                                        writer.append("@return");
                                        writer.append(';');
                                        writer.append(first.returnTag().getComment().getText().replaceAll(";", ","));
                                        writer.append(';');
                                        writer.append(String.valueOf(legit));
                                        writer.append("\n");
                                    }
                                }
                            }
                            for (ParamTag firstParamTag : first.paramTags()) {
                                String cleanFirst = firstParamTag.getComment().getText().trim().replaceAll("\n ", "");
                                if (!cleanFirst.isEmpty()) {
                                    for (ParamTag secParamTag : second.paramTags()) {
                                            String cleanSecond = secParamTag.getComment().getText().trim().replaceAll("\n ", "");
                                            if (cleanFirst.equals(cleanSecond)) {
                                                System.out.println("\n@param tag clone: " + firstParamTag.getComment().getText() + "\n" +
                                                        " among " + first.getSignature() + " \nand " + second.getSignature());
                                                writer.append(className);
                                                writer.append(';');
                                                writer.append(first.getSignature());
                                                writer.append(';');
                                                writer.append(second.getSignature());
                                                writer.append(';');
                                                writer.append("@param");
                                                writer.append(';');
                                                writer.append(firstParamTag.getComment().getText().replaceAll(";", ","));
                                                writer.append(';');
                                                writer.append(String.valueOf(legit || firstParamTag.getParamName().equals(secParamTag.getParamName())));
                                                writer.append("\n");
                                            }

                                    }
                                }
                            }

                            for (ThrowsTag firstThrowTag : first.throwsTags()) {
                                String cleanFirst = firstThrowTag.getComment().getText().trim().replaceAll("\n ", "");
                                if (!cleanFirst.isEmpty()) {
                                    for (ThrowsTag secThrowTag : second.throwsTags()) {
                                        String cleanSecond = secThrowTag.getComment().getText().trim().replaceAll("\n ", "");
                                        if (cleanFirst.equals(cleanSecond)) {
                                            System.out.println("\n@throws tag clone: " + firstThrowTag.getComment().getText() + "\n" +
                                                    " among " + first.getSignature() + " \nand " + second.getSignature());
                                            writer.append(className);
                                            writer.append(';');
                                            writer.append(first.getSignature());
                                            writer.append(';');
                                            writer.append(second.getSignature());
                                            writer.append(';');
                                            writer.append("@throws");
                                            writer.append(';');
                                            writer.append(firstThrowTag.getComment().getText().replaceAll(";", ","));
                                            writer.append(';');
                                            writer.append(String.valueOf(legit || firstThrowTag.getException().equals(secThrowTag.getException())));
                                            writer.append("\n");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }catch(Exception e){
                //do nothing
            }
        }
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
        return  noBlankFreeText.isEmpty() || noBlankFreeText.equals("{@inheritDoc}");
    }

    /**
     * Check if comment clone is legit, meaning that the case could be an override or overload.
     *
     * @param firstName first method name
     * @param secondName second method name
     * @return true if the comment clone is legit
     */
    private static boolean isCloneLegit(String firstName, String secondName) {
        return firstName.equals(secondName) || firstName.contains(secondName) || secondName.contains(firstName);

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
            String className = unnecessaryPrefix[1].replaceAll("/",".");
            selectedClassNames[i++] = className.replace(".java", "");
        }
        return selectedClassNames;

    }
}