package org.replicomment.extractor;

import com.github.javaparser.Range;

public class CommentedOutCodeResult {
    @Override
    public String toString() {
        return className + '`'
                + commentContent + '`'
                + lineNumbers.begin.line +'-'+lineNumbers.end.line +'`'
                + path + '\n';
    }

    public CommentedOutCodeResult(String className, String commentContent, Range lineNumbers, String path) {
        this.className = className;
        this.commentContent = commentContent;
        this.lineNumbers = lineNumbers;
        this.path = path;
    }

    private String className;
    private String path;
    private String commentContent;
    private Range lineNumbers;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getCommentContent() {
        return commentContent;
    }

    public void setCommentContent(String commentContent) {
        this.commentContent = commentContent;
    }

    public Range getLineNumbers() {
        return lineNumbers;
    }

    public void setLineNumbers(Range lineNumbers) {
        this.lineNumbers = lineNumbers;
    }


}
