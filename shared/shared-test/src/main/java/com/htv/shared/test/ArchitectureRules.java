package com.htv.shared.test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/**
 * Luật kiến trúc của mọi service (thiết kế mục 3.6). Mỗi service có một test gọi {@link #check(String)}
 * với gói gốc của nó.
 *
 * <pre>
 * interfaces ──► application ──► domain
 * infrastructure ──► application, domain
 * </pre>
 *
 * <ul>
 *   <li>{@code domain}: Java thuần. Không import Spring, gRPC, Jackson, Servlet, và không biết tầng khác.
 *   <li>{@code application}: không biết {@code infrastructure} và {@code interfaces}.
 *   <li>{@code interfaces} (REST, gRPC, listener): chỉ gọi {@code application}; không chạm {@code infrastructure}.
 *   <li>{@code infrastructure}: không chạm {@code interfaces}.
 *   <li>Service không import lớp của service khác (chỉ qua {@code contracts/*}).
 * </ul>
 */
public final class ArchitectureRules {

    /** Gói gốc của các service hiện có. Thêm service mới thì thêm vào đây. */
    public static final List<String> SERVICE_PACKAGES = List.of("com.htv.identity", "com.htv.order", "com.htv.notification");

    private ArchitectureRules() {
    }

    /** Chạy mọi luật trên mã (không gồm test) của service có gói gốc {@code basePackage}. */
    public static void check(String basePackage) {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
        for (ArchRule rule : rules(basePackage)) {
            rule.check(classes);
        }
    }

    public static List<ArchRule> rules(String basePackage) {
        String domain = basePackage + ".domain..";
        String application = basePackage + ".application..";
        String infrastructure = basePackage + ".infrastructure..";
        String interfaces = basePackage + ".interfaces..";

        ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer("Domain").definedBy(domain)
                .layer("Application").definedBy(application)
                .layer("Infrastructure").definedBy(infrastructure)
                .layer("Interfaces").definedBy(interfaces)
                .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces")
                .because("thiết kế: interfaces -> application -> domain; infrastructure -> application, domain");

        ArchRule pureDomain = noClasses().that().resideInAPackage(domain)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "io.grpc..", "com.fasterxml.jackson..", "tools.jackson..",
                        "jakarta.servlet..", "feign..")
                .allowEmptyShould(true)
                .because("domain là Java thuần (JPA annotation và lớp gốc entity của jpa-starter được phép)");

        ArchRule noOtherService = noClasses().that().resideInAPackage(basePackage + "..")
                .should().dependOnClassesThat().resideInAnyPackage(SERVICE_PACKAGES.stream()
                        .filter(p -> !p.equals(basePackage)).map(p -> p + "..").toArray(String[]::new))
                .allowEmptyShould(true)
                .because("service chỉ nói chuyện với nhau qua contracts (REST/gRPC), không import mã của nhau");

        return List.of(layers, pureDomain, noOtherService);
    }
}
