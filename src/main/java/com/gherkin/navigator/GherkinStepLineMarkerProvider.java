package com.gherkin.navigator;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.cucumber.psi.GherkinStep;

import javax.swing.*;

/**
 * Provides visual gutter icons next to Gherkin steps that have step definitions.
 * This gives users a visual indicator that navigation is available.
 */
public class GherkinStepLineMarkerProvider implements LineMarkerProvider {
    
    // Use a built-in icon for fast loading
    private static final Icon NAVIGATION_ICON = IconLoader.getIcon("/gutter/implementingMethod.svg", GherkinStepLineMarkerProvider.class);
    
    @Nullable
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // Only process GherkinStep elements
        if (!(element instanceof GherkinStep)) {
            return null;
        }
        
        GherkinStep step = (GherkinStep) element;
        
        // Check if step has valid text
        String text = step.getText();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        
        // Get the reference to step definition
        PsiReference[] references = element.getReferences();
        if (references.length == 0) {
            return null;
        }
        
        PsiElement target = null;
        for (PsiReference reference : references) {
            if (reference instanceof GherkinStepReference) {
                target = reference.resolve();
                break;
            }
        }
        
        // Only show marker if we found a step definition
        if (target == null) {
            return null;
        }
        
        // Create the line marker with navigation capability
        PsiElement finalTarget = target;
        return NavigationGutterIconBuilder
            .create(NAVIGATION_ICON)
            .setTarget(target)
            .setTooltipText("Navigate to step definition")
            .setAlignment(GutterIconRenderer.Alignment.LEFT)
            .createLineMarkerInfo(step);
    }
}

