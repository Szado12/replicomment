package org.replicomment.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.comments.Comment;
import org.replicomment.extractor.CommentedOutCodeResult;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
public class CommentedOutCodeChecker {


    List<CommentedOutCodeResult> resultList;
    List<Comment> singleWordComments;
    List<String> commonJavaSingleWords = new ArrayList() {{
        add("public");
        add("protected");
        add("private");
        add("static");
        add("else");
        add("return");
    }};
    List<CommentedOutCodeResult> singleWordCommentsResult;
    public Map<String, List<CommentedOutCodeResult>> CheckForCodeInComment(List<Comment> comments, String folder, String fileName, boolean groupSingleLineComments){
        resultList = new ArrayList<>();
        singleWordComments = new ArrayList<>();
        singleWordCommentsResult = new ArrayList<>();
        String filePath = findFilePath(folder,fileName);
        comments.sort(Comparator.comparingInt(o -> o.getBegin().get().line));
        if(groupSingleLineComments)
            comments =  groupSingleLineComments(comments);

        comments = comments.stream().filter(com -> !com.getContent().isEmpty() && com.getContent().trim().length() > 0).collect(Collectors.toList());
        for (Comment comment: comments) {
            String unc = Uncomment(comment,filePath);
            if(CheckUncommentedCode(unc))
                resultList.add(new CommentedOutCodeResult(fileName,comment.getContent().replaceAll("[\\t\\n\\r]+"," "),comment.getRange().get(),filePath));
        }
        for (Comment comment: singleWordComments) {
            singleWordCommentsResult.add(new CommentedOutCodeResult(fileName,comment.getContent().replaceAll("[\\t\\n\\r]+"," "),comment.getRange().get(),filePath));
        }
        Map<String,List<CommentedOutCodeResult>> map =new HashMap();
        map.put("commentedOutCodeResult",resultList);
        map.put("singleWordComments",singleWordCommentsResult);
        return map;
    }

    private String findFilePath(String folderPath, String className){
        String y = folderPath + className.replaceAll("\\.", "\\\\\\\\") + ".java";
        return  y;
    }
    public String Uncomment(Comment comment, String filePath){
        int startLine, endLine;

        startLine = comment.getBegin().get().line;
        endLine = comment.getEnd().get().line;

        StringBuilder stringBuilder = new StringBuilder();
        String line;
        Scanner scanner = null;
        try {
            scanner = new Scanner(new File(filePath));
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }
        for(int i =1; scanner.hasNextLine();i++)
        {
            line = scanner.nextLine();
            if(i>= startLine && i<= endLine){
                if(comment.isLineComment())
                    line = line.replace("//","");
                else
                    line = line.replace("/**","")
                        .replace("/*","")
                        .replace("*/","");
            }
            stringBuilder.append(line);
            stringBuilder.append(System.lineSeparator());
        }
        return stringBuilder.toString();
    }

    private void AddGroupedComment(List<Comment> groupedComments,Comment comment){
        if(comment != null)
        {
            if(IsSingleWordComment(comment.getContent()))
                singleWordComments.add(comment);
            else
                groupedComments.add(comment);
        }
    }

    private List<Comment> groupSingleLineComments(List<Comment> comments){
        List<Comment> groupedComments = new ArrayList<>();

        int previousLineCommentLineNumber = -1;
        int previousLineCommentColumn = -1;
        Comment previousComment = null;
        for (Comment comment: comments) {
            if(comment.isLineComment()){
                if(previousLineCommentLineNumber +1 == comment.getBegin().get().line && previousLineCommentColumn == comment.getBegin().get().column){
                    previousComment.setRange(new Range(previousComment.getBegin().get(),comment.getEnd().get()));
                    previousComment.setContent(previousComment.getContent() +" "+ comment.getContent());
                }
                else{
                    AddGroupedComment(groupedComments,previousComment);
                    previousComment = comment;
                }
                previousLineCommentLineNumber = comment.getEnd().get().line;
                previousLineCommentColumn = comment.getBegin().get().column;
            }
            else{
                AddGroupedComment(groupedComments,comment);
            }
        }
        if(!groupedComments.contains(previousComment)) {
            AddGroupedComment(groupedComments,previousComment);
        }
        return groupedComments;
    }
    private boolean IsSingleWordComment(String comment){
        StringTokenizer tokenizer = new StringTokenizer(comment);
        if(tokenizer.countTokens() != 1)
            return false;
        String token = tokenizer.nextToken();
        if(commonJavaSingleWords.contains(token.toLowerCase(Locale.ROOT)))
            return false;
        Pattern singleWordPattern = Pattern.compile("[[a-z][A-Z][0-9][_]]{"+token.length()+"}");
        Matcher matcher = singleWordPattern.matcher(token);
        int index = 0;
        while(matcher.find())
            index++;
        return index == 1;
    }

    private Boolean CheckUncommentedCode(String code){
        try {
            CompilationUnit cu = JavaParser.parse(code);
            return true;
        }
        catch (Exception e){
            return false;
        }
    }
}
