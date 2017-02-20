////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2016 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle.checks;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.utils.CheckUtils;
import com.puppycrawl.tools.checkstyle.utils.CommonUtils;
import com.puppycrawl.tools.checkstyle.utils.AnnotationUtility;
import com.puppycrawl.tools.checkstyle.utils.ScopeUtils;

public class NullAnnotationsCheck extends AbstractCheck {

    /**
     * A keys are pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_MISSED_ANNOTATION_KEY = "Missed @NonNull or @Nullable annotation for ";
    public static final String MSG_USELESS_ANNOTATION_KEY = "Useless @NonNull or @Nullable annotation for ";

    private static final String NON_NULL = "NonNull";
    private static final String NULLABLE = "Nullable";

    /**
     * Contains
     * <a href="http://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html">
     * primitive datatypes</a>.
     */
    private final Set<Integer> primitiveDataTypes = Collections.unmodifiableSet(
        Stream.of(
            TokenTypes.LITERAL_VOID,
            TokenTypes.LITERAL_BYTE,
            TokenTypes.LITERAL_SHORT,
            TokenTypes.LITERAL_INT,
            TokenTypes.LITERAL_LONG,
            TokenTypes.LITERAL_FLOAT,
            TokenTypes.LITERAL_DOUBLE,
            TokenTypes.LITERAL_BOOLEAN,
            TokenTypes.LITERAL_CHAR)
        .collect(Collectors.toSet()));

    @Override
    public int[] getDefaultTokens() {
        return new int[] {
            TokenTypes.VARIABLE_DEF,
            TokenTypes.METHOD_DEF,
            TokenTypes.CTOR_DEF,
        };
    }

    @Override
    public int[] getAcceptableTokens() {
        return new int[] {
            TokenTypes.VARIABLE_DEF,
            TokenTypes.METHOD_DEF,
            TokenTypes.CTOR_DEF
        };
    }

    @Override
    public int[] getRequiredTokens() {
        return CommonUtils.EMPTY_INT_ARRAY;
    }

    @Override
    public void visitToken(DetailAST ast) {
        final DetailAST container = ast.getParent().getParent();
        if(ast.getType() == TokenTypes.VARIABLE_DEF) {
            if (ScopeUtils.isClassFieldDef(ast)) {
                visitField(ast);
            }
        } else {
            visitMethod(ast);
        }
    }

    private void visitField(final DetailAST field) {
        final DetailAST modifiersAST =
                field.findFirstToken(TokenTypes.MODIFIERS);
        final boolean isStatic = modifiersAST.branchContains(TokenTypes.LITERAL_STATIC);
        final boolean isFinal = modifiersAST.branchContains(TokenTypes.FINAL);

        checkToken(field, isStatic && isFinal);
    }

    private void visitMethod(final DetailAST method) {
        checkToken(method, false);

        final DetailAST modifiers =
            method.findFirstToken(TokenTypes.MODIFIERS);
        // exit on fast lane if there is nothing to check here

        if (method.branchContains(TokenTypes.PARAMETER_DEF)
                // ignore abstract and native methods
                && !modifiers.branchContains(TokenTypes.LITERAL_NATIVE)) {
            // we can now be sure that there is at least one parameter
            final DetailAST parameters =
                method.findFirstToken(TokenTypes.PARAMETERS);
            DetailAST child = parameters.getFirstChild();
            while (child != null) {
                // children are PARAMETER_DEF and COMMA
                if (child.getType() == TokenTypes.PARAMETER_DEF) {
                    checkToken(child, false);
                }
                child = child.getNextSibling();
            }
        }
    }

    private void checkToken(final DetailAST ast, final boolean ignore) {
        boolean containsAnnotation = AnnotationUtility.containsAnnotation(ast, NON_NULL)
                || AnnotationUtility.containsAnnotation(ast, NULLABLE);
        boolean ignored = ignore || isIgnoredToken(ast);
        if (!ignored && !containsAnnotation) {
            final DetailAST paramName = ast.findFirstToken(TokenTypes.IDENT);
            final DetailAST firstNode = CheckUtils.getFirstNode(ast);
            log(firstNode.getLineNo(), firstNode.getColumnNo(),
                MSG_MISSED_ANNOTATION_KEY + paramName.getText());
        } else if (ignored && containsAnnotation) {
            final DetailAST paramName = ast.findFirstToken(TokenTypes.IDENT);
            final DetailAST firstNode = CheckUtils.getFirstNode(ast);
            log(firstNode.getLineNo(), firstNode.getColumnNo(),
                MSG_USELESS_ANNOTATION_KEY + paramName.getText());
        }
    }

    private boolean isIgnoredToken(DetailAST token) {
        final DetailAST parameterType = token
            .findFirstToken(TokenTypes.TYPE);
        // constructor
        if(parameterType == null) {
            return true;
        }
        if (primitiveDataTypes.contains(parameterType.getFirstChild().getType())) {
            return true;
        }
        return false;
    }
}
