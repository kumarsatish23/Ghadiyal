package com.gherkin.navigator;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.cucumber.psi.GherkinStep;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance Gherkin step reference with advanced search strategies.
 * Uses multi-threaded search and caching for maximum accuracy and speed.
 */
public class GherkinStepReference extends PsiReferenceBase<GherkinStep> {
    
    private static final ExecutorService SEARCH_EXECUTOR = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
    );
    
    private final String stepText;
    private final String normalizedStepText;
    private volatile PsiElement cachedResult;
    
    public GherkinStepReference(@NotNull GherkinStep element) {
        super(element, calculateTextRange(element), false);
        this.stepText = extractStepText(element);
        this.normalizedStepText = normalizeStepText(stepText);
    }
    
    /**
     * Calculate clickable text range - entire step text for proper hyperlink
     */
    private static TextRange calculateTextRange(GherkinStep element) {
        String fullText = element.getText();
        String keyword = element.getKeyword().getText();
        
        int start = keyword.length();
        while (start < fullText.length() && Character.isWhitespace(fullText.charAt(start))) {
            start++;
        }
        
        return new TextRange(start, fullText.length());
    }
    
    private String extractStepText(GherkinStep element) {
        String fullText = element.getText();
        if (fullText != null) {
            String keyword = element.getKeyword().getText();
            if (fullText.startsWith(keyword)) {
                fullText = fullText.substring(keyword.length()).trim();
            }
        }
        return fullText != null ? fullText.trim() : "";
    }
    
    /**
     * Normalize step text: <param> -> {param}
     */
    private String normalizeStepText(String text) {
        return text.replaceAll("<([^>]+)>", "{$1}");
    }
    
    /**
     * Resolve using multiple parallel search strategies for maximum accuracy.
     * This is what makes the hyperlink appear when hovering with Ctrl/Cmd.
     */
    @Nullable
    @Override
    public PsiElement resolve() {
        if (stepText.isEmpty()) {
            return null;
        }
        
        // Return cached result if available
        if (cachedResult != null && cachedResult.isValid()) {
            return cachedResult;
        }
        
        Project project = myElement.getProject();
        
        // Use ReadAction for thread-safe PSI access
        cachedResult = ReadAction.compute(() -> {
            // Use parallel search strategies for speed and accuracy
            List<SearchStrategy> strategies = Arrays.asList(
                new IndexedTextSearch(),
                new WordBasedSearch(),
                new PatternBasedSearch()
            );
            
            List<Future<List<PsiElement>>> futures = new ArrayList<>();
            
            // Execute all strategies in parallel
            for (SearchStrategy strategy : strategies) {
                Future<List<PsiElement>> future = SEARCH_EXECUTOR.submit(() -> 
                    strategy.search(project, normalizedStepText)
                );
                futures.add(future);
            }
            
            // Collect results from all strategies
            List<PsiElement> allMatches = new ArrayList<>();
            for (Future<List<PsiElement>> future : futures) {
                try {
                    List<PsiElement> matches = future.get(5, TimeUnit.SECONDS);
                    if (matches != null) {
                        allMatches.addAll(matches);
                    }
                } catch (Exception e) {
                    // Continue with other strategies
                }
            }
            
            // Return best match
            return selectBestMatch(allMatches);
        });
        
        return cachedResult;
    }
    
    /**
     * Get all variants for code completion (optional)
     */
    @NotNull
    @Override
    public Object[] getVariants() {
        return PsiElement.EMPTY_ARRAY;
    }
    
    /**
     * Select the most precise match from candidates
     */
    @Nullable
    private PsiElement selectBestMatch(List<PsiElement> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        
        // Filter to only exact matches
        List<PsiElement> exactMatches = new ArrayList<>();
        for (PsiElement candidate : candidates) {
            if (isExactMatch(candidate)) {
                exactMatches.add(candidate);
            }
        }
        
        if (exactMatches.isEmpty()) {
            return null;
        }
        
        if (exactMatches.size() == 1) {
            return exactMatches.get(0);
        }
        
        // Score and return best
        return exactMatches.stream()
            .max(Comparator.comparingInt(this::scoreMatch))
            .orElse(null);
    }
    
    /**
     * Check if candidate is an exact match
     */
    private boolean isExactMatch(PsiElement element) {
        String text = element.getText();
        if (text == null) return false;
        
        String extracted = extractStepTextFromDefinition(text);
        if (extracted == null) return false;
        
        return compareStepTexts(normalizedStepText, extracted);
    }
    
    /**
     * Compare two step texts for exact match
     */
    private boolean compareStepTexts(String stepText, String defText) {
        if (stepText.equals(defText)) {
            return true;
        }
        
        // Split into words and compare
        String[] stepWords = stepText.split("\\s+");
        String[] defWords = defText.split("\\s+");
        
        if (stepWords.length != defWords.length) {
            return false;
        }
        
        for (int i = 0; i < stepWords.length; i++) {
            String sw = stepWords[i];
            String dw = defWords[i];
            
            // Both are parameters - OK
            if (sw.matches("\\{[^}]+\\}") && dw.matches("\\{[^}]+\\}")) {
                continue;
            }
            
            // Must match exactly (case-insensitive)
            if (!sw.equalsIgnoreCase(dw)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Extract step text from decorator/annotation
     */
    @Nullable
    private String extractStepTextFromDefinition(String text) {
        // pytest-bdd: parsers.parse("...")
        Pattern pattern = Pattern.compile("parsers\\.parse\\([\"']([^\"']+)[\"']\\)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Simple: @given("...")
        pattern = Pattern.compile("@(?:given|when|then|step|and|but|Given|When|Then|And|But)\\s*\\([\"']([^\"']+)[\"']");
        matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }
    
    /**
     * Score a match for ranking
     */
    private int scoreMatch(PsiElement element) {
        String text = element.getText();
        if (text == null) return 0;
        
        int score = 1000;
        
        // Prefer shorter elements (more precise)
        score -= text.length() / 10;
        
        // Prefer elements that are decorators only
        if (text.startsWith("@")) {
            score += 500;
        }
        
        // Prefer fewer lines (just decorator, not whole function)
        long lines = text.chars().filter(ch -> ch == '\n').count();
        score -= (int)(lines * 50);
        
        return score;
    }
    
    // ==================== Search Strategies ====================
    
    /**
     * Base interface for search strategies
     */
    private interface SearchStrategy {
        List<PsiElement> search(Project project, String stepText);
    }
    
    /**
     * Strategy 1: Indexed text search using PSI search helper
     */
    private class IndexedTextSearch implements SearchStrategy {
        @Override
        public List<PsiElement> search(Project project, String stepText) {
            List<PsiElement> results = Collections.synchronizedList(new ArrayList<>());
            PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            
            // Search for the step text in string literals
            searchHelper.processElementsWithWord(
                (element, offsetInElement) -> {
                    if (isStepDefinition(element)) {
                        results.add(element);
                    }
                    return true;
                },
                scope,
                stepText,
                UsageSearchContext.IN_STRINGS,
                true,
                false
            );
            
            return results;
        }
    }
    
    /**
     * Strategy 2: Word-based search for better recall
     */
    private class WordBasedSearch implements SearchStrategy {
        @Override
        public List<PsiElement> search(Project project, String stepText) {
            List<PsiElement> results = Collections.synchronizedList(new ArrayList<>());
            PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            
            // Extract significant words
            String[] words = stepText.split("\\s+");
            String longestWord = "";
            for (String word : words) {
                String clean = word.replaceAll("[<>{}]", "");
                if (clean.length() > longestWord.length() && clean.length() > 3) {
                    longestWord = clean;
                }
            }
            
            if (longestWord.isEmpty()) {
                return results;
            }
            
            final String searchWord = longestWord;
            searchHelper.processElementsWithWord(
                (element, offsetInElement) -> {
                    String text = element.getText();
                    if (text != null && text.contains(searchWord) && isStepDefinition(element)) {
                        results.add(element);
                    }
                    return true;
                },
                scope,
                searchWord,
                UsageSearchContext.IN_STRINGS,
                true,
                false
            );
            
            return results;
        }
    }
    
    /**
     * Strategy 3: Pattern-based search for annotations/decorators
     */
    private class PatternBasedSearch implements SearchStrategy {
        @Override
        public List<PsiElement> search(Project project, String stepText) {
            List<PsiElement> results = Collections.synchronizedList(new ArrayList<>());
            PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            
            // Search for decorator keywords
            String[] keywords = {"given", "when", "then", "Given", "When", "Then"};
            
            for (String keyword : keywords) {
                searchHelper.processElementsWithWord(
                    (element, offsetInElement) -> {
                        String text = element.getText();
                        if (text != null && text.startsWith("@" + keyword) && isStepDefinition(element)) {
                            String extracted = extractStepTextFromDefinition(text);
                            if (extracted != null && compareStepTexts(stepText, extracted)) {
                                results.add(element);
                            }
                        }
                        return true;
                    },
                    scope,
                    keyword,
                    UsageSearchContext.IN_CODE,
                    true,
                    false
                );
            }
            
            return results;
        }
    }
    
    /**
     * Check if element is a step definition
     */
    private boolean isStepDefinition(PsiElement element) {
        String text = element.getText();
        if (text == null || text.length() > 500) {
            return false;
        }
        
        return text.matches("(?s)^\\s*@(given|when|then|step|and|but|Given|When|Then|And|But)\\s*\\(.*");
    }
}
