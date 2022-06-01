package org.replicomment;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.apache.commons.lang3.StringUtils;
import org.replicomment.extractor.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.replicomment.util.CommentedOutCodeChecker;
import org.replicomment.util.Reflection;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;


public class JavadocClonesFinder {

    private static final boolean SHOW_LEGIT = true;
    public static final JavadocExtractor javadocExtractor = new JavadocExtractor();
    public static HashMap<String, DocumentedType> documentedTypes = new HashMap<>();
    private static FileWriter localCloneWriter;
    private static FileWriter hierarchyCloneWriter;
    private static FileWriter crossCloneWriter;
    private static FileWriter fieldCrossCloneWriter;
    private static FileWriter fieldCloneWriter;
    private static FileWriter fieldHieCloneWriter;
    private static FileWriter commentedOutWriter;
    private static FileWriter singleWordOutWriter;


    private enum CONTEXT {
        INNER, CROSS, HIERARCHY, FIELD
    }

    public static void main(String[] args) throws IOException {
        List<String> sourceFolderNames = FileUtils.readLines(new File(
              JavadocClonesFinder.class.getResource("/sources.txt").getPath()));

        sourceFolderNames.replaceAll(x -> x.replace("\\","\\\\"));
        for (String source : sourceFolderNames) {
            String[] tokens = source.split(";");

            // Collect all sources

            Collection<File> list = FileUtils.listFiles(
                    new File(
                            tokens[1]),
                    new RegexFileFilter("(.*).java"),
                    TrueFileFilter.INSTANCE);
            // Collect all the class names in source folder
            List<String> selectedClassNames = getClassesInFolder(list, tokens[1]);

            System.out.println("[INFO] Analyzing " + tokens[1] + " ...");

            String sourceId = tokens[0];
            analyzeComments(tokens[1], sourceId, selectedClassNames);
            documentedTypes = new HashMap<>();
        }
        System.out.println("[INFO] Terminating now ...");
    }

    private static void prepareCSVOutput(FileWriter writer, boolean hierarchyClone, boolean crossFile, boolean fields)
            throws IOException {

        writer.append("Main Class");
        writer.append(';');
        if (!fields) {
            writer.append("Method1");
            writer.append(';');
            writer.append("Method2");
            writer.append(';');
        } else {
            writer.append("Field1");
            writer.append(';');
            writer.append("Field2");
            writer.append(';');
            writer.append("Type1");
            writer.append(';');
            writer.append("Type2");
            writer.append(';');
        }
        writer.append("Kind");
        writer.append(';');
        if (!fields) {
            writer.append("Param1");
            writer.append(';');
            writer.append("Param2");
            writer.append(';');
        }
        writer.append("Cloned text");
        writer.append(';');
        writer.append("Legit?");
        writer.append(';');
        if (hierarchyClone) {
            writer.append("Extended Class");
        } else if (crossFile) {
            writer.append("External Class");
        }
        writer.append('\n');
    }

    /**
     * Search for Javadoc clones and stores them.
     *
     * @param sourcesFolder      folder containing the Java sources to analyze
     * @param selectedClassNames fully qualified names of the Java classes to be analyzed
     */
    private static void analyzeComments(String sourcesFolder, String sourceFolderID,
                                        List<String> selectedClassNames) throws IOException {

        CommentedOutCodeChecker commentedOutCodeChecker = new CommentedOutCodeChecker();
        prepareResultsFiles(sourceFolderID);
        int classIndex = 0;
        for (String className : selectedClassNames) {
            // For each class name in folder, find comment clones
            try {
                DocumentedType documentedType;
                NodeList<ClassOrInterfaceType> extendedTypes = new NodeList<>();

                if (documentedTypes.containsKey(className)) {
                    documentedType = documentedTypes.get(className);
                } else {
                    documentedType = javadocExtractor.extract(
                            className, sourcesFolder);
                    documentedTypes.put(className, documentedType);
                }

                if (documentedType != null) {
                    if (documentedType.getSourceClass() != null) {
                        // Grab hierarchy of current class (specifically: the supertypes)
                        extendedTypes = documentedType.getSourceClass().getExtendedTypes();
                        // FIXME what about implemented types (interfaces?)

                        // FIXME can we check if the two classes have parent class in common?
                        // FIXME ...but I'd avoid it. I'd even avoid the same hierarchy heuristic now.
                        // FIXME Because I did find real c&p in in hierarchies (implementations)...
                        // FIXME
                        // FIXME Reason: the may have same constructor documentation.
                        // FIXME Alternatives:
                        // FIXME 1) allow same constructor doc. in package (sounds wild)
                        // FIXME 2) allow same constructor doc. if:
                        // FIXME    a) it's only a part (e.g. only free-text) to be copied & it says "constructor" & similar
                        // FIXME    b) it's a whole comment clone but includes doc. parameters & they match with constructors'
                        // FIXME       parameters; although we should break the "whole" here, which we currently do not do
                        // FIXME      (how difficult is that?)
                    }
//                    System.out.println("\nIn class " + className + ":");

                    //Check for commented out code
                    Map<String, List<CommentedOutCodeResult>> commentedOutCodeResult = commentedOutCodeChecker.CheckForCodeInComment(documentedType.getAllComments(),sourcesFolder, className,true);
                    addDataToOutputFile(commentedOutWriter,commentedOutCodeResult.get("commentedOutCodeResult"));
                    addDataToOutputFile(singleWordOutWriter,commentedOutCodeResult.get("singleWordComments"));

                    // Retrieve methods in current class.
                    List<DocumentedExecutable> localExecutables =
                            documentedType.getDocumentedExecutables();

                    // Following lines manage method-level clones.
                    for (int i = 0; i < localExecutables.size(); i++) {
                        // Pick ONE local method at time, and forward it to clone search procedures
                        // to compare its doc against the doc of all other methods, which means,
                        // the ones that are ahead of it in the source file (from index i):
                        // We will cover all of them without comparing the same couple of methods twice.
                        DocumentedExecutable firstMethod = localExecutables.get(i);

                        // Look for local (same-file) doc clones, method level.
                        methodLevelClonesSearch(localCloneWriter, className, "",
                                localExecutables, i, firstMethod, CONTEXT.INNER);

                        // Look for hierarchy doc clones, method level.
                        exploreSourceHierarchy(hierarchyCloneWriter, sourcesFolder,
                                selectedClassNames, className, extendedTypes, firstMethod);

                        // Look for clones cross-file, method level (excluding subtypes, addressed above).
                        // Avoid comparing C1 and C2 and then C2 and C1 in subsequent iterations, by removing
                        // classes already explored (the ones before the current index in the list of classes)
                        List<String> externalClasses = new ArrayList<>(selectedClassNames.subList(classIndex + 1,
                                selectedClassNames.size()));

                        // Remove the class itself from the pool of "external" classes.
//                            externalClasses.remove(className);

                        cleanExternalClasses(className, extendedTypes, externalClasses);
                        crossFileClonesSearch(crossCloneWriter, sourcesFolder,
                                className, externalClasses, firstMethod);
                    }

                    // Following lines manage fields clones.
                    List<DocumentedField> documentedFields = documentedType.getDocumentedFields();
                    for (int i = 0; i < documentedFields.size(); i++) {
                        DocumentedField firstField = documentedFields.get(i);
                        // Look for local (same-file) doc clones, field level.
                        fieldLevelClonesSearch(fieldCloneWriter, className, "", documentedFields, i, firstField);

                        // Look for hierarchy doc clones, field level.
                        exploreHierarchyFields(fieldHieCloneWriter, sourcesFolder,
                                selectedClassNames, className, extendedTypes, firstField);

                        // Look for cross-file field level clones.
                        List<String> externalClasses = new ArrayList<>(selectedClassNames.subList(classIndex + 1,
                                selectedClassNames.size()));
                        cleanExternalClasses(className, extendedTypes, externalClasses);
                        crossFileFieldClonesSearch(fieldCrossCloneWriter, sourcesFolder,
                                className, externalClasses, firstField);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            classIndex++;
        }
        closeOutputFiles();

    }

    private static void cleanExternalClasses(String className,
                                             NodeList<ClassOrInterfaceType> extendedTypes,
                                             List<String> externalClasses) {
        String packageName = className.substring(0, className.lastIndexOf("."));
        for (ClassOrInterfaceType superType : extendedTypes) {
            // Remove supertypes of current class from external classes:
            // we already addressed those clones.
            String externalClassName = packageName + "." + superType.getNameAsString();
            externalClasses.remove(externalClassName);
        }
    }

    private static void prepareResultsFiles(String sourceFolderID) throws IOException {
        // Prepare results header
        localCloneWriter = new FileWriter("2020_JavadocClones_" + sourceFolderID + ".csv");
        hierarchyCloneWriter = new FileWriter("2020_JavadocClones_h_" + sourceFolderID + ".csv");
        crossCloneWriter = new FileWriter("2020_JavadocClones_cf_" + sourceFolderID + ".csv");

        fieldCrossCloneWriter = new FileWriter("2020_JavadocClones_fields_cf_" + sourceFolderID + ".csv");
        fieldHieCloneWriter = new FileWriter("2020_JavadocClones_fields_h_" + sourceFolderID + ".csv");
        fieldCloneWriter = new FileWriter("2020_JavadocClones_fields_" + sourceFolderID + ".csv");

        commentedOutWriter = new FileWriter("CommentedOutCode_"+sourceFolderID+".csv");
        singleWordOutWriter = new FileWriter("SingleWordComments_"+sourceFolderID+".csv");

        prepareCSVOutput(localCloneWriter, false, false, false);
        prepareCSVOutput(hierarchyCloneWriter, true, false, false);
        prepareCSVOutput(crossCloneWriter, false, true, false);

        prepareCSVOutput(fieldCloneWriter, false, false, true);
        prepareCSVOutput(fieldHieCloneWriter, true, false, true);
        prepareCSVOutput(fieldCrossCloneWriter, false, true, true);

        prepareOutputFileForCommentedOutCode(commentedOutWriter);
        prepareOutputFileForCommentedOutCode(singleWordOutWriter);
    }

    private static void closeOutputFiles() throws IOException {
        // Close result files.
        localCloneWriter.flush();
        localCloneWriter.close();
        hierarchyCloneWriter.flush();
        hierarchyCloneWriter.close();
        crossCloneWriter.flush();
        crossCloneWriter.close();

        fieldCloneWriter.flush();
        fieldCloneWriter.close();
        fieldCrossCloneWriter.flush();
        fieldCrossCloneWriter.close();
        fieldHieCloneWriter.flush();
        fieldHieCloneWriter.close();

        commentedOutWriter.flush();
        commentedOutWriter.close();

        singleWordOutWriter.flush();
        singleWordOutWriter.close();
    }

    private static void methodLevelClonesSearch(FileWriter writer, String className, String externalClass,
                                                List<DocumentedExecutable> docExecutables, int i,
                                                DocumentedExecutable first,
                                                CONTEXT cloneContext) throws IOException {

        for (int j = i + 1; j < docExecutables.size(); j++) {
            // i+1 to avoid comparing A and B and then again B and A
            // (in a positive case, it would count as 2 clones, while
            //  we actually count 1)
            DocumentedExecutable second = docExecutables.get(j);


            String firstJavadoc = first.getWholeJavadocAsString();
            String secondJavadoc = second.getWholeJavadocAsString();

            if (firstJavadoc.trim().isEmpty() || secondJavadoc.trim().isEmpty())
                continue;


            String firstSignature = first.toString();
            String secondSignature = second.toString();

            boolean wholeClone = isWholeClone(firstJavadoc, secondJavadoc);
            if (!wholeClone) {
                // Not a whole comment clone. Overloading?
                boolean legit = isOverloading(first.getName(), second.getName());

                // FIXME these methods need some refactoring, it's a lot of message chaining.
                freeTextCloneCheck(writer, className, externalClass, first, second, legit);
                returnTagCloneCheck(writer, className, externalClass, first, second, legit);
                paramTagsSearch(writer, className, externalClass, first,
                        second, firstSignature, secondSignature, legit);
                exTagsSearch(writer, className, externalClass, first,
                        second, firstSignature, secondSignature, legit);
            } else {
                // A whole clone is never legitimate, unless it's overriding (assuming inherited method
                // behaviour does not change, then doc also doesn't)
                boolean legit = isOverriding(first.getName(), second.getName(), externalClass) ||
//                        isConstructorsInHierarchy(first, second, cloneContext)
                        isGenericConstructorComment(first, second, firstJavadoc);
                wholeClonePrint(writer, className, externalClass, first, second, legit, firstJavadoc);
            }
        }
    }

    private static boolean isGenericConstructorComment(DocumentedExecutable first,
                                                       DocumentedExecutable second,
                                                       String javadoc) {
        final String defaultDoc = "Sole constructor. (For invocation by subclass constructors, typically implicit.)";
        final String subclassesConstructor = "Constructor for use by subclasses.";
        String genericConstructorDoc = "(sole |default )?constructor\\.?";

        if (first.isConstructor() && second.isConstructor()) {
            return (javadoc.trim().equals(defaultDoc) ||
                    javadoc.trim().equals(subclassesConstructor) ||
                    javadoc.trim().toLowerCase().matches(genericConstructorDoc))
                    && first.getParameters().isEmpty() && second.getParameters().isEmpty();
        }
        return false;
    }

    private static boolean isConstructorsInHierarchy(DocumentedExecutable first,
                                                     DocumentedExecutable second,
                                                     CONTEXT cloneContext) {
        // Constructors in the same hierarchy allowed to have same doc.
        return cloneContext.equals(CONTEXT.HIERARCHY) && first.isConstructor() && second.isConstructor();
    }

    private static void fieldLevelClonesSearch(FileWriter writer,
                                               String className,
                                               String extClassName,
                                               List<DocumentedField> documentedFields,
                                               int i,
                                               DocumentedField first) throws IOException {
        String firstJavadoc = first.getJavadocFreeText();
        if (firstJavadoc.isEmpty()) {
            return;
        }
        for (int j = i + 1; j < documentedFields.size(); j++) {
            // i+1 to avoid comparing A and B and then again B and A
            // (in a positive case, it would count as 2 clones, while
            //  we actually count 1)
            DocumentedField second = documentedFields.get(j);
            String secondJavadoc = second.getJavadocFreeText();

            String firstName = first.getName();
            String secondName = second.getName();

            String firstType = first.getTypeName();
            String secondType = second.getTypeName();

            if (!firstJavadoc.equals(secondJavadoc)) {
                continue;
            } else {
                // TODO in case we include external classes, too: && firstName.equals(secondName)?
                boolean legit = firstType.equals(secondType);
                if (SHOW_LEGIT || !legit) {
                    writer.append(className);
                    writer.append(';');
                    writer.append(firstName);
                    writer.append(';');
                    writer.append(secondName);
                    writer.append(';');
                    writer.append(firstType);
                    writer.append(';');
                    writer.append(secondType);
                    writer.append(';');
                    writer.append("Field");
                    writer.append(';');
                    writer.append(StringUtils.replace(firstJavadoc, ";", ","));
                    writer.append(';');
                    writer.append(String.valueOf(legit));
                    writer.append(';');
                    if (!"".equals(extClassName)) {
                        writer.append(extClassName);
                        writer.append(';');
                    }
                    writer.append("\n");
                }
            }

        }
    }

    private static void crossFileClonesSearch(FileWriter crossCloneWriter,
                                              String sourcesFolder,
                                              String className,
                                              List<String> selectedClassNames,
                                              DocumentedExecutable first) throws IOException {
        // Given an executable (first) of the current class, check its doc against
        // the doc of each executables of each external class.
        for (String externalClass : selectedClassNames) {
            // For each external class, grab the documented type...
            DocumentedType documentedExtType;
            if (!externalClass.equals(className)) {

                if (documentedTypes.containsKey(externalClass)) {
                    documentedExtType = documentedTypes.get(externalClass);
                } else {
                    documentedExtType = javadocExtractor.extract(
                            externalClass, sourcesFolder);
                    documentedTypes.put(externalClass, documentedExtType);
                }

                if (documentedExtType != null) {
                    List<DocumentedExecutable> externalExecutables =
                            documentedExtType.getDocumentedExecutables();
                    // Grab the executables of the external class...
//                    for (int j = 0; j < externalExecutables.size(); j++) {
                    // Compare each of it against first.
                    // FIXME but this looks flawed for cross-file. In same file, each ex. is compared
                    // FIXME against each other ex. from itself on. Here though we have to compare a single
                    // FIXME ex. against all the others of an external class, none excluded.
                    // FIXME methodLevelClonesSearch already iterates over these external executables, we do
                    // FIXME have to duplicate the cycle here!
                    methodLevelClonesSearch(crossCloneWriter, className,
                            externalClass, externalExecutables, -1, first, CONTEXT.CROSS);
//                    }
                }
            }
        }
    }


    private static void crossFileFieldClonesSearch(FileWriter crossCloneWriter,
                                                   String sourcesFolder,
                                                   String className,
                                                   List<String> externalClasses,
                                                   DocumentedField firstField) throws IOException {
        for (String externalClass : externalClasses) {
            DocumentedType documentedExtType;
            if (!externalClass.equals(className)) {

                if (documentedTypes.containsKey(externalClass)) {
                    documentedExtType = documentedTypes.get(externalClass);
                } else {
                    documentedExtType = javadocExtractor.extract(
                            externalClass, sourcesFolder);
                    documentedTypes.put(externalClass, documentedExtType);
                }

                if (documentedExtType != null) {
                    List<DocumentedField> externalFields =
                            documentedExtType.getDocumentedFields();
//                    for (int j = 0; j < externalFields.size(); j++) {
                    fieldLevelClonesSearch(crossCloneWriter, className,
                            externalClass, externalFields, -1, firstField);
//                    }
                }
            }
        }

    }

    private static void exploreSourceHierarchy(FileWriter ewriter,
                                               String sourcesFolder,
                                               List<String> selectedClassNames,
                                               String className,
                                               NodeList<ClassOrInterfaceType> extendedTypes,
                                               DocumentedExecutable first) throws IOException {
        if (extendedTypes.isEmpty()) {
            return;
        }

        String packageName = className.substring(0, className.lastIndexOf("."));

        for (ClassOrInterfaceType superType : extendedTypes) {
            // We found a bunch of subtypes. Time to retrieve their doc.
            String externalClass = packageName + "." + superType.getNameAsString();
            DocumentedType documentedSubType;
            if (selectedClassNames.contains(externalClass)) {
                // Found subtype source.

                if (documentedTypes.containsKey(externalClass)) {
                    documentedSubType = documentedTypes.get(externalClass);
                } else {
                    documentedSubType = javadocExtractor.extract(
                            externalClass, sourcesFolder);
                    documentedTypes.put(externalClass, documentedSubType);
                }

                if (documentedSubType != null) {
                    List<DocumentedExecutable> externalExecutables =
                            documentedSubType.getDocumentedExecutables();
//                    for (int j = 0; j < externalExecutables.size(); j++) {
                    methodLevelClonesSearch(ewriter, className, externalClass,
                            externalExecutables, -1, first, CONTEXT.HIERARCHY);
//                    }
                }
            }
        }
    }

    private static void exploreHierarchyFields(FileWriter ewriter,
                                               String sourcesFolder,
                                               List<String> selectedClassNames,
                                               String className,
                                               NodeList<ClassOrInterfaceType> extendedTypes,
                                               DocumentedField first) throws IOException {
        if (extendedTypes.isEmpty()) {
            return;
        }

        String packageName = className.substring(0, className.lastIndexOf("."));

        for (ClassOrInterfaceType superType : extendedTypes) {
            // We found a bunch of subtypes. Time to retrieve their doc.
            String externalClass = packageName + "." + superType.getNameAsString();
            DocumentedType documentedSubType;
            if (selectedClassNames.contains(externalClass)) {
                // Found subtype source.

                // FIXME the following shouldn't be necessary having populated the hashmap for methods.
                if (documentedTypes.containsKey(externalClass)) {
                    documentedSubType = documentedTypes.get(externalClass);
                } else {
                    documentedSubType = javadocExtractor.extract(
                            externalClass, sourcesFolder);
                    documentedTypes.put(externalClass, documentedSubType);
                }

                if (documentedSubType != null) {
                    List<DocumentedField> externalFields =
                            documentedSubType.getDocumentedFields();
//                    for (int j = 0; j < externalFields.size(); j++) {
                    fieldLevelClonesSearch(ewriter, className, externalClass,
                            externalFields, -1, first);
//                    }
                }
            }
        }
    }

    private static boolean isOverriding(String firstName, String secondName, String externalClass) {
        if (!"".equals(externalClass)) {
            firstName = firstName.toLowerCase();
            secondName = secondName.toLowerCase();
            return firstName.equals(secondName);
        }
        return false;
    }


    private static void wholeClonePrint(FileWriter writer, String className, String extClassName,
                                        DocumentedExecutable first, DocumentedExecutable second,
                                        boolean legit, String firstJavadoc) throws IOException {
        if (SHOW_LEGIT || !legit) {
            writer.append(className);
            writer.append(';');
            writer.append(first.toString());
            writer.append(';');
            writer.append(second.toString());
            writer.append(';');
            writer.append("Whole");
            writer.append(';');
            writer.append("");
            writer.append(';');
            writer.append("");
            writer.append(';');
            writer.append(StringUtils.replace(firstJavadoc, ";", ","));
            writer.append(';');
            writer.append(String.valueOf(legit));
            writer.append(';');
            if (!"".equals(extClassName)) {
                writer.append(extClassName);
                writer.append(';');
            }
            writer.append("\n");
        }
    }

    private static boolean isWholeClone(String first, String second) {
        return !freeTextToFilter(first) && first.equals(second);
    }

    private static void exTagsSearch(FileWriter writer, String className, String externalClass, DocumentedExecutable first, DocumentedExecutable second, String firstSignature, String secondSignature, boolean legit) throws IOException {
        for (ThrowsTag firstThrowTag : first.throwsTags()) {
            String cleanFirst = StringUtils.replace(firstThrowTag.getComment()
                            .getText()
                            .trim()
                    , "\n ", "");
            if (!cleanFirst.isEmpty()) {
                for (ThrowsTag secThrowTag : second.throwsTags()) {
                    throwsTagCloneCheck(writer, className, externalClass, firstSignature, secondSignature,
                            firstThrowTag, secThrowTag, legit);
                }
            }
        }
    }

    private static void throwsTagCloneCheck(FileWriter writer,
                                            String className, String extClassName,
                                            String firstMethod,
                                            String secondMethod,
                                            ThrowsTag firstTag,
                                            ThrowsTag secondTag,
                                            boolean legit) throws IOException {
        if (SHOW_LEGIT || !legit) {
            Comment comment1 = firstTag.getComment();
            Comment comment2 = secondTag.getComment();

            String cleanFirst = StringUtils.replace(comment1.getText().trim(), "\n ", "");
            String cleanSecond = StringUtils.replace(comment2.getText().trim(), "\n ", "");

            if (cleanFirst.equals(cleanSecond)) {

                String exceptionNameOne = firstTag.getException();
                String exceptionNameTwo = secondTag.getException();
                // Same exception name: could be a legitimate clone
                legit = legit || exceptionNameOne.equals(exceptionNameTwo);

                String genericConditionEx = "(if|for) (any|an) (error|problem|issue) (that|which)? (occurs|happens|raises)";
                // The following would be < 3 anyway
                //            String genericEx = "(upon|on) (error)";

                // Cloned exception will be tolerated ONLY IF:
                // - same exception name;
                // - no shorter than 4 words;
                // - not generic condition ("if an error occurs" etc.)
                // ---------------------------
                // -----> NEW HEURISTIC <-----
                // ---------------------------
                legit = legit
                        && comment1.getText().split(" ").length > 3
                        && !comment1.getText().matches(genericConditionEx);

                // ...unless it is thrown always
                legit = legit && !cleanFirst.equals("always");

//          System.out.println("\n@param tag clone: " + firstParamTag.getComment().getText() + "\n" +
//          " among " + first.getSignature() + " \nand " + second.getSignature());

                if (SHOW_LEGIT || !legit) {
                    writer.append(className);
                    writer.append(';');
                    writer.append(firstMethod);
                    writer.append(';');
                    writer.append(secondMethod);
                    writer.append(';');
                    writer.append("@throws");
                    writer.append(';');
                    writer.append("");
                    writer.append(';');
                    writer.append("");
                    writer.append(';');
                    writer.append(StringUtils.replace(comment1.getText(), ";", ","));
                    writer.append(';');
                    writer.append(String.valueOf(legit));
                    writer.append(';');
                    if (!"".equals(extClassName)) {
                        writer.append(extClassName);
                        writer.append(';');
                    }
                    writer.append("\n");
                }
            }
        }
    }

    private static void paramTagsSearch(FileWriter writer,
                                        String className,
                                        String externalClass, DocumentedExecutable first,
                                        DocumentedExecutable second, String firstSignature,
                                        String secondSignature, boolean legit) throws IOException {
        for (ParamTag firstParamTag : first.paramTags()) {
            String cleanFirst = StringUtils.replace(firstParamTag
                            .getComment()
                            .getText()
                            .trim()
                    , "\n ", "");
            if (!cleanFirst.isEmpty()) {
                for (ParamTag secParamTag : second.paramTags()) {
                    paramTagsSearch(writer, className, externalClass, firstSignature, secondSignature,
                            legit, firstParamTag, secParamTag);

                }
            }
        }
    }

    private static void paramTagsSearch(FileWriter writer,
                                        String className, String extClassName,
                                        String first,
                                        String second,
                                        boolean legit,
                                        ParamTag firstTag,
                                        ParamTag secondTag) throws IOException {
        Comment comment1 = firstTag.getComment();
        Comment comment2 = secondTag.getComment();

        String cleanFirst = StringUtils.replace(comment1.getText().trim(), "\n ", "");
        String cleanSecond = StringUtils.replace(comment2.getText().trim(), "\n ", "");

        String paramName1 = firstTag.getParamName();
        String paramName2 = secondTag.getParamName();

        String paramType1 = firstTag.getParamType();
        String paramType2 = secondTag.getParamType();

        // Same parameter name could mean legitimate clone
        legit = legit || paramName1.equals(paramName2);

        if (SHOW_LEGIT || !legit) {
            if (cleanFirst.equals(cleanSecond)) {
//          System.out.println("\n@param tag clone: " + firstParamTag.getComment().getText() + "\n" +
//          " among " + first.getSignature() + " \nand " + second.getSignature());
                writer.append(className);
                writer.append(';');
                writer.append(first);
                writer.append(';');
                writer.append(second);
                writer.append(';');
                writer.append("@param");
                writer.append(';');
                writer.append(paramType1 + " " + paramName1);
                writer.append(';');
                writer.append(paramType2 + " " + paramName2);
                writer.append(';');
                writer.append(StringUtils.replace(comment1.getText(), ";", ","));
                writer.append(';');
                writer.append(String.valueOf(legit));
                writer.append(';');
                if (!"".equals(extClassName)) {
                    writer.append(extClassName);
                    writer.append(';');
                }
                writer.append("\n");
            }
        }
    }

    private static void returnTagCloneCheck(FileWriter writer,
                                            String className,
                                            String extClassName,
                                            DocumentedExecutable first,
                                            DocumentedExecutable second,
                                            boolean legit) throws IOException {
        // Legitimate clone if overloading/overriding or if it's a return tag for same return type

        // ---------------------------
        // -----> NEW HEURISTIC <-----
        // ---------------------------
        // Same, non-primitive return type
        legit = legit || isSameReturnType(first, second);

        if (first.returnTag() != null && second.returnTag() != null) {
            String firstComment = first.returnTag().getComment().getText();
            String cleanFirst = StringUtils.replace(firstComment.trim(), "\n ", "");

            if (!cleanFirst.isEmpty()) {
                String cleanSecond = StringUtils.replace(second.returnTag().getComment().getText().trim(),
                        "\n ", "");
                if (cleanFirst.equals(cleanSecond)) {
                    // ---------------------------
                    // -----> VERY NEW HEURISTIC <-----
                    // ---------------------------
                    // Reference to {@code this}, where the return type is consistent w/ enclosing type
                    legit = legit || isReferenceToInstance(cleanFirst, className, extClassName, first, second);
//              System.out.println("\n@return tag clone: " + first.returnTag().getComment().getText() + "\n" +
//               " among " + first.getSignature() + " \nand " + second.getSignature());

                    if (SHOW_LEGIT || !legit) {
                        writer.append(className);
                        writer.append(';');
                        writer.append(first.toString());
                        writer.append(';');
                        writer.append(second.toString());
                        writer.append(';');
                        writer.append("@return");
                        writer.append(';');
                        writer.append("");
                        writer.append(';');
                        writer.append("");
                        writer.append(';');
                        writer.append(StringUtils.replace(firstComment, ";", ","));
                        writer.append(';');
                        writer.append(String.valueOf(legit));
                        writer.append(';');
                        if (!"".equals(extClassName)) {
                            writer.append(extClassName);
                            writer.append(';');
                        }
                        writer.append("\n");
                    }
                }
            }
        }
    }

    private static boolean isReferenceToInstance(String comment,
                                                 String className,
                                                 String extClassName,
                                                 DocumentedExecutable first,
                                                 DocumentedExecutable second) {
        if (!comment.contains("this")) {
            // FIXME SO naive.
            return false;
        }

        if (extClassName.isEmpty()) {
            extClassName = className;
        }

        String firstType = first.getReturnType();
        String secondType = second.getReturnType();
        // Clean possible generic types
        firstType = JavadocExtractor.rawType(firstType);
        secondType = JavadocExtractor.rawType(secondType);
        String fClassType = className.substring(className.lastIndexOf(".") + 1);
        String sClassType = extClassName.substring(extClassName.lastIndexOf(".") + 1);
        return firstType.equals(fClassType) && secondType.equals(sClassType);

    }

    private static void freeTextCloneCheck(FileWriter writer,
                                           String className,
                                           String extClassName,
                                           DocumentedExecutable first,
                                           DocumentedExecutable second,
                                           boolean legit) throws IOException {

        if (!freeTextToFilter(first.getJavadocFreeText())) {
            String cleanFirst = first.getJavadocFreeText().trim(); //.replaceAll("\n ", "");
            String cleanSecond = second.getJavadocFreeText().trim(); //.replaceAll("\n ", "");

            // If free text is the generic description of two constructors, let it be legit (assume rest of the doc
            // is compensating)
            legit = legit || isGenericConstructorComment(first, second, cleanFirst);

            if (SHOW_LEGIT || !legit) {
                if (cleanFirst.equals(cleanSecond)) {
//                                    System.out.println("\nFree text clone: " + first.getJavadocFreeText() + "\n" +
//                                            " among " + first.getSignature() + " \nand " + second.getSignature());
                    StringUtils.replace(cleanFirst, "\n ", "");
                    StringUtils.replace(cleanSecond, "\n ", "");

                    writer.append(className);
                    writer.append(';');
                    writer.append(first.toString());
                    writer.append(';');
                    writer.append(second.toString());
                    writer.append(';');
                    writer.append("Free text");
                    writer.append(';');
                    writer.append("");
                    writer.append(';');
                    writer.append("");
                    writer.append(';');
                    writer.append(StringUtils.replace(first.getJavadocFreeText(), ";", ","));
                    writer.append(';');
                    writer.append(String.valueOf(legit));
                    writer.append(';');
                    if (!"".equals(extClassName)) {
                        writer.append(extClassName);
                        writer.append(';');
                    }
                    writer.append("\n");
                }
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
        String noBlankFreeText = StringUtils.replace(freeText.trim(), "\n ", "");
        return noBlankFreeText.isEmpty()
                || noBlankFreeText.equals("{@inheritDoc}")
                || noBlankFreeText.equals("{@deprecated}");
    }

    /**
     * Check if comment clone could be because of an overloaded method.
     * Originally, this method was also labelling overriding as legitimate
     * clone.
     * <p>
     * Given the amount of actual copy&paste cases happening
     * because of overriding, we now prefer to keep the benefit of the doubt.
     * For overloading, the matter is less borderline: the difference is
     * only in the parameters; if @param are different, it is fine to
     * believe the clone is legitimate. Overload cases which do not differ
     * at least at the @param level will NOT be labeled as legitimate.
     *
     * @param firstName  first method name
     * @param secondName second method name
     * @return true if method overloading
     */
    private static boolean isOverloading(String firstName, String secondName) {
        firstName = firstName.toLowerCase();
        secondName = secondName.toLowerCase();
        return firstName.equals(secondName);
//                || firstName.contains(secondName)
//                || secondName.contains(firstName);

    }

    /**
     * Check if @return clone could be because of same (non-primitive) type.
     *
     * @return true if same return type
     */
    private static boolean isSameReturnType(DocumentedExecutable first, DocumentedExecutable second) {
        String firstType = first.getReturnType();
        // Clean possible generic types
        firstType = JavadocExtractor.rawType(firstType);
        String secondType = second.getReturnType();
        secondType = JavadocExtractor.rawType(secondType);

        // Constructors
        if (firstType.trim().isEmpty()) {
            return secondType.trim().isEmpty();
        }
        if (Reflection.getPrimitiveClasses().get(firstType) != null) {
            // one of the two is a PRIMITIVE type: always consider potential clone
            return false;
        } else return firstType.equals(secondType);
    }
    /**
     * From a list of files in path, finds the fully qualified names of Java classes.
     *
     * @param list collection of {@code Files}
     * @param path the String path to search in
     * @return fully qualified names found, as a list of Strings
     */
    private static List<String> getClassesInFolder(Collection<File> list, String path) {
        List<String> selectedClassNames = new ArrayList<>();
        for (File file : list) {
            if (!file.getName().endsWith(".java") || file.getName().contains("package-info")) {
                continue;
            }
            String fileName = file.getAbsolutePath();
            String[] unnecessaryPrefix = fileName.split(path);
            String className = StringUtils.replace(unnecessaryPrefix[1], "\\", ".");

            selectedClassNames.add(className.replace(".java", ""));
        }
        return selectedClassNames;

    }

    private static void prepareOutputFileForCommentedOutCode(Writer writer){
        try {
            writer.append("Class Name`");
            writer.append("Comment Content`");
            writer.append("Lines`");
            writer.append("Path \n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void addDataToOutputFile(Writer writer, List<CommentedOutCodeResult> results){
        for (CommentedOutCodeResult result: results) {
            try {
                writer.append(result.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
