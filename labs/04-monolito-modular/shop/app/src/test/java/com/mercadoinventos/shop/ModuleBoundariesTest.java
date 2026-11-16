package com.mercadoinventos.shop;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Los límites entre módulos, verificados en CADA build. Si alguien importa una clase
 * interna de otro módulo, el build falla con un mensaje que dice qué y dónde.
 */
@AnalyzeClasses(packages = "com.mercadoinventos.shop", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    private static ArchRule internalsArePrivate(String module) {
        return noClasses()
                .that().resideOutsideOfPackage("com.mercadoinventos.shop." + module + "..")
                .should().dependOnClassesThat().resideInAPackage("com.mercadoinventos.shop." + module + ".internal..")
                .as("Nadie fuera de '" + module + "' usa sus clases internas (solo " + module + ".api)")
                .allowEmptyShould(true);
    }

    @ArchTest
    static final ArchRule catalogInternalsArePrivate = internalsArePrivate("catalog");

    @ArchTest
    static final ArchRule inventoryInternalsArePrivate = internalsArePrivate("inventory");

    @ArchTest
    static final ArchRule paymentsInternalsArePrivate = internalsArePrivate("payments");

    @ArchTest
    static final ArchRule ordersInternalsArePrivate = internalsArePrivate("orders");

    @ArchTest
    static final ArchRule shippingInternalsArePrivate = internalsArePrivate("shipping");

    @ArchTest
    static final ArchRule noCyclesBetweenModules = slices()
            .matching("com.mercadoinventos.shop.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule publicApisDoNotDependOnInternals = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..internal..")
            .as("Las API públicas no exponen clases internas");
}
