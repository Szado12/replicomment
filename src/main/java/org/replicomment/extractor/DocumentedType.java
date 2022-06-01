package org.replicomment.extractor;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.comments.Comment;
import org.replicomment.util.Checks;

import java.util.List;
import java.util.Objects;

/** Represents a class or interface that is documented with Javadoc comments. */
public final class DocumentedType {

  /** Documented class or interface (or enum, ...). */
  private final Class<?> documentedClass;
  /** Source class as parsed by Javaparser. */
  ClassOrInterfaceDeclaration sourceClass;
  /** Constructors and methods of this documented type. */
  private final List<DocumentedExecutable> documentedExecutables;

  /** Fields of this documented type. */
  private final List<DocumentedField> documentedFields;

  public List<Comment> getAllComments() {
    return allComments;
  }

  private final List<com.github.javaparser.ast.comments.Comment> allComments;
  /**
   * Creates a new DocumentedType wrapping the given class and with the given constructors and
   * methods.
   *
   * @param documentedClass the {@code Class} of this documentedClass
   * @param sourceClass
   * @param documentedExecutables constructors and methods of {@code documentedClass}
   * @throws NullPointerException if either documentedClass or documentedExecutables is null
   */
  DocumentedType(Class<?> documentedClass, ClassOrInterfaceDeclaration sourceClass,
                 List<DocumentedExecutable> documentedExecutables,
                 List<DocumentedField> documentedFields, List<com.github.javaparser.ast.comments.Comment> allComments) {
    Checks.nonNullParameter(documentedClass, "documentedClass");
    Checks.nonNullParameter(documentedExecutables, "documentedExecutables");
//    Checks.nonNullParameter(sourceClass, "sourceClass");
    this.sourceClass = sourceClass;
    this.documentedClass = documentedClass;
    this.documentedExecutables = documentedExecutables;
    this.documentedFields = documentedFields;
    this.allComments = allComments;
  }

  /**
   * Returns the runtime class of the documented type this DocumentedType represents.
   *
   * @return the runtime class of the documented type this DocumentedType represents
   */
  Class<?> getDocumentedClass() {
    return documentedClass;
  }

  public ClassOrInterfaceDeclaration getSourceClass() {
    return sourceClass;
  }

  /**
   * Returns constructors and methods of this {@code DocumentedType}.
   *
   * @return constructors and methods of this {@code DocumentedType}
   */
  public List<DocumentedExecutable> getDocumentedExecutables() {
    return documentedExecutables;
  }

  /**
   * Returns fields of this {@code DocumentedType}.
   *
   * @return fields of this {@code DocumentedType}.
   */
  public List<DocumentedField> getDocumentedFields() {
    return documentedFields;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DocumentedType)) {
      return false;
    }
    DocumentedType that = (DocumentedType) o;
    return documentedClass.equals(that.documentedClass)
        && documentedExecutables.equals(that.documentedExecutables);
  }

  @Override
  public int hashCode() {
    return Objects.hash(documentedClass, documentedExecutables);
  }
}
