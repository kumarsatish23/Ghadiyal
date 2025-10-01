package com.gherkin.navigator;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.cucumber.psi.GherkinStep;

/**
 * Reference contributor that enables Ctrl+Click navigation on Gherkin steps.
 * This is registered in plugin.xml and called by IntelliJ's reference resolution system.
 */
public class GherkinStepReferenceContributor extends PsiReferenceContributor {
    
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // Register our reference provider for all Gherkin step elements
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(GherkinStep.class),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                            @NotNull ProcessingContext context) {
                    if (element instanceof GherkinStep) {
                        GherkinStep step = (GherkinStep) element;
                        // Only create reference if step has valid text
                        String text = step.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            return new PsiReference[]{new GherkinStepReference(step)};
                        }
                    }
                    return PsiReference.EMPTY_ARRAY;
                }
            }
        );
    }
}

