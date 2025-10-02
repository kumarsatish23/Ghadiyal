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
    
    // Static cache with size limit to prevent memory issues
    private static final Map<String, CacheEntry> RESOLUTION_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 10000; // 10 seconds (reduced frequency)
    private static final int MAX_CACHE_SIZE = 500; // Limit cache size
    private static final int CLEANUP_THRESHOLD = 400; // Cleanup at 400 entries
    
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
        
        // Cache the result (even if null) but limit cache size
        if (RESOLUTION_CACHE.size() >= MAX_CACHE_SIZE) {
            cleanupCache(); // Aggressive cleanup when near limit
        }
        RESOLUTION_CACHE.put(cacheKey, new CacheEntry(result));
        
        // Regular cleanup at threshold
        if (RESOLUTION_CACHE.size() >= CLEANUP_THRESHOLD) {
            cleanupCache();
        }
        
        return result;
    }
    
    /**
     * Clean up expired cache entries aggressively
     */
    private void cleanupCache() {
        // Remove expired entries
        RESOLUTION_CACHE.entrySet().removeIf(entry -> !entry.getValue().isValid());
        
        // If still too large, remove oldest entries
        if (RESOLUTION_CACHE.size() > CLEANUP_THRESHOLD) {
            List<String> keys = new ArrayList<>(RESOLUTION_CACHE.keySet());
            // Remove oldest 25% of entries
            int toRemove = keys.size() / 4;
            for (int i = 0; i < toRemove && i < keys.size(); i++) {
                RESOLUTION_CACHE.remove(keys.get(i));
            }
        }
    }
    
    /**
     * Perform multi-strategy search with early termination for speed.
     * Strategies run in order of precision, stopping early when we find good matches.
     */
    @Nullable
    private PsiElement performMultiStrategySearch(Project project) {
        PsiElement result = null;
        
        // Strategy 1: Direct text search (most precise, fastest)
        List<PsiElement> directMatches = searchByDirectText(project, normalizedStepText);
        if (!directMatches.isEmpty()) {
            result = selectBestMatch(directMatches);
            if (result != null) {
                return result; // Found it! Return immediately for speed
            }
        }
        
        // Strategy 2: Word-based search (good recall, moderate speed)
        List<PsiElement> wordMatches = searchBySignificantWord(project, normalizedStepText);
        if (!wordMatches.isEmpty()) {
            result = selectBestMatch(wordMatches);
            if (result != null) {
                return result; // Found it! Return immediately
            }
        }
        
        // Strategy 3: Pattern-based search (comprehensive fallback, slower)
        List<PsiElement> patternMatches = searchByPattern(project, normalizedStepText);
        if (!patternMatches.isEmpty()) {
            result = selectBestMatch(patternMatches);
        }
        
        return result;
    }
    
    /**
     * Strategy 1: Direct text search using PSI index
     * For complex steps, searches by first meaningful non-parameter word
     * OPTIMIZED: Limited scope and result count
     */
    private List<PsiElement> searchByDirectText(Project project, String stepText) {
        List<PsiElement> results = new ArrayList<>();
        PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
        
        // OPTIMIZATION: Only search in Python files (where step definitions are)
        GlobalSearchScope scope = GlobalSearchScope.getScopeRestrictedByFileTypes(
            GlobalSearchScope.projectScope(project),
            com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByExtension("py")
        );
        
        // For complex steps, extract first meaningful word for indexing
        String searchTerm = extractFirstMeaningfulWord(stepText);
        if (searchTerm.isEmpty()) {
            return results; // Don't search with empty term
        }
        
        final String finalSearchTerm = searchTerm;
        final int MAX_RESULTS = 10; // Limit results for performance
        
        searchHelper.processElementsWithWord(
            (element, offsetInElement) -> {
                if (results.size() >= MAX_RESULTS) {
                    return false; // Stop searching after limit
                }
                
                if (isStepDefinition(element)) {
                    String extracted = extractStepTextFromDefinition(element.getText());
                    if (extracted != null && compareStepTexts(stepText, extracted)) {
                        results.add(element);
                        return false; // Found match, stop immediately
                    }
                }
                return true;
            },
            scope,
            finalSearchTerm,
            UsageSearchContext.IN_STRINGS,
            true,
            false
        );
        
        return results;
    }
    
    /**
     * Extract first meaningful non-parameter word from step text.
     * E.g., "image pulled with <repo>:<tag>" -> "image"
     */
    private String extractFirstMeaningfulWord(String stepText) {
        String[] words = stepText.split("\\s+");
        for (String word : words) {
            // Skip parameter patterns
            if (isComplexParameterPattern(word) || isSimpleParameter(word)) {
                continue;
            }
            
            // Clean and check if meaningful
            String clean = word.replaceAll("[<>{}()\\[\\].,;:\"']", "");
            if (clean.length() >= 3 && !clean.matches("\\d+")) {
                return clean;
            }
        }
        return "";
    }
    
    /**
     * Strategy 2: Word-based search for better recall
     * OPTIMIZED: Limited scope, result count, and word attempts
     */
    private List<PsiElement> searchBySignificantWord(Project project, String stepText) {
        List<PsiElement> results = new ArrayList<>();
        PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
        
        // OPTIMIZATION: Only search in Python files
        GlobalSearchScope scope = GlobalSearchScope.getScopeRestrictedByFileTypes(
            GlobalSearchScope.projectScope(project),
            com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByExtension("py")
        );
        
        // Extract most significant words (limit to top 3 for performance)
        List<String> searchWords = extractSignificantWords(stepText);
        if (searchWords.isEmpty()) {
            return results;
        }
        
        final int MAX_WORDS_TO_TRY = 3; // Only try top 3 words
        final int MAX_RESULTS = 10;
        
        // Try each significant word until we find matches
        for (int i = 0; i < Math.min(searchWords.size(), MAX_WORDS_TO_TRY); i++) {
            String searchWord = searchWords.get(i);
            
            searchHelper.processElementsWithWord(
                (element, offsetInElement) -> {
                    if (results.size() >= MAX_RESULTS) {
                        return false; // Stop if we have enough results
                    }
                    
                    String text = element.getText();
                    if (text != null && text.contains(searchWord) && isStepDefinition(element)) {
                        String extracted = extractStepTextFromDefinition(text);
                        if (extracted != null && compareStepTexts(stepText, extracted)) {
                            results.add(element);
                            return false; // Found match, stop immediately
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
            
            // If we found matches with this word, no need to try others
            if (!results.isEmpty()) {
                break;
            }
        }
        
        return results;
    }
    
    /**
     * Extract significant words from step text, ordered by importance.
     * Skips parameters, common words, and prioritizes meaningful terms.
     */
    private List<String> extractSignificantWords(String stepText) {
        List<String> significantWords = new ArrayList<>();
        Set<String> commonWords = new HashSet<>(Arrays.asList(
            "the", "and", "with", "from", "onto", "into", "a", "an", "as", "of", "in", "to", "on"
        ));
        
        String[] words = stepText.split("\\s+");
        
        for (String word : words) {
            // Skip complex parameter patterns like {repo}:{tag}
            if (isComplexParameterPattern(word) || isSimpleParameter(word)) {
                continue;
            }
            
            // Clean the word
            String clean = word.replaceAll("[<>{}()\\[\\].,;:\"']", "").toLowerCase();
            
            // Skip if too short, pure numbers, or common words
            if (clean.length() < 3 || clean.matches("\\d+") || commonWords.contains(clean)) {
                continue;
            }
            
            significantWords.add(clean);
        }
        
        // Sort by length (longer = more specific = better)
        significantWords.sort((a, b) -> Integer.compare(b.length(), a.length()));
        
        return significantWords;
    }
    
    /**
     * Check if a word is a complex parameter pattern like {repo}:{tag} (has multiple parameters)
     * NOT simple parameters like {param} or JSON like {"pagesize":1000}
     */
    private boolean isComplexParameterPattern(String word) {
        // JSON patterns have quotes or look like {"key":value} or {"key":"value"}
        // These should be treated as SINGLE parameters, not complex patterns
        if (word.contains("\"") || word.contains(":") && (word.startsWith("{") && word.endsWith("}"))) {
            // Check if it's JSON format: {"key":...} or {"key":"..."}
            if (word.matches("\\{\"[^\"]+\":\\s*[^}]+\\}")) {
                return false; // This is JSON, treat as single parameter
            }
        }
        
        // Must have at least 2 parameter placeholders separated by punctuation
        // Matches: {repo}:{tag}, {repo}:{tag}:{sha}, <repo>:<tag>, etc.
        int paramCount = 0;
        int i = 0;
        while (i < word.length()) {
            if ((word.charAt(i) == '<' || word.charAt(i) == '{')) {
                paramCount++;
                // Skip to closing bracket
                char closing = word.charAt(i) == '<' ? '>' : '}';
                while (i < word.length() && word.charAt(i) != closing) {
                    i++;
                }
            }
            i++;
        }
        return paramCount >= 2;
    }
    
    /**
     * Strategy 3: Pattern-based search for annotations/decorators
     * OPTIMIZED: Limited scope, result count, and early termination
     */
    private List<PsiElement> searchByPattern(Project project, String stepText) {
        List<PsiElement> results = new ArrayList<>();
        PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
        
        // OPTIMIZATION: Only search in Python files
        GlobalSearchScope scope = GlobalSearchScope.getScopeRestrictedByFileTypes(
            GlobalSearchScope.projectScope(project),
            com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByExtension("py")
        );
        
        // Search for decorator keywords and "parsers" for pytest-bdd
        // Reduced list - only most common ones
        String[] keywords = {"given", "when", "then", "parsers"};
        final int MAX_RESULTS = 10;
        
        for (String keyword : keywords) {
            if (!results.isEmpty()) {
                break; // Found match, stop searching other keywords
            }
            
            searchHelper.processElementsWithWord(
                (element, offsetInElement) -> {
                    if (results.size() >= MAX_RESULTS) {
                        return false; // Stop if we have enough results
                    }
                    
                    String text = element.getText();
                    if (text != null && isStepDefinition(element)) {
                        String extracted = extractStepTextFromDefinition(text);
                        if (extracted != null && compareStepTexts(stepText, extracted)) {
                            results.add(element);
                            return false; // Found match, stop immediately
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
     * Compare two step texts for exact match.
     * Handles simple params like {param}, JSON like {"pagesize":1000}, complex patterns like {repo}:{tag},
     * parameter-to-literal matching, and simple-to-complex parameter matching.
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
            
            boolean swIsSimpleParam = isSimpleParameter(sw);
            boolean dwIsSimpleParam = isSimpleParameter(dw);
            boolean swIsComplexParam = isComplexParameterPattern(sw);
            boolean dwIsComplexParam = isComplexParameterPattern(dw);
            
            boolean swIsAnyParam = swIsSimpleParam || swIsComplexParam;
            boolean dwIsAnyParam = dwIsSimpleParam || dwIsComplexParam;
            
            // Case 1: Both are simple parameters - OK
            if (swIsSimpleParam && dwIsSimpleParam) {
                continue; // {param} matches {param}, or {"json":1} matches {param}
            }
            
            // Case 2: Both are complex parameters with same structure - OK
            if (swIsComplexParam && dwIsComplexParam) {
                if (normalizeParameterPattern(sw).equals(normalizeParameterPattern(dw))) {
                    continue; // {repo}:{tag} matches {repo}:{tag}
                }
            }
            
            // Case 3: Simple parameter matches complex parameter - OK
            // Example: {image_name} matches {image}:{tag}
            // The simple param can contain a value like "alpine:latest"
            if ((swIsSimpleParam && dwIsComplexParam) || (swIsComplexParam && dwIsSimpleParam)) {
                continue; // Simple param can match complex pattern
            }
            
            // Case 4: One is ANY parameter and the other is a literal - OK
            // Example: "do" (literal) matches {verb} (parameter)
            if (swIsAnyParam != dwIsAnyParam) {
                continue; // Parameter matches any literal word
            }
            
            // Case 5: Both are literals - must match exactly (case-insensitive)
            if (!sw.equalsIgnoreCase(dw)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if word is a simple parameter like {param} or <param>
     * Also treats JSON like {"pagesize":1000} and quoted params like "{param}" as simple parameters
     */
    private boolean isSimpleParameter(String word) {
        // JSON patterns: {"key":value} or {"key":"value"}
        if (word.matches("\\{\"[^\"]+\":\\s*[^}]+\\}")) {
            return true; // Treat JSON as simple parameter
        }
        
        // Quoted parameters: "{param}" or '<param>'
        if (word.matches("\"[<{][^>}:]+[>}]\"") || word.matches("'[<{][^>}:]+[>}]'")) {
            return true; // Treat quoted parameter as simple parameter
        }
        
        // Quoted literals that should match quoted parameters: "value" should match "{param}"
        if (word.matches("\"[^\"]+\"") || word.matches("'[^']+'")) {
            return true; // Treat any quoted value as matchable to parameter
        }
        
        // Regular simple parameters: {param}, <param>
        return word.matches("^[<{][^>}:]+[>}]$");
    }
    
    /**
     * Normalize parameter pattern to check structural equivalence.
     * E.g., {repo}:{tag} -> P:P, <sha> -> P
     */
    private String normalizeParameterPattern(String word) {
        // Replace all parameter placeholders with 'P' to check structure
        String normalized = word.replaceAll("[<{][^>}:]+[>}]", "P");
        return normalized;
    }
    
    /**
     * Extract step text from decorator/annotation and normalize it.
     * Handles single-line, multi-line, AND Python's implicit string concatenation.
     */
    @Nullable
    private String extractStepTextFromDefinition(String text) {
        String extracted = null;
        
        // pytest-bdd: parsers.parse("...") - handles multi-line AND concatenated strings
        // Example: parsers.parse("part1" "part2") or parsers.parse("text")
        Pattern pattern = Pattern.compile("parsers\\.parse\\s*\\(([^)]+)\\)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String content = matcher.group(1);
            // Extract ALL string literals and concatenate them (Python implicit concatenation)
            extracted = extractConcatenatedStrings(content);
        }
        
        // Simple decorators: @given("..."), @when("..."), @then("..."), etc.
        // Also handles multi-line and concatenated strings
        if (extracted == null) {
            pattern = Pattern.compile("@(?:given|when|then|step|and|but|Given|When|Then|And|But)\\s*\\(([^)]+)\\)", Pattern.DOTALL);
        matcher = pattern.matcher(text);
        if (matcher.find()) {
                String content = matcher.group(1);
                // Extract ALL string literals and concatenate them
                extracted = extractConcatenatedStrings(content);
            }
        }
        
        // Normalize the extracted text (convert <param> to {param} for consistency)
        if (extracted != null) {
            // Remove any extra whitespace/newlines from multi-line strings
            extracted = extracted.replaceAll("\\s+", " ").trim();
            extracted = normalizeStepText(extracted);
        }
        
        return extracted;
    }
    
    /**
     * Extract and concatenate all string literals from Python code.
     * Handles Python's implicit string concatenation: "part1" "part2" -> "part1part2"
     * Correctly handles nested quotes: 'text with "quotes" inside' and "text with 'quotes' inside"
     */
    private String extractConcatenatedStrings(String content) {
        StringBuilder result = new StringBuilder();
        
        // Match double-quoted strings (can contain single quotes)
        // Pattern: "anything except unescaped double quotes"
        Pattern doubleQuotePattern = Pattern.compile("\"([^\"]*)\"");
        Matcher doubleQuoteMatcher = doubleQuotePattern.matcher(content);
        while (doubleQuoteMatcher.find()) {
            result.append(doubleQuoteMatcher.group(1));
        }
        
        // If we found double-quoted strings, return them
        if (result.length() > 0) {
            return result.toString().trim();
        }
        
        // Otherwise, try single-quoted strings (can contain double quotes)
        // Pattern: 'anything except unescaped single quotes'
        Pattern singleQuotePattern = Pattern.compile("'([^']*)'");
        Matcher singleQuoteMatcher = singleQuotePattern.matcher(content);
        while (singleQuoteMatcher.find()) {
            result.append(singleQuoteMatcher.group(1));
        }
        
        String extracted = result.toString().trim();
        return extracted.isEmpty() ? null : extracted;
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
     * Check if element is a step definition.
     * Recognizes both simple decorators and parsers.parse() format.
     */
    private boolean isStepDefinition(PsiElement element) {
        String text = element.getText();
        if (text == null || text.length() > 1000) {
            return false;
        }
        
        // Match simple decorators: @given(...), @when(...), @then(...)
        if (text.matches("(?s)^\\s*@(given|when|then|step|and|but|Given|When|Then|And|But)\\s*\\(.*")) {
            return true;
        }
        
        // Match parsers.parse format: parsers.parse(...)
        if (text.contains("parsers.parse")) {
            return true;
        }
        
        return false;
    }
}

