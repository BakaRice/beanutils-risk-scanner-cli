package com.example.beanutils.scanner.discovery;

import com.example.beanutils.scanner.project.MavenProjectLoader;
import com.example.beanutils.scanner.project.ProjectModel;
import com.example.beanutils.scanner.source.ParsedSource;
import com.example.beanutils.scanner.source.SourceIndexer;
import com.example.beanutils.scanner.source.SourceWorkspace;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.SymbolResolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanUtilsCallDetectorTest {
    @TempDir Path temp;

    @Test
    void discoversSpringCallsAndRejectsNamesakes() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/copy-calls").toAbsolutePath();
        var project = new MavenProjectLoader().load(fixture, false,
                Path.of(System.getProperty("user.home"), ".m2", "repository"));
        var workspace = new SourceIndexer().index(project);

        List<CopyCallSite> calls = new BeanUtilsCallDetector().discover(workspace);

        assertEquals(8, calls.size());
        assertTrue(calls.stream().anyMatch(call -> call.form() == CopyCallForm.IGNORE_PROPERTIES
                && call.ignoredProperties().contains("items") && call.ignoredProperties().contains("tags")));
        assertTrue(calls.stream().anyMatch(call -> call.form() == CopyCallForm.EDITABLE));
        assertTrue(calls.stream().anyMatch(call -> call.form() == CopyCallForm.METHOD_REFERENCE));
        assertTrue(calls.stream().noneMatch(call -> call.code().contains("OtherBeanUtils")));
    }

    @Test
    void keepsDiscoveringTheCallWhenTypeResolutionThrowsNoClassDefFoundError() {
        String code = """
                package fixture;
                import org.springframework.beans.BeanUtils;
                class CopyService {
                    void copy(Source source, Target target) {
                        BeanUtils.copyProperties(source, target);
                    }
                }
                """;
        var unit = StaticJavaParser.parse(code);
        Path file = temp.resolve("src/main/java/fixture/CopyService.java");
        unit.setStorage(file);
        unit.setData(Node.SYMBOL_RESOLVER_KEY, new MissingInterfaceSymbolResolver());
        var project = new ProjectModel(temp, List.of(), List.of(), List.of(), List.of());
        var workspace = new SourceWorkspace(project, new CombinedTypeSolver(),
                List.of(new ParsedSource(file, "fixture", unit, code)), List.of(), null);

        List<CopyCallSite> calls = new BeanUtilsCallDetector().discover(workspace);

        assertEquals(1, calls.size());
        assertEquals("BeanUtils.copyProperties(source, target)", calls.get(0).code());
        assertTrue(calls.get(0).resolvedSourceType() == null);
        assertTrue(calls.get(0).resolvedTargetType() == null);
    }

    private static final class MissingInterfaceSymbolResolver implements SymbolResolver {
        @Override
        public <T> T resolveDeclaration(Node node, Class<T> resultClass) {
            throw missingInterface();
        }

        @Override
        public <T> T toResolvedType(Type type, Class<T> resultClass) {
            throw missingInterface();
        }

        @Override
        public ResolvedType calculateType(Expression expression) {
            throw missingInterface();
        }

        @Override
        public ResolvedReferenceTypeDeclaration toTypeDeclaration(Node node) {
            throw missingInterface();
        }

        private NoClassDefFoundError missingInterface() {
            return new NoClassDefFoundError("com/baomidou/mybatisplus/service/IService");
        }
    }
}
