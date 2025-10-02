package com.gherkin.navigator;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UsageSearchContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.cucumber.psi.GherkinStep;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance Gherkin step reference with advanced search strategies.
 * Uses multiple search strategies with smart caching for maximum accuracy without blocking.
 */
public class GherkinStepReference extends PsiReferenceBase<GherkinStep> {
    
    // Static cache shared across all instances for better performance
    private static final Map<String, CacheEntry> RESOLUTION_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 5000; // 5 seconds
    
    private final String stepText;
    private final String normalizedStepText;
    
    public GherkinStepReference(@NotNull GherkinStep element) {
        super(element, calculateTextRange(element), false);
        this.stepText = extractStepText(element);
        this.normalizedStepText = normalizeStepText(stepText);
    }
    
    /**
     * Cache entry for storing resolved references with timestamp
     */
    private static class CacheEntry {
        final PsiElement element;
        final long timestamp;
        
        CacheEntry(PsiElement element) {
            this.element = element;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isValid() {
            return element != null && element.isValid() && 
                   (System.currentTimeMillis() - timestamp) < CACHE_EXPIRY_MS;
        }
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
     * Resolve using multiple search strategies with smart caching.
     * This is what makes the hyperlink appear when hovering with Ctrl/Cmd.
     */
    @Nullable
    @Override
    public PsiElement resolve() {
        if (stepText.isEmpty()) {
            return null;
        }
        
        // Check cache first - key is project + normalized step text
        Project project = myElement.getProject();
        String cacheKey = project.getName() + ":" + normalizedStepText;
        
        CacheEntry cached = RESOLUTION_CACHE.get(cacheKey);
        if (cached != null && cached.isValid()) {
            return cached.element;
        }
        
        // Don't search during indexing - return null to avoid blocking
        if (DumbService.isDumb(project)) {
            return null;
        }
        
        // Use ReadAction for thread-safe PSI access
        PsiElement result = ReadAction.compute(() -> performMultiStrategySearch(project));
        
        // Cache the result (even if null)
        RESOLUTION_CACHE.put(cacheKey, new CacheEntry(result));
        
        // Cleanup old cache entries periodically (every 100 resolutions)
        if (RESOLUTION_CACHE.size() > 1000) {
            cleanupCache();
        }
        
        return result;
    }
    
    /**
     * Clean up expired cache entries
     */
    private void cleanupCache() {
        RESOLUTION_CACHE.entrySet().removeIf(entry -> !entry.getValue().isValid());
    }
    
    /**
     * Perform multi-strategy search for maximum accuracy.
     * Runs strategies sequentially (not parallel) to avoid thread overhead.
     */
    @Nullable
    private PsiElement performMultiStrategySearch(Project project) {
        Set<PsiElement> allMatches = new LinkedHashSet<>();
        
        // Strategy 1: Direct text search in strings (most precise)
        List<PsiElement> directMatches = searchByDirectText(project, normalizedStepText);
        allMatches.addAll(directMatches);
        
        // Strategy 2: Word-based search (catches variations)
        if (allMatches.isEmpty()) {
            List<PsiElement> wordMatches = searchBySignificantWord(project, normalizedStepText);
            allMatches.addAll(wordMatches);
        }
        
        // Strategy 3: Pattern-based search (comprehensive fallback)
        if (allMatches.isEmpty()) {
            List<PsiElement> patternMatches = searchByPattern(project, normalizedStepText);
            allMatches.addAll(patternMatches);
        }
        
        // Return best match from all strategies
        return selectBestMatch(new ArrayList<>(allMatches));
    }
    
    /**
     * Strategy 1: Direct text search using PSI index
     */
    private List<PsiElement> searchByDirectText(Project project, String stepText) {
        List<PsiElement> results = new ArrayList<>();
        PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        
        searchHelper.processElementsWithWord(
            (element, offsetInElement) -> {
                if (isStepDefinition(element)) {
                    String extracted = extractStepTextFromDefinition(element.getText());
                    if (extracted != null && compareStepTexts(stepText, extracted)) {
                        results.add(element);
                    }
                }
                return true; // Continue searching for all matches
            },
            scope,
            stepText,
            UsageSearchContext.IN_STRINGS,
            true,
            false
        );
        
        return results;
    }
    
    /**
     * Strategy 2: Word-based search for better recall
     */
    private List<PsiElement> searchBySignificantWord(Project project, String stepText) {
        List<PsiElement> results = new ArrayList<>();
        PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        
        // Extract most significant word
        String[] words = stepText.split("\\s+");
        String longestWord = "";
        for (String word : words) {
            String clean = word.replaceAll("[<>{}()\\[\\].,;:]", "");
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
                    String extracted = extractStepTextFromDefinition(text);
                    if (extracted != null && compareStepTexts(stepText, extracted)) {
                        results.add(element);
                    }
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
    
    /**
     * Strategy 3: Pattern-based search for annotations/decorators
     */
    private List<PsiElement> searchByPattern(Project project, String stepText) {
        List<PsiElement> results = new ArrayList<>();
        PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        
        // Search for decorator keywords
        String[] keywords = {"given", "when", "then", "Given", "When", "Then"};
        
        for (String keyword : keywords) {
            searchHelper.processElementsWithWord(
                (element, offsetInElement) -> {
                    String text = element.getText();
                    if (text != null && text.contains("@" + keyword) && isStepDefinition(element)) {
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
            
            // Stop if we found matches
            if (!results.isEmpty()) {
                break;
            }
        }
        
        return results;
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
