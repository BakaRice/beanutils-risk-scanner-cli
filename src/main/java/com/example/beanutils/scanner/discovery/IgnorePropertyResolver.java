package com.example.beanutils.scanner.discovery;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class IgnorePropertyResolver {
    Result resolve(List<Expression> expressions, CompilationUnit unit) {
        Set<String> names = new LinkedHashSet<>();
        boolean complete = true;
        for (Expression expression : expressions) {
            complete &= collect(expression, unit, names, new LinkedHashSet<>());
        }
        return new Result(names, complete);
    }

    private boolean collect(Expression expression, CompilationUnit unit, Set<String> names, Set<String> visiting) {
        if (expression instanceof StringLiteralExpr literal) {
            names.add(literal.asString());
            return true;
        }
        if (expression instanceof ArrayCreationExpr array) {
            return array.getInitializer().map(value -> collect(value, unit, names, visiting)).orElse(false);
        }
        if (expression instanceof ArrayInitializerExpr initializer) {
            boolean complete = true;
            for (Expression value : initializer.getValues()) {
                complete &= collect(value, unit, names, visiting);
            }
            return complete;
        }
        if (expression instanceof NameExpr name) {
            if (!visiting.add(name.getNameAsString())) {
                return false;
            }
            Optional<VariableDeclarator> declaration = unit.findAll(VariableDeclarator.class).stream()
                    .filter(variable -> variable.getNameAsString().equals(name.getNameAsString()))
                    .filter(variable -> variable.getInitializer().isPresent())
                    .findFirst();
            boolean complete = declaration
                    .map(variable -> collect(variable.getInitializer().orElseThrow(), unit, names, visiting))
                    .orElse(false);
            visiting.remove(name.getNameAsString());
            return complete;
        }
        return false;
    }

    record Result(Set<String> names, boolean complete) {
    }
}
