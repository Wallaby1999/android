/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.groovy;

import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.java.JavaVersionDslElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.SOURCE_COMPATIBILITY_ATTRIBUTE_NAME;
import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.TARGET_COMPATIBILITY_ATTRIBUTE_NAME;
import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.*;
import static com.android.tools.idea.gradle.dsl.parser.java.LanguageLevelUtil.convertToGradleString;
import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;

public class GroovyDslWriter implements GradleDslWriter {
  @Override
  public PsiElement createDslElement(@NotNull GradleDslElement element) {
    GroovyPsiElement psiElement = ensureGroovyPsi(element.getPsiElement());
    if (psiElement != null) {
      return psiElement;
    }

    GradleDslElement parent = element.getParent();
    if (parent == null) {
      return null;
    }

    GroovyPsiElement parentPsiElement = ensureGroovyPsi(parent.create());
    if (parentPsiElement == null) {
      return null;
    }
    Project project = parentPsiElement.getProject();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    if (isNewEmptyBlockElement(element)) {
      return null; // Avoid creation of an empty block statement.
    }

    String statementText = element.getName();
    if (element.isBlockElement()) {
      statementText += " {\n}\n";
    } else if (element.shouldUseAssignment()) {
      statementText += " = 'abc'";
    } else {
      statementText += "\"abc\", \"xyz\"";
    }
    GrStatement statement = factory.createStatementFromText(statementText);
    // TODO: Move these workarounds to a more sensible way of doing things.
    if (statement instanceof GrApplicationStatement) {
      // Workaround to create an application statement.
      ((GrApplicationStatement)statement).getArgumentList().delete();
    } else if (statement instanceof GrAssignmentExpression) {
      // Workaround to create an assignment statetment
      GrAssignmentExpression assignment = (GrAssignmentExpression)statement;
      if (assignment.getRValue() != null) {
        assignment.getRValue().delete();
      }
    }
    PsiElement lineTerminator = factory.createLineTerminator(1);
    PsiElement addedElement;
    if (parentPsiElement instanceof GroovyFile) {
      addedElement = parentPsiElement.addAfter(statement, parentPsiElement.getLastChild());
      parentPsiElement.addBefore(lineTerminator, addedElement);
    }
    else {
      addedElement = parentPsiElement.addBefore(statement, parentPsiElement.getLastChild());
      parentPsiElement.addAfter(lineTerminator, addedElement);
    }
    if (element.isBlockElement()) {
      GrClosableBlock closableBlock = getClosableBlock(addedElement);
      if (closableBlock != null) {
        element.setPsiElement(closableBlock);
      }
    }
    else {
      if (addedElement instanceof GrApplicationStatement || addedElement instanceof GrAssignmentExpression) {
        // This is for the workarounds above, this ensures that applyDslLiteral is called to actually add the value to
        // either the application or assignment statement.
        element.setPsiElement(addedElement);
      }
    }
    return element.getPsiElement();
  }

  @Override
  public void deleteDslElement(@NotNull GradleDslElement element) {
    PsiElement psiElement = element.getPsiElement();
    if (psiElement == null || !psiElement.isValid()) {
      return;
    }

    PsiElement parent = psiElement.getParent();
    psiElement.delete();

    if (parent != null) {
      deleteIfEmpty(parent);
    }

    element.setPsiElement(null);
  }

  @Override
  public PsiElement createDslLiteral(@NotNull GradleDslLiteral literal) {
    GradleDslElement parent = literal.getParent();

    if (!(parent instanceof GradleDslExpressionMap)) {
      return createDslElement(literal);
    }
    // This is a value in the map element we need to create a named argument for it.
    GroovyPsiElement parentPsiElement = ensureGroovyPsi(parent.create());
    if (parentPsiElement == null) {
      return null;
    }

    literal.setPsiElement(parentPsiElement);
    GrLiteral newLiteral = extractUnsavedLiteral(literal);
    if (newLiteral == null) {
      return null;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(newLiteral.getProject());
    GrNamedArgument namedArgument = factory.createNamedArgument(literal.getName(), newLiteral);
    PsiElement added;
    if (parentPsiElement instanceof GrArgumentList) {
      added = ((GrArgumentList)parentPsiElement).addNamedArgument(namedArgument);
    }
    else {
      added = parentPsiElement.addAfter(namedArgument, parentPsiElement.getLastChild());
    }
    if (added instanceof GrNamedArgument) {
      GrNamedArgument addedNameArgument = (GrNamedArgument)added;
      GrLiteral grLiteral = getChildOfType(addedNameArgument, GrLiteral.class);
      if (grLiteral != null) {
        literal.setExpression(grLiteral);
        literal.setModified(false);
        literal.reset();
        return literal.getPsiElement();
      }
    }
    return null;
  }

  @Override
  public void applyDslLiteral(@NotNull GradleDslLiteral literal) {
    PsiElement psiElement = ensureGroovyPsi(literal.getPsiElement());
    if (psiElement == null) {
      return;
    }

    GrLiteral newLiteral = extractUnsavedLiteral(literal);
    if (newLiteral == null) {
      return;
    }
    PsiElement expression = ensureGroovyPsi(literal.getExpression());
    if (expression != null) {
      PsiElement replace = expression.replace(newLiteral);
      if (replace instanceof GrLiteral) {
        literal.setExpression(replace);
      }
    }
    else {
      PsiElement added;
      if (psiElement instanceof GrListOrMap || // Entries in [].
          (psiElement instanceof GrArgumentList && !(psiElement instanceof GrCommandArgumentList))) { // Method call arguments in ().
        added = psiElement.addBefore(newLiteral, psiElement.getLastChild()); // add before ) or ]
      }
      else {
        added = psiElement.addAfter(newLiteral, psiElement.getLastChild());
      }
      if (added instanceof GrLiteral) {
        literal.setExpression(added);
      }

      if (literal.getUnsavedConfigBlock() != null) {
        addConfigBlock(literal);
      }
    }

    literal.reset();
    literal.setModified(false);
  }

  @Override
  public void deleteDslLiteral(@NotNull GradleDslLiteral literal) {
    PsiElement expression = literal.getExpression();
    if (expression == null) {
      return;
    }
    PsiElement parent = expression.getParent();
    expression.delete();
    deleteIfEmpty(parent);
  }

  @Override
  public PsiElement createDslReference(@NotNull GradleDslReference reference) {
    return createDslElement(reference);
  }

  @Override
  public void applyDslReference(@NotNull GradleDslReference reference) {
    PsiElement psiElement = ensureGroovyPsi(reference.getPsiElement());
    if (psiElement == null) {
      return;
    }

    PsiElement newReference = extractUnsavedReference(reference);
    if (newReference == null) {
      return;
    }

    PsiElement expression = ensureGroovyPsi(reference.getExpression());
    if (expression != null) {
      PsiElement replace = expression.replace(newReference);
      reference.setExpression(replace);
    } else {
      PsiElement added;
      added = psiElement.addAfter(newReference, psiElement.getLastChild());
      reference.setExpression(added);
    }

    reference.reset();
    reference.setModified(false);
  }

  @Override
  public void deleteDslReference(@NotNull GradleDslReference reference) {
    PsiElement expression = reference.getExpression();
    if (expression == null) {
      return;
    }
    PsiElement parent = expression.getParent();
    expression.delete();
    deleteIfEmpty(parent);
  }

  @Override
  public PsiElement createDslMethodCall(@NotNull GradleDslMethodCall methodCall) {
    PsiElement psiElement = methodCall.getPsiElement();
    if (psiElement != null) {
      return psiElement;
    }

    if (methodCall.getParent() == null) {
      return null;
    }

    PsiElement parentPsiElement = methodCall.getParent().create();
    if (parentPsiElement == null) {
      return null;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parentPsiElement.getProject());
    String statementText = (methodCall.getStatementName() != null ? methodCall.getStatementName() + " " : "") + methodCall.getName() + "()";
    GrStatement statement = factory.createStatementFromText(statementText);
    PsiElement addedElement = parentPsiElement.addBefore(statement, parentPsiElement.getLastChild());

    if (addedElement instanceof GrApplicationStatement) {
      GrExpression[] expressionArguments = ((GrApplicationStatement)addedElement).getArgumentList().getExpressionArguments();
      if (expressionArguments.length == 1 && expressionArguments[0] instanceof GrMethodCallExpression) {
        methodCall.setPsiElement(expressionArguments[0]);
        return methodCall.getPsiElement();
      }
    }

    if (addedElement instanceof GrMethodCallExpression) {
      methodCall.setPsiElement(addedElement);
      return methodCall.getPsiElement();
    }

    return null;
  }

  @Override
  public void applyDslMethodCall(@NotNull GradleDslMethodCall element) {
    PsiElement psiElement = element.getPsiElement();
    if (psiElement instanceof GrMethodCallExpression) {
      GrMethodCallExpression methodCall = (GrMethodCallExpression)psiElement;
      GradleDslElement toBeAddedArgument = element.getToBeAddedArgument();
      if (toBeAddedArgument != null) {
        toBeAddedArgument.setPsiElement(methodCall.getArgumentList());
        toBeAddedArgument.applyChanges();
        element.commitNewArgument(toBeAddedArgument);
      }
    }

    for (GradleDslElement argument : element.getArguments()) {
      if (argument.isModified()) {
        argument.applyChanges();
      }
    }
  }

  @Override
  public PsiElement createDslExpressionList(@NotNull GradleDslExpressionList expressionList) {
    PsiElement psiElement = expressionList.getPsiElement();
    if (psiElement == null) {
      if (expressionList.getParent() instanceof GradleDslExpressionMap) {
        // This is a list in the map element and we need to create a named argument for it.
        return createNamedArgumentList(expressionList);
      }
      psiElement = createDslElement(expressionList);
    }

    if (psiElement == null) {
      return null;
    }

    if (psiElement instanceof GrListOrMap) {
      return psiElement;
    }

    if (psiElement instanceof GrArgumentList) {
      if (!expressionList.getToBeAddedExpressions().isEmpty() &&
          ((GrArgumentList)psiElement).getAllArguments().length == 1 &&
          !expressionList.isAppendToArgumentListWithOneElement()) {
        // Sometimes it's not possible to append to the arguments list with one item. eg. proguardFile "xyz".
        // Set the psiElement to null and create a new psiElement of an empty application statement.
        expressionList.setPsiElement(null);
        psiElement = createDslElement(expressionList);
      }
      else {
        return psiElement;
      }
    }

    if (psiElement instanceof GrApplicationStatement) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(psiElement.getProject());
      GrArgumentList argumentList = factory.createArgumentListFromText("xyz");
      argumentList.getFirstChild().delete(); // Workaround to get an empty argument list.
      PsiElement added = psiElement.addAfter(argumentList, psiElement.getLastChild());
      if (added instanceof GrArgumentList) {
        GrArgumentList addedArgumentList = (GrArgumentList)added;
        expressionList.setPsiElement(addedArgumentList);
        return addedArgumentList;
      }
    }

    return null;
  }

  @Override
  public PsiElement createDslExpressionMap(@NotNull GradleDslExpressionMap expressionMap) {
    PsiElement psiElement = createDslElement(expressionMap);
    if (psiElement == null) {
      return null;
    }

    if (psiElement instanceof GrListOrMap || psiElement instanceof GrArgumentList) {
      return psiElement;
    }

    if (psiElement instanceof GrApplicationStatement) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(psiElement.getProject());
      GrArgumentList argumentList = factory.createArgumentListFromText("xyz");
      argumentList.getFirstChild().delete(); // Workaround to get an empty argument list.
      PsiElement added = psiElement.addAfter(argumentList, psiElement.getLastChild());
      if (added instanceof GrArgumentList) {
        GrArgumentList addedArgumentList = (GrArgumentList)added;
        expressionMap.setPsiElement(addedArgumentList);
        return addedArgumentList;
      }
    }

    return null;
  }

  @Override
  public PsiElement createDslJavaVersionElement(@NotNull JavaVersionDslElement element) {
    GroovyPsiElement psiElement = ensureGroovyPsi(extractCorrectJavaVersionPsiElement(element));
    if (psiElement != null) {
      return psiElement;
    }

    GradlePropertiesDslElement parent = (GradlePropertiesDslElement)element.getParent();
    assert parent != null;
    PsiElement javaPsiElement = parent.create();
    assert javaPsiElement != null;
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(javaPsiElement.getProject());

    // Tries to create the new element close to targetCompatibility or sourceCompatibility, if neither of them exists, create it at the
    // end of the file.
    // Also, tries to copy the value from targetCompatibility or sourceCompatibility if possible to keep consistency.
    JavaVersionDslElement anchor = null;

    String name = element.getName();
    if (SOURCE_COMPATIBILITY_ATTRIBUTE_NAME.equals(name)) {
      anchor = parent.getPropertyElement(TARGET_COMPATIBILITY_ATTRIBUTE_NAME, JavaVersionDslElement.class);
    }
    else if (TARGET_COMPATIBILITY_ATTRIBUTE_NAME.equals(name)) {
      anchor = parent.getPropertyElement(SOURCE_COMPATIBILITY_ATTRIBUTE_NAME, JavaVersionDslElement.class);
    }

    PsiElement anchorPsiElement = null;
    String anchorText = null;
    if (anchor != null) {
      anchorPsiElement = anchor.getPsiElement();
      anchorText = anchor.getVersionText();
    }

    if (anchorPsiElement == null) {
      anchorPsiElement = javaPsiElement.getLastChild();
    }

    if (anchorText == null) {
      anchorText = "1.6";
    }

    GrExpression newExpressionPsi;
    GroovyPsiElement valuePsi;
    if (element.shouldUseAssignment()) {
      GrExpression expression = factory.createExpressionFromText(name + " = " + anchorText);
      newExpressionPsi = (GrExpression)javaPsiElement.addBefore(expression, anchorPsiElement);
      valuePsi = ((GrAssignmentExpression)newExpressionPsi).getRValue();
    }
    else {
      GrExpression expression = factory.createExpressionFromText(name + " " + anchorText);
      newExpressionPsi = (GrExpression)javaPsiElement.addBefore(expression, anchorPsiElement);
      valuePsi = ((GrApplicationStatement)newExpressionPsi).getExpressionArguments()[0];
    }

    if (valuePsi instanceof GrLiteral) {
      element.setVersionElement(new GradleDslLiteral(parent, newExpressionPsi, name, valuePsi));
    }
    else if (valuePsi instanceof GrReferenceExpression) {
      element.setVersionElement(new GradleDslReference(parent, newExpressionPsi, name, valuePsi));
    }
    return element.getPsiElement();
  }

  @Override
  public void applyDslJavaVersionElement(@NotNull JavaVersionDslElement element) {
    PsiElement psiElement = extractCorrectJavaVersionPsiElement(element);

    LanguageLevel newValue = element.getUnsavedValue();
    if (newValue == null || psiElement == null) {
      return;
    }

    String groovyString = convertToGradleString(newValue, element.getVersionText());
    GrExpression newVersionPsi = GroovyPsiElementFactory.getInstance(psiElement.getProject()).createExpressionFromText(groovyString);

    GrExpression oldVersionPsi;
    if (element.shouldUseAssignment()) {
      oldVersionPsi = ((GrAssignmentExpression)psiElement).getRValue();
    }
    else {
      oldVersionPsi = ((GrApplicationStatement)psiElement).getExpressionArguments()[0];
    }

    assert oldVersionPsi != null;
    oldVersionPsi.replace(newVersionPsi);
  }

  @Override
  public void deleteDslJavaVersionElement(@NotNull JavaVersionDslElement element) {
    PsiElement psiElement = extractCorrectJavaVersionPsiElement(element);
    if (psiElement != null) {
      PsiElement parent = psiElement.getParent();
      psiElement.delete();
      deleteIfEmpty(parent);
    }
  }
}
